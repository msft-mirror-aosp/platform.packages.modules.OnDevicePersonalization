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
import android.content.pm.PackageManager;
import android.ondevicepersonalization.Constants;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import java.util.HashMap;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to request data from the managing service.
 */
public class DataAccessServiceImpl extends IDataAccessService.Stub {
    private static final String TAG = "DataAccessServiceImpl";
    private final Context mApplicationContext;
    private final OnDevicePersonalizationVendorDataDao mVendorDataDao;
    private final boolean mIncludeUserData;

    public DataAccessServiceImpl(
            @NonNull String appPackageName,
            @NonNull String vendorPackageName,
            @NonNull Context applicationContext,
            boolean includeUserData) {
        mApplicationContext = applicationContext;
        try {
            mVendorDataDao = OnDevicePersonalizationVendorDataDao.getInstance(mApplicationContext,
                    vendorPackageName,
                    PackageUtils.getCertDigest(mApplicationContext, vendorPackageName));
            mIncludeUserData = includeUserData;
            // TODO(b/249345663): Create a policy-engine guarded UserData accessor.
            // if mIncludeUserData is true, also create a R/W DAO for the LOCAL_DATA table.

        } catch (PackageManager.NameNotFoundException nnfe) {
            throw new IllegalArgumentException("Package: " + vendorPackageName + " does not exist.",
                    nnfe);
        }
    }

    /** Handle a request from the isolated process. */
    @Override
    public void onRequest(
            int operation,
            @NonNull Bundle params,
            @NonNull IDataAccessServiceCallback callback
    ) {
        try {
            switch (operation) {
                case Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP:
                    OnDevicePersonalizationExecutors.getBackgroundExecutor().execute(
                            () -> remoteDataLookup(
                                    params.getStringArray(Constants.EXTRA_LOOKUP_KEYS), callback));
                    break;
                case Constants.DATA_ACCESS_OP_REMOTE_DATA_SCAN:
                default:
                    callback.onError(Constants.STATUS_INTERNAL_ERROR);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    private void remoteDataLookup(String[] keys, @NonNull IDataAccessServiceCallback callback) {
        HashMap<String, byte[]> vendorData = new HashMap<>();
        try {
            for (String key : keys) {
                vendorData.put(key, mVendorDataDao.readSingleVendorDataRow(key));
            }
            Bundle result = new Bundle();
            result.putSerializable(Constants.EXTRA_RESULT, vendorData);
            callback.onSuccess(result);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error in remoteDataLookup", e);
        }
    }
}
