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

import static android.ondevicepersonalization.OnDevicePersonalizationSystemServiceManager.ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemService;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.SystemService;

/**
 * @hide
 */
public class OnDevicePersonalizationSystemService
        extends IOnDevicePersonalizationSystemService.Stub {
    private static final String TAG = "ondevicepersonalization";
    private final Context mContext;

    OnDevicePersonalizationSystemService(Context context) {
        mContext = context;
    }

    @Override public void onRequest(
            Bundle bundle,
            IOnDevicePersonalizationSystemServiceCallback callback) {
        try {
            callback.onResult(Bundle.EMPTY);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        private OnDevicePersonalizationSystemService mService;

        /** @hide */
        public Lifecycle(Context context) {
            super(context);
            if (!isOdpSupported(context)) {
                return;
            }
            mService = new OnDevicePersonalizationSystemService(getContext());
        }

        /** @hide */
        @Override
        public void onStart() {
            if (mService == null) {
                Log.i(TAG, "OnDevicePersonalizationSystemService not started!");
                return;
            }
            publishBinderService(ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE, mService);
            Log.i(TAG, "OnDevicePersonalizationSystemService started!");
        }

            /** Returns true if the device supports ODP. */
        private static boolean isOdpSupported(Context context) {
            final PackageManager pm = context.getPackageManager();
            if (pm == null) {
                Log.e(TAG, "PackageManager not found.");
                return false;
            }
            return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                    && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                    // Android TV
                    && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                    // Android Go
                    && !pm.hasSystemFeature(PackageManager.FEATURE_RAM_LOW);
        }
    }
}
