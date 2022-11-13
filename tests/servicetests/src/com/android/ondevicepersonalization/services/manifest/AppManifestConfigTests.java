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

package com.android.ondevicepersonalization.services.manifest;

import static android.content.pm.PackageManager.GET_META_DATA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AppManifestConfigTests {
    private static final String BASE_DOWNLOAD_URL =
            "https://www.gstatic.com/ondevicepersonalization/testing/test_data1.json";
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testManifestContainsOdpSettings() throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mContext.getPackageName(), PackageManager.PackageInfoFlags.of(GET_META_DATA));
        assertTrue(AppManifestConfigHelper.manifestContainsOdpSettings(mContext, packageInfo));
    }

    @Test
    public void testManifestContainsOdpSettingsFalse() throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mContext.getPackageName(), PackageManager.PackageInfoFlags.of(GET_META_DATA));
        packageInfo.packageName = "nonExistentName";
        assertFalse(AppManifestConfigHelper.manifestContainsOdpSettings(mContext, packageInfo));
    }

    @Test
    public void testGetDownloadUrlFromOdpSettings() throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mContext.getPackageName(), PackageManager.PackageInfoFlags.of(GET_META_DATA));
        assertEquals(BASE_DOWNLOAD_URL,
                AppManifestConfigHelper.getDownloadUrlFromOdpSettings(mContext, packageInfo));
    }

    @Test
    public void testGetDownloadHandlerFromOdpSettings()
            throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mContext.getPackageName(), PackageManager.PackageInfoFlags.of(GET_META_DATA));
        assertEquals("com.test.VendorDownloadHandler",
                AppManifestConfigHelper.getDownloadHandlerFromOdpSettings(mContext, packageInfo));
    }
}
