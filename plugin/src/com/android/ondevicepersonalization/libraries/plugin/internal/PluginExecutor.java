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

import android.content.Context;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginContext;
import com.android.ondevicepersonalization.libraries.plugin.PluginContextProvider;
import com.android.ondevicepersonalization.libraries.plugin.PluginState;

import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;

/** Loads and executes plugins in the current process. */
public class PluginExecutor {
    private static final ImmutableSet<String> CONTAINER_ALLOWLIST = ImmutableSet.of();
    private final Map<String, Plugin> mPlugins = new HashMap<>();
    private final Map<String, PluginContext> mPluginContexts = new HashMap<>();
    private final Context mContext;

    /** Creates a {@link PluginExecutor}. */
    public static PluginExecutor create(Context context) {
        return new PluginExecutor(context);
    }

    /** Loads a plugin. */
    public void load(
            PluginInfoInternal info,
            PluginCallback callback,
            PluginContextProvider pluginContextProvider)
            throws RemoteException {
        Plugin plugin =
                PluginLoader.loadPlugin(
                        info.entryPointClassName(),
                        info.pluginCodeList(),
                        mContext.getClassLoader(),
                        CONTAINER_ALLOWLIST);
        if (plugin == null) {
            callback.onFailure(FailureType.ERROR_LOADING_PLUGIN);
            return;
        }

        // TODO(b/239079452) : Use unique id to identify plugins.
        String pluginId = info.taskName();

        mPlugins.put(pluginId, plugin);
        mPluginContexts.put(pluginId, pluginContextProvider.createPluginContext(pluginId));

        // TODO(b/239079143): Add more specific methods to the callback.
        callback.onSuccess(PersistableBundle.EMPTY);
    }

    /** Executes a plugin. */
    public void execute(
            PersistableBundle input,
            String pluginId,
            PluginCallback callback,
            PluginContext pluginContext)
            throws RemoteException {
        if (!mPlugins.containsKey(pluginId)) {
            callback.onFailure(FailureType.ERROR_EXECUTING_PLUGIN);
            return;
        }

        mPlugins.get(pluginId).onExecute(input, callback, mPluginContexts.get(pluginId));
    }

    /** Unloads a plugin. */
    public void unload(String pluginId, PluginCallback callback, PluginContext pluginContext)
            throws RemoteException {
        if (!mPlugins.containsKey(pluginId)) {
            callback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
            return;
        }
        if (!mPluginContexts.containsKey(pluginId)) {
            callback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
            return;
        }
        mPlugins.remove(pluginId);
        mPluginContexts.remove(pluginId);
        callback.onSuccess(PersistableBundle.EMPTY);
    }

    /** Checks the plugin state and returns it via stateCallback. */
    public void checkPluginState(String pluginId, IPluginStateCallback stateCallback)
            throws RemoteException {
        if (!mPlugins.containsKey(pluginId)) {
            stateCallback.onState(PluginState.STATE_NOT_LOADED);
            return;
        }
        if (!mPluginContexts.containsKey(pluginId)) {
            stateCallback.onState(PluginState.STATE_NOT_LOADED);
            return;
        }
        stateCallback.onState(PluginState.STATE_LOADED);
    }

    private PluginExecutor(Context context) {
        this.mContext = context;
    }
}
