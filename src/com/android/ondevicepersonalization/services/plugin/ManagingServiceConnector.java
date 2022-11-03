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
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to request data from the managing service.
 */
public class ManagingServiceConnector extends IManagingServiceConnector.Stub {
    private static final String TAG = "ManagingServiceConnector";

    public static final int ERROR_NOT_IMPLEMENTED = 100;
    private final Context mApplicationContext;

    ManagingServiceConnector(@NonNull Context applicationContext) {
        mApplicationContext = applicationContext;
        // TODO(b/249345663): Create DAOs for VendorData and UserData tables
        // to handle data access requests from vendor code.
    }

    /** Handle a request from the isolated process. */
    // TODO(b/249345663): Replace the generic method below with strongly typed
    // methods for each type of managing service request.
    @Override public void handleManagingServiceRequest(
            int operation,
            @NonNull Bundle params,
            @NonNull IManagingServiceConnectorCallback callback
    ) {
        try {
            callback.onError(ERROR_NOT_IMPLEMENTED);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }
}
