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
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginApplication;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginHost;
import com.android.ondevicepersonalization.libraries.plugin.PluginState;
import com.android.ondevicepersonalization.libraries.plugin.internal.CallbackConverter;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginExecutorService;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginStateCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginExecutor;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginInfoInternal;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginLoaderImpl;

import org.checkerframework.checker.nullness.qual.Nullable;

/** Service that loads, and executes {@link Plugin} implementations. */
public class PluginExecutorService extends Service {
    public static final String TAG = "PluginExecutorService";
    private PluginExecutor mPluginExecutor;

    public PluginExecutorService() {}

    @Nullable PluginApplication mPluginApplication = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Context applicationContext = getApplicationContext();
        if (applicationContext == null) {
            return;
        }
        mPluginExecutor = PluginExecutor.create(applicationContext, new PluginLoaderImpl());

        // Expect the Application context to implement the {@link PluginApplication} interface. The
        // {@link PluginApplication}'s purpose is to supply an optional {@link PluginHost}
        // implementation.
        if (!(applicationContext instanceof PluginApplication)) {
            return;
        }
        mPluginApplication = (PluginApplication) applicationContext;
    }

    @Override
    public @Nullable IBinder onBind(Intent intent) {

        return new IPluginExecutorService.Stub() {
            @Override
            public void load(
                    PluginInfoInternal info,
                    IPluginCallback pluginCallback,
                    @Nullable Bundle pluginContextInitData) {

                // The {@link PluginHost} provides a method to create a {@link PluginContext}.
                @Nullable PluginHost pluginHost = null;
                if (mPluginApplication != null) {
                    pluginHost = mPluginApplication.getPluginHost();
                }

                PluginCallback publicPluginCallback =
                        CallbackConverter.toPublicCallback(pluginCallback);
                try {
                    mPluginExecutor.load(
                            info, publicPluginCallback, pluginHost, pluginContextInitData);
                } catch (RemoteException e) {
                    try {
                        pluginCallback.onFailure(FailureType.ERROR_LOADING_PLUGIN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback failed.");
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
                    mPluginExecutor.execute(input, pluginName, publicPluginCallback);
                } catch (RemoteException e) {
                    try {
                        pluginCallback.onFailure(FailureType.ERROR_EXECUTING_PLUGIN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback failed.");
                    }
                }
            }

            @Override
            public void unload(String pluginName, IPluginCallback pluginCallback) {
                PluginCallback publicPluginCallback =
                        CallbackConverter.toPublicCallback(pluginCallback);
                try {
                    mPluginExecutor.unload(pluginName, publicPluginCallback);
                } catch (RemoteException e) {
                    try {
                        pluginCallback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback failed.");
                    }
                }
            }

            @Override
            public void checkPluginState(String pluginName, IPluginStateCallback stateCallback) {
                try {
                    mPluginExecutor.checkPluginState(pluginName, stateCallback);
                } catch (RemoteException e) {
                    try {
                        stateCallback.onState(PluginState.STATE_EXCEPTION_THROWN);
                    } catch (RemoteException e2) {
                        Log.w(TAG, "Callback failed.");
                    }
                }
            }
        };
    }
}
