/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.ondevicepersonalization;

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.ACCESS_SYSTEM_SERVER_SERVICE;
import static android.ondevicepersonalization.OnDevicePersonalizationSystemServiceManager.ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE;

import android.adservices.ondevicepersonalization.Constants;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemService;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.util.Objects;

/**
 * @hide
 */
public class OnDevicePersonalizationSystemService
        extends IOnDevicePersonalizationSystemService.Stub {
    private static final String TAG = "ondevicepersonalization";
    // TODO(b/302991763): set up per-user directory if needed.
    private static final String ODP_BASE_DIR = "/data/system/ondevicepersonalization/0/";
    private static final String CONFIG_FILE_IDENTIFIER = "CONFIG";
    public static final String PERSONALIZATION_STATUS_KEY = "PERSONALIZATION_STATUS";
    private final Context mContext;
    private BooleanFileDataStore mDataStore = null;

    // TODO(b/302992251): use a manager to access configs instead of directly exposing DataStore.

    OnDevicePersonalizationSystemService(Context context) {
        this(context, new BooleanFileDataStore(ODP_BASE_DIR, CONFIG_FILE_IDENTIFIER));
    }

    @VisibleForTesting
    OnDevicePersonalizationSystemService(Context context, BooleanFileDataStore dataStore) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(dataStore);
        mContext = context;
        try {
            this.mDataStore = dataStore;
            mDataStore.initialize();
        } catch (Exception e) {
            Log.e(TAG, "Cannot initialize system service datastore.", e);
            mDataStore = null;
        }
    }

    @Override public void onRequest(
            Bundle bundle,
            IOnDevicePersonalizationSystemServiceCallback callback) {
        enforceCallingPermission();
        sendResult(callback, null);
    }

    @Override
    public void setPersonalizationStatus(
            boolean enabled,
            IOnDevicePersonalizationSystemServiceCallback callback) {
        enforceCallingPermission();
        Bundle result = new Bundle();
        try {
            mDataStore.put(PERSONALIZATION_STATUS_KEY, enabled);
            // Confirm the value was updated.
            Boolean statusResult = mDataStore.get(PERSONALIZATION_STATUS_KEY);
            if (statusResult == null || statusResult.booleanValue() != enabled) {
                sendError(callback, Constants.STATUS_INTERNAL_ERROR);
                return;
            }
            // Echo the result back
            result.putBoolean(PERSONALIZATION_STATUS_KEY, statusResult);
        } catch (Exception e) {
            Log.e(TAG, "Unable to persist personalization status", e);
            sendError(callback, Constants.STATUS_INTERNAL_ERROR);
            return;
        }

        sendResult(callback, result);
    }

    @Override
    public void readPersonalizationStatus(
            IOnDevicePersonalizationSystemServiceCallback callback) {
        enforceCallingPermission();
        Boolean result = null;

        try {
            result = mDataStore.get(PERSONALIZATION_STATUS_KEY);
        } catch (Exception e) {
            Log.e(TAG, "Error reading datastore", e);
            sendError(callback, Constants.STATUS_INTERNAL_ERROR);
            return;
        }

        if (result == null) {
            Log.d(TAG, "Unable to restore personalization status");
            sendError(callback, Constants.STATUS_KEY_NOT_FOUND);
        } else {
            Bundle bundle = new Bundle();
            bundle.putBoolean(PERSONALIZATION_STATUS_KEY, result.booleanValue());
            sendResult(callback, bundle);
        }
    }

    private void sendResult(
            IOnDevicePersonalizationSystemServiceCallback callback, Bundle bundle) {
        try {
            callback.onResult(bundle);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    private void sendError(
            IOnDevicePersonalizationSystemServiceCallback callback, int errorCode) {
        try {
            callback.onError(errorCode);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    @VisibleForTesting
    void enforceCallingPermission() {
        if (mContext.checkCallingPermission(ACCESS_SYSTEM_SERVER_SERVICE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("ODP System Service Permission denied");
        }
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        private OnDevicePersonalizationSystemService mService;

        /** @hide */
        public Lifecycle(Context context) {
            super(context);
            mService = new OnDevicePersonalizationSystemService(getContext());
        }

        /** @hide */
        @Override
        public void onStart() {
            if (mService == null || mService.mDataStore == null) {
                Log.e(TAG, "OnDevicePersonalizationSystemService not started!");
                return;
            }
            publishBinderService(ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE, mService);
            Log.i(TAG, "OnDevicePersonalizationSystemService started!");
        }
    }
}
