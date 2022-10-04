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

package com.android.ondevicepersonalization.services.download.mdd;

import static android.content.pm.PackageManager.GET_META_DATA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.android.libraries.mobiledatadownload.RemoveFileGroupsByFilterRequest;
import com.google.android.libraries.mobiledatadownload.RemoveFileGroupsByFilterResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationFileGroupPopulatorTest {
    private static final String BASE_URL =
            "https://www.gstatic.com/ondevicepersonalization/testing/test_data1.json";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationFileGroupPopulator mPopulator;
    private String mPackageName;

    @Before
    public void setup() throws Exception {
        mPackageName = mContext.getPackageName();
        mPopulator = new OnDevicePersonalizationFileGroupPopulator(mContext);
        RemoveFileGroupsByFilterRequest request =
                RemoveFileGroupsByFilterRequest.newBuilder().build();
        MobileDataDownloadFactory.getMdd(mContext).removeFileGroupsByFilter(request).get();
    }

    @Test
    public void testRefreshFileGroup() throws Exception {
        mPopulator.refreshFileGroups(MobileDataDownloadFactory.getMdd(mContext)).get();
        RemoveFileGroupsByFilterRequest request =
                RemoveFileGroupsByFilterRequest.newBuilder().build();
        RemoveFileGroupsByFilterResponse response = MobileDataDownloadFactory.getMdd(
                mContext).removeFileGroupsByFilter(request).get();
        assertEquals(1, response.removedFileGroupsCount());
    }

    @Test
    public void testCreateDownloadUrlNoSyncToken() throws Exception {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mPackageName, PackageManager.PackageInfoFlags.of(GET_META_DATA));
        String downloadUrl = OnDevicePersonalizationFileGroupPopulator.createDownloadUrl(
                packageInfo, mContext);
        assertTrue(downloadUrl.startsWith(BASE_URL));
    }

    @Test
    public void testCreateDownloadUrl() throws Exception {
        long timestamp = System.currentTimeMillis();
        assertTrue(OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, mPackageName,
                PackageUtils.getCertDigest(mContext, mPackageName)).updateOrInsertSyncToken(
                timestamp));

        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mPackageName, PackageManager.PackageInfoFlags.of(GET_META_DATA));
        String downloadUrl = OnDevicePersonalizationFileGroupPopulator.createDownloadUrl(
                packageInfo, mContext);
        assertTrue(downloadUrl.startsWith(BASE_URL));
        assertTrue(downloadUrl.contains(String.valueOf(timestamp)));
    }

    @After
    public void cleanup() throws Exception {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
        OnDevicePersonalizationVendorDataDao.clearInstance(mPackageName,
                PackageUtils.getCertDigest(mContext, mPackageName));
    }
}
