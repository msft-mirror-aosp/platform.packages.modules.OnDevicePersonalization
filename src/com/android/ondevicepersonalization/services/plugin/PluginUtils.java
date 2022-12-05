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
import android.os.Bundle;
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

    public static final String PARAM_CLASS_NAME_KEY = "param.classname";
    public static final String PARAM_OPERATION_KEY = "param.operation";

    public static final String OUTPUT_RESULT_KEY = "result";

    public static final int OP_DOWNLOAD_FILTER_HANDLER = 1;
    public static final int OP_MAX = 2;  // 1 more than the last defined operation.

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
            @NonNull PluginController pluginController, @NonNull Bundle pluginParams) {
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

    /**
     * Create a plugin id encoding the vendors information.
     *
     * @param vendorPackageName Name of the vendor package
     * @param taskName          Name of the task to be run
     * @return PluginId to be used by the plugin
     */
    public static String createPluginId(String vendorPackageName, String taskName) {
        // TODO(b/249345663) Perform any validation needed on the input.
        return vendorPackageName + "-" + taskName;
    }

    /**
     * Gets the Vendor package name from the given pluginId
     *
     * @param pluginId pluginId containing vendorPackageName
     * @return VendorPackageName
     */
    public static String getVendorPackageNameFromPluginId(String pluginId) {
        // TODO(b/249345663) Perform any validation needed on the input.
        return pluginId.split("-")[0];
    }

    private PluginUtils() {}
}
