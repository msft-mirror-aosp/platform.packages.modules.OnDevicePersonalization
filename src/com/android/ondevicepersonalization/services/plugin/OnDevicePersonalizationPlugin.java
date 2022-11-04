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

import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginContext;

/** Plugin that runs in an isolated process. */
public class OnDevicePersonalizationPlugin implements Plugin {
    private static final String TAG = "OnDevicePersonalizationPlugin";

    @Override public void onExecute(
            PersistableBundle input,
            PluginCallback callback,
            @Nullable PluginContext context) {
        Log.i(TAG, "Executing plugin.");
        try {
            // TODO(b/228200518): Extract vendor class name from 'input'
            // and invoke its handler method.
            callback.onFailure(FailureType.ERROR_UNKNOWN);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error.");
        }
    }
}
