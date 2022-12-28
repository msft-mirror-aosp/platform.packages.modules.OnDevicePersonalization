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

import static android.content.pm.PackageManager.GET_META_DATA;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.VendorData;
import com.android.ondevicepersonalization.services.data.VendorDataContract;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.download.mdd.OnDevicePersonalizationFileGroupPopulator;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.RemoveFileGroupsByFilterRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationDataProcessingAsyncCallableManualTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationFileGroupPopulator mPopulator;
    private MobileDataDownload mMdd;
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private final VendorData mContent1 = new VendorData.Builder()
            .setKey("key1")
            .setData("dGVzdGRhdGEx".getBytes())
            .setFp("fp1")
            .build();

    private final VendorData mContent2 = new VendorData.Builder()
            .setKey("key2")
            .setData("dGVzdGRhdGEy".getBytes())
            .setFp("fp2")
            .build();

    @Before
    public void setup() throws Exception {
        mPackageName = mContext.getPackageName();
        mPackageInfo = mContext.getPackageManager().getPackageInfo(
                mPackageName, PackageManager.PackageInfoFlags.of(GET_META_DATA));
        mMdd = MobileDataDownloadFactory.getMdd(mContext);
        mPopulator = new OnDevicePersonalizationFileGroupPopulator(mContext);
        RemoveFileGroupsByFilterRequest request =
                RemoveFileGroupsByFilterRequest.newBuilder().build();
        MobileDataDownloadFactory.getMdd(mContext).removeFileGroupsByFilter(request).get();

        // Initialize the DB as a test instance
        OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, mPackageName,
                PackageUtils.getCertDigest(mContext, mPackageName));
    }

    @Test
    public void testRun() throws Exception {
        mPopulator.refreshFileGroups(mMdd).get();
        String fileGroupName = OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                mPackageName, mContext);
        // Trigger the download immediately.
        mMdd.downloadFileGroup(
                DownloadFileGroupRequest.newBuilder().setGroupName(fileGroupName).build()).get();

        OnDevicePersonalizationDataProcessingAsyncCallable callable =
                new OnDevicePersonalizationDataProcessingAsyncCallable(mPackageInfo, mContext);
        callable.call().get();
        OnDevicePersonalizationVendorDataDao dao =
                OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, mPackageName,
                        PackageUtils.getCertDigest(mContext, mPackageName));
        Cursor cursor = dao.readAllVendorData();
        List<VendorData> vendorDataList = new ArrayList<>();
        while (cursor.moveToNext()) {
            String key = cursor.getString(
                    cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.KEY));

            byte[] data = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.DATA));

            String fp = cursor.getString(
                    cursor.getColumnIndexOrThrow(VendorDataContract.VendorDataEntry.FP));
            vendorDataList.add(new VendorData.Builder()
                    .setKey(key)
                    .setData(data)
                    .setFp(fp)
                    .build());
        }
        cursor.close();
        assertEquals(2, vendorDataList.size());
        for (VendorData data : vendorDataList) {
            if (data.getKey().equals(mContent1.getKey())) {
                compareDataContent(mContent1, data);
            } else if (data.getKey().equals(mContent2.getKey())) {
                compareDataContent(mContent2, data);
            } else {
                fail("Vendor data from DB contains unexpected key");
            }
        }
    }

    private void compareDataContent(VendorData expectedData, VendorData actualData) {
        assertEquals(expectedData.getKey(), actualData.getKey());
        assertArrayEquals(expectedData.getData(), actualData.getData());
        assertEquals(expectedData.getFp(), actualData.getFp());
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
