/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.serviceflow;

import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.PersistableBundle;

import com.android.ondevicepersonalization.services.request.AppRequestFlow;
import com.android.ondevicepersonalization.services.request.RenderFlow;

/** Factory for service flow instances. */
public class ServiceFlowFactory {

    /** Create a service flow instance give the type. */
    public static ServiceFlow createInstance(ServiceFlowType serviceFlowType, Object... args) {
        return switch (serviceFlowType) {
            case APP_REQUEST_FLOW -> new AppRequestFlow(
                    (String) args[0], (ComponentName) args[1], (PersistableBundle) args[2],
                    (IExecuteCallback) args[3], (Context) args[4], (long) args[5]);
            case RENDER_FLOW -> new RenderFlow((String) args[0], (IBinder) args[1], (int) args[2],
                    (int) args[3], (int) args[4], (IRequestSurfacePackageCallback) args[5],
                    (Context) args[6], (long) args[7]);
            default -> throw new IllegalArgumentException(
                    "Invalid service flow type: " + serviceFlowType);
        };
    }
}
