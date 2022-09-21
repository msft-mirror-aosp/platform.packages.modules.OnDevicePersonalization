/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ondevicepersonalization.libraries.plugin.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.FileUtils;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Pair;

import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginInfo.ArchiveInfo;
import com.android.ondevicepersonalization.libraries.plugin.internal.util.ApkReader;

import dalvik.system.InMemoryDexClassLoader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Util class used to help with loading plugins. */
public final class PluginLoader {
    public static final String TAG = "PluginLoader";
    private static final String CHECKSUM_SUFFIX = ".md5";
    private static final long BIND_TIMEOUT_MS = 2_000;

    /** Interface to wrap the service call the PluginManager wants to make. */
    @FunctionalInterface
    @SuppressWarnings("AndroidApiChecker")
    public interface PluginTask {
        /** Executes the task specified in info. */
        void run(PluginInfoInternal info) throws RemoteException;
    }

    /**
     * Returns an instance of the plugin.
     *
     * @param className The class name of the plugin
     * @param pluginCode contains FileDescriptors with the plugin apks to be loaded
     * @param classLoader The plugin container's class loader to be isolated and managed
     * @param containerClassesAllowlist Classes allowed to be loaded outside plugin's class loader.
     *     These are usually classes that are accessed inside and outside the sandbox.
     */
    public static @Nullable Plugin loadPlugin(
            String className,
            ImmutableList<PluginCode> pluginCode,
            ClassLoader classLoader,
            ImmutableSet<String> containerClassesAllowlist) {
        try (CloseableList<FileInputStream> archiveList =
                PluginCode.createFileInputStreamListFromNonNativeFds(pluginCode)) {
            ByteBuffer[] dexes = ApkReader.loadPluginCode(archiveList.closeables());

            // Instantiating a ClassLoader and loading classes on Android would trigger dex-to-vdex
            // generation to speed up class verification process at subsequent calls by verifying
            // dex
            // upfront and storing verified dex (vdex) to app's storage.
            // On an isolated process, dex-to-vdex process would always fail due to denials of
            // file/dir creation. It potentially hurts class loading performance but not impacts
            // functionalities, worth further systracing/profiling to observe VerifyClass overhead.
            IsolationClassLoader isolatedContainerClassLoader =
                    new IsolationClassLoader(classLoader, containerClassesAllowlist);
            InMemoryDexClassLoader pluginClassLoader =
                    new InMemoryDexClassLoader(dexes, isolatedContainerClassLoader);

            Class<?> clazz = pluginClassLoader.loadClass(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (!(instance instanceof Plugin)) {
                return null;
            }
            return (Plugin) instance;
        } catch (IOException e) {
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Prepares and runs a plugin task. */
    public static boolean prepareThenRun(
            Context applicationContext,
            SettableFuture<Boolean> serviceReadiness,
            String serviceName,
            PluginInfoInternal.Builder infoBuilder,
            List<ArchiveInfo> pluginArchives,
            PluginTask pluginTask) {
        AssetsCacheManager assetsCacheManager = new AssetsCacheManager(applicationContext);
        // Copy the plugin in app's assets to a file in app's cache directory then await
        // readiness of the service before moving forward.
        // This minimizes the amount of data/code within which the pluginClassName is looked up at
        // sandbox container.
        // Avoid using Context.getAssets().openFd() as it wraps a file descriptor mapped to
        // the-entire-app-apk instead of the-plugin-archive-in-the-app-apk.
        for (ArchiveInfo pluginArchive : pluginArchives) {
            if (pluginArchive.packageName() != null) {
                if (!assetsCacheManager.copyPluginFromPackageAssetsToCacheDir(pluginArchive)) {
                    return false;
                }
            } else {
                if (!assetsCacheManager.copyPluginFromAssetsToCacheDir(pluginArchive.filename())) {
                    return false;
                }
            }
        }

        // Consider further optimizations and restrictions e.g.,
        //  - Cache file descriptors (be careful of the shared file offset among all fd.dup())
        //  - Restrict cpu affinity and usage i.e. background execution

        ImmutableList<Pair<File, String>> archivesInCacheDir =
                ImmutableList.copyOf(
                        Lists.transform(
                                pluginArchives,
                                (ArchiveInfo archive) ->
                                        new Pair<File, String>(
                                                new File(
                                                        applicationContext.getCacheDir(),
                                                        archive.filename()),
                                                assetsCacheManager.getChecksumFromAssets(
                                                        archive))));

        try (CloseableList<PluginCode> files =
                createCloseablePluginCodeListFromFiles(archivesInCacheDir)) {

            infoBuilder.setPluginCodeList(ImmutableList.copyOf(files.closeables()));

            PluginInfoInternal info = infoBuilder.build();

            if (!maybeAwaitPluginServiceReady(serviceName, serviceReadiness)) {
                return false;
            }

            pluginTask.run(info);
            return true;
        } catch (RemoteException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static CloseableList<PluginCode> createCloseablePluginCodeListFromFiles(
            Collection<Pair<File, String>> fileChecksumPairs) throws IOException {
        List<PluginCode> fileList = new ArrayList<>();
        for (Pair<File, String> fileChecksumPair : fileChecksumPairs) {
            File file = fileChecksumPair.first;
            String checksum = fileChecksumPair.second;
            fileList.add(
                    PluginCode.builder()
                            .setNativeFd(
                                    ParcelFileDescriptor.open(
                                            file, ParcelFileDescriptor.MODE_READ_ONLY))
                            .setNonNativeFd(
                                    ParcelFileDescriptor.open(
                                            file, ParcelFileDescriptor.MODE_READ_ONLY))
                            .setChecksum(checksum)
                            .build());
        }

        return new CloseableList<>(fileList);
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private static boolean maybeAwaitPluginServiceReady(
            String serviceName, SettableFuture<Boolean> readiness) {
        try {
            // Don't block-wait at app's main thread for service readiness as the readiness
            // signal is asserted at onServiceConnected which also run at apps' main thread
            // ends up deadlock or starvation since the signal will not be handled until the
            // maybeAwaitPluginServiceReady finished.
            if (isMainThread()) {
                if (!readiness.isDone() || !readiness.get()) {
                    return false;
                }
            } else {
                readiness.get(BIND_TIMEOUT_MS, MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        }
        return true;
    }

    private static class AssetsCacheManager {
        final Context mApplicationContext;

        AssetsCacheManager(Context applicationContext) {
            this.mApplicationContext = applicationContext;
        }

        private String getChecksumFromAssets(ArchiveInfo pluginArchive) {
            AssetManager assetManager;
            if (pluginArchive.packageName() != null) {
                try {
                    assetManager = packageAssetManager(pluginArchive.packageName());
                } catch (NameNotFoundException e) {
                    return "";
                }
            } else {
                assetManager = mApplicationContext.getAssets();
            }

            String checksumFile =
                    Files.getNameWithoutExtension(pluginArchive.filename()) + CHECKSUM_SUFFIX;
            try (InputStream checksumInAssets = assetManager.open(checksumFile);
                    InputStreamReader checksumInAssetsReader =
                            new InputStreamReader(checksumInAssets)) {
                return CharStreams.toString(checksumInAssetsReader);
            } catch (IOException e) {
                // The magic empty string would tell sandbox to fall back to non-caching mode where
                // preparation and setup for a plugin is executed from scratch.
                return "";
            }
        }

        private AssetManager packageAssetManager(String pluginPackage)
                throws NameNotFoundException {
            Context pluginContext = mApplicationContext.createPackageContext(pluginPackage, 0);
            return pluginContext.getAssets();
        }

        private boolean copyPluginFromPackageAssetsToCacheDir(ArchiveInfo pluginArchive) {
            try {
                AssetManager assetManager = packageAssetManager(pluginArchive.packageName());
                return copyPluginToCacheDir(pluginArchive.filename(), assetManager);
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        private boolean copyPluginFromAssetsToCacheDir(String pluginArchive) {
            // Checksum filename should be in the format of <plugin_filename>.<CHECKSUM_SUFFIX>.
            // E.g. plugin filename is foo.apk/foo.zip then checksum filename should be foo.md5
            return copyPluginToCacheDir(pluginArchive, mApplicationContext.getAssets());
        }

        private boolean copyPluginToCacheDir(String pluginArchive, AssetManager assetManager) {
            // If pluginArchive has no file extension, append CHECKSUM_SUFFIX directly.
            String checksumFile = Files.getNameWithoutExtension(pluginArchive) + CHECKSUM_SUFFIX;
            if (canReusePluginInCacheDir(pluginArchive, checksumFile, assetManager)) {
                return true;
            }

            File pluginInCacheDir = new File(mApplicationContext.getCacheDir(), pluginArchive);
            File checksumInCacheDir = new File(mApplicationContext.getCacheDir(), checksumFile);
            try (InputStream pluginSrc = assetManager.open(pluginArchive);
                    OutputStream pluginDst = new FileOutputStream(pluginInCacheDir);
                    InputStream checksumSrc = assetManager.open(checksumFile);
                    OutputStream checksumDst = new FileOutputStream(checksumInCacheDir)) {
                // Data := content (plugin) + metadata (checksum)
                // Enforce the Data writing order: (content -> metadata) like what common file
                // systems do to ensure better fault tolerance and data integrity.
                FileUtils.copy(pluginSrc, pluginDst);
                FileUtils.copy(checksumSrc, checksumDst);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private boolean canReusePluginInCacheDir(
                String pluginArchive, String checksumFile, AssetManager assetManager) {
            // Can reuse the plugin at app's cache directory when both are met:
            //  - The plugin already existed at app's cache directory
            //  - Checksum of plugin_in_assets == Checksum of plugin_at_cache_dir
            File pluginInCacheDir = new File(mApplicationContext.getCacheDir(), pluginArchive);
            if (!pluginInCacheDir.exists()) {
                return false;
            }
            try (InputStream checksumInAssets = assetManager.open(checksumFile);
                    InputStreamReader checksumInAssetsReader =
                            new InputStreamReader(checksumInAssets);
                    InputStream checksumInCacheDir =
                            new FileInputStream(
                                    new File(mApplicationContext.getCacheDir(), checksumFile));
                    InputStreamReader checksumInCacheDirReader =
                            new InputStreamReader(checksumInCacheDir)) {
                return CharStreams.toString(checksumInAssetsReader)
                        .equals(CharStreams.toString(checksumInCacheDirReader));
            } catch (IOException e) {
                return false;
            }
        }
    }

    private PluginLoader() {}
}
