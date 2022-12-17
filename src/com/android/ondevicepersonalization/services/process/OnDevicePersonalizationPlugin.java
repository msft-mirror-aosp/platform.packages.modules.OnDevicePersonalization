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

package com.android.ondevicepersonalization.services.process;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.ondevicepersonalization.OnDevicePersonalizationContextImpl;
import android.ondevicepersonalization.PersonalizationService;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginContext;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.Futures;

import java.util.List;

/** Plugin that runs in an isolated process. */
public class OnDevicePersonalizationPlugin implements Plugin {
    private static final String TAG = "OnDevicePersonalizationPlugin";
    private Bundle mInput;
    private PluginCallback mPluginCallback;
    private PluginContext mPluginContext;

    @Override
    public void onExecute(
            @NonNull Bundle input,
            @NonNull PluginCallback callback,
            @Nullable PluginContext pluginContext) {
        Log.i(TAG, "Executing plugin.");
        mInput = input;
        mPluginCallback = callback;
        mPluginContext = pluginContext;

        try {
            String className = input.getString(ProcessUtils.PARAM_CLASS_NAME_KEY);
            if (className == null || className.isEmpty()) {
                Log.e(TAG, "className missing.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            int operation = input.getInt(ProcessUtils.PARAM_OPERATION_KEY);
            if (operation == 0 || operation >= ProcessUtils.OP_MAX) {
                Log.e(TAG, "operation missing or invalid.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            IBinder binder = input.getBinder(ProcessUtils.PARAM_DATA_ACCESS_BINDER);
            if (binder == null) {
                Log.e(TAG, "Binder missing.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }
            IDataAccessService dataAccessService =
                    IDataAccessService.Stub.asInterface(binder);
            if (dataAccessService == null) {
                Log.e(TAG, "Invalid dataAccessService");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            Class<?> clazz = Class.forName(className);
            PersonalizationService personalizationService =
                    (PersonalizationService) clazz.getDeclaredConstructor().newInstance();
            // TODO(b/249345663): Set the 'Context' for the service.

            if (operation == ProcessUtils.OP_DOWNLOAD_FILTER_HANDLER) {
                var unused = Futures.submit(
                        () -> runDownloadHandlerFilter(personalizationService,
                                dataAccessService,
                                input.getParcelable(ProcessUtils.INPUT_PARCEL_FD,
                                        ParcelFileDescriptor.class)),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());
            }
        } catch (Exception e) {
            Log.e(TAG, "Plugin failed. " + e);
            sendErrorResult(FailureType.ERROR_UNKNOWN);
        }
    }

    private void runDownloadHandlerFilter(PersonalizationService service,
            IDataAccessService dataAccessService,
            ParcelFileDescriptor fd) {
        Log.d(TAG, "runDownloadHandlerFilter() started.");
        OnDevicePersonalizationContext odpContext =
                new OnDevicePersonalizationContextImpl(dataAccessService);
        service.onDownload(fd, odpContext,
                new PersonalizationService.DownloadCallback() {
                    @Override
                    public void onSuccess(@NonNull List<String> result) {
                        PersistableBundle finalOutput = new PersistableBundle();
                        finalOutput.putStringArray(ProcessUtils.OUTPUT_RESULT_KEY,
                                result.toArray(new String[0]));
                        try {
                            mPluginCallback.onSuccess(finalOutput);
                        } catch (Exception e) {
                            Log.e(TAG, "Error calling pluginCallback", e);
                        }
                    }

                    @Override
                    public void onError() {
                        sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                    }
                });
    }

    private void sendErrorResult(FailureType failure) {
        try {
            mPluginCallback.onFailure(failure);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error.");
        }
    }
}
