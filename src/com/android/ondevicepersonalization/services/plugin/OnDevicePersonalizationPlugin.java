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
import android.ondevicepersonalization.DownloadHandler;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginContext;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.Futures;

/** Plugin that runs in an isolated process. */
public class OnDevicePersonalizationPlugin implements Plugin {
    private static final String TAG = "OnDevicePersonalizationPlugin";
    private PersistableBundle mInput;
    private PluginCallback mPluginCallback;
    private PluginContext mPluginContext;

    @Override public void onExecute(
            @NonNull PersistableBundle input,
            @NonNull PluginCallback callback,
            @Nullable PluginContext context) {
        Log.i(TAG, "Executing plugin.");
        mInput = input;
        mPluginCallback = callback;
        mPluginContext = context;

        try {
            String className = input.getString(PluginUtils.PARAM_CLASS_NAME_KEY);
            if (className == null || className.isEmpty()) {
                Log.e(TAG, "className missing.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            int operation = input.getInt(PluginUtils.PARAM_OPERATION_KEY);
            if (operation == 0 || operation >= PluginUtils.OP_MAX) {
                Log.e(TAG, "operation missing or invalid.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            Class<?> clazz = Class.forName(className);
            Object o = clazz.getDeclaredConstructor().newInstance();

            if (operation == PluginUtils.OP_DOWNLOAD_FILTER_HANDLER) {
                DownloadHandler downloadHandler = (DownloadHandler) o;
                var unused = Futures.submit(
                        () -> runDownloadHandlerFilter(downloadHandler),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());
            }
        } catch (Exception e) {
            Log.e(TAG, "Plugin failed. " + e.toString());
            sendErrorResult(FailureType.ERROR_UNKNOWN);
        }
    }

    private void runDownloadHandlerFilter(DownloadHandler downloadHandler) {
        Log.d(TAG, "runDownloadHandlerFilter() started.");
        // TODO(b/239479120, b/258808270): Build the parameters to downloadHandler.filterData,
        //  call downloadHandler.filterData, and build output from vendor code to managing process
        PersistableBundle result = new PersistableBundle();
        result.putStringArray(PluginUtils.OUTPUT_RESULT_KEY, new String[0]);
        try {
            mPluginCallback.onSuccess(result);
        } catch (Exception e) {
            Log.e(TAG, "Error calling pluginCallback", e);
        }
    }

    private void sendErrorResult(FailureType failure) {
        try {
            mPluginCallback.onFailure(failure);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error.");
        }
    }
}
