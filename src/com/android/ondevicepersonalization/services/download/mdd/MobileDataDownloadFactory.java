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

package com.android.ondevicepersonalization.services.download.mdd;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.android.downloader.AndroidDownloaderLogger;
import com.google.android.downloader.ConnectivityHandler;
import com.google.android.downloader.DownloadConstraints;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.PlatformUrlEngine;
import com.google.android.downloader.UrlEngine;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.ExceptionHandler;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.Offroad2FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.backends.AndroidFileBackend;
import com.google.android.libraries.mobiledatadownload.file.backends.JavaFileBackend;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.DownloadMetadataStore;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.SharedPreferencesDownloadMetadata;
import com.google.android.libraries.mobiledatadownload.monitor.NetworkUsageMonitor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Executor;

/** Mobile Data Download Factory. */
public class MobileDataDownloadFactory {
    /** Downloader Connection Timeout in Milliseconds. */
    private static final int DOWNLOADER_CONNECTION_TIMEOUT_MS = 10 * 1000; // 10 seconds
    /** Downloader Read Timeout in Milliseconds. */
    private static final int DOWNLOADER_READ_TIMEOUT_MS = 10 * 1000; // 10 seconds.
    /** Downloader max download threads. */
    private static final int DOWNLOADER_MAX_DOWNLOAD_THREADS = 2;
    private static final String MDD_METADATA_SHARED_PREFERENCES = "mdd_metadata_store";
    private static MobileDataDownload sSingleton;
    private static SynchronousFileStorage sSynchronousFileStorage;

    /** Returns a singleton of MobileDataDownload. */
    @NonNull
    public static synchronized MobileDataDownload getMdd(
            @NonNull Context context) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSingleton == null) {
                SynchronousFileStorage fileStorage = getFileStorage(context);

                // TODO(b/241009783): This only adds the core MDD code. We still need other
                //  components:
                // 1) Add Logger
                // 2) Set Flags
                // 3) Add Configurator.
                sSingleton =
                        MobileDataDownloadBuilder.newBuilder()
                                .setContext(context)
                                .setControlExecutor(getControlExecutor())
                                .setTaskScheduler(Optional.of(new MddTaskScheduler(context)))
                                .setNetworkUsageMonitor(getNetworkUsageMonitor(context))
                                .setFileStorage(fileStorage)
                                .setFileDownloaderSupplier(() -> getFileDownloader(context))
                                .addFileGroupPopulator(
                                        new OnDevicePersonalizationFileGroupPopulator(context))
                                .build();
            }

            return sSingleton;
        }
    }

    @NonNull
    private static NetworkUsageMonitor getNetworkUsageMonitor(@NonNull Context context) {
        return new NetworkUsageMonitor(context, System::currentTimeMillis);
    }

    @NonNull
    public static SynchronousFileStorage getFileStorage(@NonNull Context context) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSynchronousFileStorage == null) {
                sSynchronousFileStorage =
                        new SynchronousFileStorage(
                                ImmutableList.of(
                                        /*backends*/ AndroidFileBackend.builder(context).build(),
                                        new JavaFileBackend()),
                                ImmutableList.of(/*transforms*/),
                                ImmutableList.of(/*monitors*/));
            }
            return sSynchronousFileStorage;
        }
    }

    @NonNull
    private static ListeningExecutorService getControlExecutor() {
        return OnDevicePersonalizationExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static Executor getDownloadExecutor() {
        return OnDevicePersonalizationExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static UrlEngine getUrlEngine() {
        // TODO(b/219594618): Switch to use CronetUrlEngine.
        return new PlatformUrlEngine(
                OnDevicePersonalizationExecutors.getBlockingExecutor(),
                DOWNLOADER_CONNECTION_TIMEOUT_MS,
                DOWNLOADER_READ_TIMEOUT_MS);
    }

    @NonNull
    private static ExceptionHandler getExceptionHandler() {
        return ExceptionHandler.withDefaultHandling();
    }

    @NonNull
    private static FileDownloader getFileDownloader(
            @NonNull Context context) {
        DownloadMetadataStore downloadMetadataStore = getDownloadMetadataStore(context);

        Downloader downloader =
                new Downloader.Builder()
                        .withIOExecutor(OnDevicePersonalizationExecutors.getBlockingExecutor())
                        .withConnectivityHandler(new NoOpConnectivityHandler())
                        .withMaxConcurrentDownloads(DOWNLOADER_MAX_DOWNLOAD_THREADS)
                        .withLogger(new AndroidDownloaderLogger())
                        .addUrlEngine("https", getUrlEngine())
                        .build();

        return new Offroad2FileDownloader(
                downloader,
                getFileStorage(context),
                getDownloadExecutor(),
                /* authTokenProvider */ null,
                downloadMetadataStore,
                getExceptionHandler(),
                Optional.absent());
    }

    @NonNull
    private static DownloadMetadataStore getDownloadMetadataStore(@NonNull Context context) {
        SharedPreferences sharedPrefs =
                context.getSharedPreferences(MDD_METADATA_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        DownloadMetadataStore downloadMetadataStore =
                new SharedPreferencesDownloadMetadata(
                        sharedPrefs, OnDevicePersonalizationExecutors.getBackgroundExecutor());
        return downloadMetadataStore;
    }

    // Connectivity constraints will be checked by JobScheduler/WorkManager instead.
    private static class NoOpConnectivityHandler implements ConnectivityHandler {
        @Override
        public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
            return Futures.immediateVoidFuture();
        }
    }
}
