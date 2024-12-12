/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.ondevicepersonalization.testing.utils;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ext.SdkExtensions;

import androidx.test.platform.app.InstrumentationRegistry;

/** Helper to check if device is enabled or supports OnDevicePersonalization module */
public final class DeviceSupportHelper {
    private static final int MIN_SDK_EXT = 13;  // M2024-08

    /**
     * Check whether the device is supported.
     * OnDevicePersonalization module doesn't support Wear, Auto, TV, Go device
     * @return if the device is supported.
     */
    public static boolean isDeviceSupported() {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final PackageManager pm = instrumentation.getContext().getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                // Android TV
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                // Android Go
                && !pm.hasSystemFeature(PackageManager.FEATURE_RAM_LOW);
    }

    /**
     * Check whether the ODP module with public APIs is installed on the device.
     * For CTS only.
     */
    public static boolean isOdpModuleAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                || SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES) >= MIN_SDK_EXT;
    }
}
