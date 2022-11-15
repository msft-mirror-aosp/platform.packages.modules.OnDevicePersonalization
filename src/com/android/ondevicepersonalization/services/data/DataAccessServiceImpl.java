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

package com.android.ondevicepersonalization.services.data;

import android.annotation.NonNull;
import android.content.Context;
import android.ondevicepersonalization.Constants;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to request data from the managing service.
 */
public class DataAccessServiceImpl extends IDataAccessService.Stub {
    private static final String TAG = "DataAccessServiceImpl";
    private final Context mApplicationContext;

    public DataAccessServiceImpl(
            @NonNull String appPackageName,
            @NonNull String vendorPackageName,
            @NonNull Context applicationContext) {
        mApplicationContext = applicationContext;
        // TODO(b/249345663): Create DAOs for VendorData tables owned by vendorPackageName and
        // create a policy-engine guarded UserData accessor.
    }

    /** Handle a request from the isolated process. */
    @Override public void onRequest(
            int operation,
            @NonNull Bundle params,
            @NonNull IDataAccessServiceCallback callback
    ) {
        try {
            callback.onError(Constants.STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }
}
