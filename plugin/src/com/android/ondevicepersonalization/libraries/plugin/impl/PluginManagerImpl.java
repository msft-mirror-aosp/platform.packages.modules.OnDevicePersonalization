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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.android.ondevicepersonalization.libraries.plugin.PluginController;
import com.android.ondevicepersonalization.libraries.plugin.PluginInfo;
import com.android.ondevicepersonalization.libraries.plugin.PluginManager;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginExecutorService;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginExecutorServiceProvider;

import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Used by clients to create new plugins and receive {@link PluginController} interfaces to control
 * them.
 */
public class PluginManagerImpl implements PluginManager, PluginExecutorServiceProvider {
    private static final Executor sSingleThreadExecutor = Executors.newSingleThreadExecutor();
    private final Context mApplicationContext;
    private IPluginExecutorService mPluginExecutorService;
    private SettableFuture<Boolean> mPluginExecutorServiceReadiness = SettableFuture.create();

    PluginManagerImpl(Context applicationContext) {
        this.mApplicationContext = applicationContext;
    }

    @Override
    public @Nullable IPluginExecutorService getExecutorService() {
        return mPluginExecutorService;
    }

    @Override
    public SettableFuture<Boolean> getExecutorServiceReadiness() {
        return mPluginExecutorServiceReadiness;
    }

    private final ServiceConnection mConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mPluginExecutorService = IPluginExecutorService.Stub.asInterface(service);
                    mPluginExecutorServiceReadiness.set(true);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mPluginExecutorService = null;
                    mPluginExecutorServiceReadiness = SettableFuture.create();
                }
            };

    @Override
    public PluginController createPluginController(PluginInfo info)
            throws ServiceNotFoundException {
        Intent intent = new Intent(mApplicationContext, PluginExecutorService.class);
        // TODO(b/242358527): move the bindService() call to the PluginController.
        if (!mApplicationContext.bindService(
                intent, Context.BIND_AUTO_CREATE, sSingleThreadExecutor, mConnection)) {
            // TODO(b/242358527): don't surface an Exception here.
            throw new ServiceNotFoundException(
                    "Could not bind to service " + intent.getComponent());
        }

        return new PluginControllerImpl(mApplicationContext, this, info);
    }
}
