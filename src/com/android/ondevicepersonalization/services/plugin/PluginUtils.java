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

package com.android.ondevicepersonalization.services.plugin;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.ondevicepersonalization.Constants;
import android.ondevicepersonalization.OnDevicePersonalizationException;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginController;
import com.android.ondevicepersonalization.libraries.plugin.PluginInfo;
import com.android.ondevicepersonalization.libraries.plugin.PluginManager;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;

/** Utilities to support loading and executing plugins. */
public class PluginUtils {
    private static final String TAG = "PluginUtils";
    private static final String ENTRY_POINT_CLASS =
            "com.android.ondevicepersonalization.services.plugin.OnDevicePersonalizationPlugin";

    /** Creates a {@link PluginController} with a list of packages to load. */
    @NonNull public static PluginController createPluginController(
            String taskName, @NonNull PluginManager pluginManager, @Nullable String[] apkList)
            throws Exception {
        PluginInfo info = PluginInfo.createJvmInfo(
                taskName, getArchiveList(apkList), ENTRY_POINT_CLASS);
        return Objects.requireNonNull(pluginManager.createPluginController(info));
    }

    /** Loads the packages defined in the {@link PluginController}. */
    @NonNull public static ListenableFuture<Void> loadPlugin(
            @NonNull PluginController pluginController) {
        return CallbackToFutureAdapter.getFuture(
            completer -> {
                try {
                    Log.d(TAG, "loadPlugin");
                    pluginController.load(new PluginCallback() {
                        @Override public void onSuccess(PersistableBundle bundle) {
                            completer.set(null);
                        }
                        @Override public void onFailure(FailureType failure) {
                            completer.setException(new OnDevicePersonalizationException(
                                    Constants.STATUS_INTERNAL_ERROR,
                                    String.format("loadPlugin failed. %s", failure.toString())));
                        }
                    });
                } catch (Exception e) {
                    completer.setException(e);
                }
                return "loadPlugin";
            }
        );
    }

    /** Executes the plugin entry point. */
    @NonNull public static ListenableFuture<PersistableBundle> executePlugin(
            @NonNull PluginController pluginController, @NonNull PersistableBundle pluginParams) {
        return CallbackToFutureAdapter.getFuture(
            completer -> {
                try {
                    Log.d(TAG, "executePlugin");
                    pluginController.execute(pluginParams, new PluginCallback() {
                        @Override public void onSuccess(PersistableBundle bundle) {
                            completer.set(bundle);
                        }
                        @Override public void onFailure(FailureType failure) {
                            completer.setException(new OnDevicePersonalizationException(
                                    Constants.STATUS_INTERNAL_ERROR,
                                    String.format("executePlugin failed: %s", failure.toString())));
                        }
                    });
                } catch (Exception e) {
                    completer.setException(e);
                }
                return "executePlugin";
            }
        );
    }

    @NonNull static ImmutableList<PluginInfo.ArchiveInfo> getArchiveList(
            @Nullable String[] apkList) {
        if (apkList == null) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<PluginInfo.ArchiveInfo> archiveInfoBuilder = ImmutableList.builder();
        for (int i = 0; i < apkList.length; ++i) {
            if (apkList[i] != null && !apkList[i].isEmpty()) {
                archiveInfoBuilder.add(
                        PluginInfo.ArchiveInfo.builder().setPackageName(apkList[i]).build());
            }
        }
        return archiveInfoBuilder.build();
    }

    private PluginUtils() {}
}
