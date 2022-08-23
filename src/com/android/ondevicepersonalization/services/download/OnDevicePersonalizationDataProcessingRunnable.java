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

package com.android.ondevicepersonalization.services.download;

import android.content.pm.PackageInfo;
import android.util.Log;

/**
 * Runnable to handle the processing of the downloaded vendor data
 */
public class OnDevicePersonalizationDataProcessingRunnable implements Runnable {
    private final PackageInfo mPackageInfo;
    private static final String TAG = "OnDevicePersonalizationDataProcessingRunnable";

    public OnDevicePersonalizationDataProcessingRunnable(PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
    }

    /**
     * Processes the downloaded files for the given package and stores the data into sqlite
     * vendor tables
     */
    public void run() {
        // TODO(b/239479120): Implement this method
        Log.d(TAG, "Package Name: " + mPackageInfo.packageName);
    }
}
