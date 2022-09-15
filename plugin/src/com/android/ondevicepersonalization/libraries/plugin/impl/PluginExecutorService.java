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

package com.android.ondevicepersonalization.libraries.plugin.impl;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginContext;
import com.android.ondevicepersonalization.libraries.plugin.PluginContextProvider;
import com.android.ondevicepersonalization.libraries.plugin.PluginState;
import com.android.ondevicepersonalization.libraries.plugin.internal.CallbackConverter;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginExecutorService;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginStateCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginExecutor;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginInfoInternal;

import org.checkerframework.checker.nullness.qual.Nullable;

/** Service that loads, and executes {@link Plugin} implementations. */
public class PluginExecutorService extends Service {
    public static final String TAG = "PluginExecutorService";

    private PluginExecutor mExecutorManager;

    public PluginExecutorService() {}

    @Nullable PluginContextProvider mPluginContextProvider;
    @Nullable PluginContext mPluginContext;

    @Override
    public void onCreate() {
        super.onCreate();
        Context applicationContext = getApplicationContext();
        if (applicationContext == null) {
            return;
        }
        mExecutorManager = PluginExecutor.create(applicationContext);
        if (!(applicationContext instanceof PluginContextProvider)) {
            return;
        }
        mPluginContextProvider = (PluginContextProvider) applicationContext;
    }

    @Override
    public @Nullable IBinder onBind(Intent intent) {

        return new IPluginExecutorService.Stub() {
            @Override
            public void load(PluginInfoInternal info, IPluginCallback pluginCallback) {
                PluginCallback publicPluginCallback =
                        CallbackConverter.toPublicCallback(pluginCallback);
                try {
                    mExecutorManager.load(info, publicPluginCallback, mPluginContextProvider);
                } catch (RemoteException e) {
                    try {
                        pluginCallback.onFailure(FailureType.ERROR_LOADING_PLUGIN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback error: " + e2.toString());
                    }
                }
            }

            @Override
            public void execute(
                    String pluginName, PersistableBundle input, IPluginCallback pluginCallback) {
                // TODO(b/231347987): we need extra logic somewhere that can validated the contents
                // of the
                // output Bundle.
                PluginCallback publicPluginCallback =
                        CallbackConverter.toPublicCallback(pluginCallback);
                try {
                    mExecutorManager.execute(
                            input, pluginName, publicPluginCallback, mPluginContext);
                } catch (RemoteException e) {
                    try {
                        pluginCallback.onFailure(FailureType.ERROR_EXECUTING_PLUGIN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback error: " + e2.toString());
                    }
                }
            }

            @Override
            public void unload(String pluginName, IPluginCallback pluginCallback) {
                PluginCallback publicPluginCallback =
                        CallbackConverter.toPublicCallback(pluginCallback);
                try {
                    mExecutorManager.unload(pluginName, publicPluginCallback, mPluginContext);
                } catch (RemoteException e) {
                    try {
                        pluginCallback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback error: " + e2.toString());
                    }
                }
            }

            @Override
            public void checkPluginState(String pluginName, IPluginStateCallback stateCallback) {
                try {
                    mExecutorManager.checkPluginState(pluginName, stateCallback);
                } catch (RemoteException e) {
                    try {
                        stateCallback.onState(PluginState.STATE_EXCEPTION_THROWN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback error: " + e2.toString());
                    }
                }
            }
        };
    }
}
