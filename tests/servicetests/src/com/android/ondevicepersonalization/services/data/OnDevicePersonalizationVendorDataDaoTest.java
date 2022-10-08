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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationVendorDataDaoTest {
    private static final String TEST_OWNER = "owner";
    private static final String TEST_CERT_DIGEST = "certDigest";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationVendorDataDao mDao;

    @Before
    public void setup() {
        mDao = OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, TEST_OWNER,
                TEST_CERT_DIGEST);
    }

    @Test
    public void testInsert() {
        VendorData data = new VendorData.Builder().setKey("key").setData(new byte[10]).setFp(
                "fp").build();
        boolean insertResult = mDao.updateOrInsertVendorData(data);
        assertTrue(insertResult);
    }

    @Test
    public void testBatchInsert() {
        List<VendorData> dataList = new ArrayList<>();
        dataList.add(new VendorData.Builder().setKey("key").setData(new byte[10]).setFp(
                "fp").build());
        dataList.add(new VendorData.Builder().setKey("key2").setData(new byte[10]).setFp(
                "fp2").build());
        boolean insertResult = mDao.batchUpdateOrInsertVendorDataTransaction(dataList,
                System.currentTimeMillis());
        assertTrue(insertResult);
    }

    @Test
    public void testInsertSyncToken() {
        long timestamp = System.currentTimeMillis();
        boolean insertResult = mDao.updateOrInsertSyncToken(timestamp);
        assertTrue(insertResult);

        long timestampFromDB = mDao.getSyncToken();
        assertEquals(timestamp, timestampFromDB);
    }

    @Test
    public void testFailReadSyncToken() {
        long timestampFromDB = mDao.getSyncToken();
        assertEquals(-1L, timestampFromDB);
    }

    @Test
    public void testGetInstance() {
        OnDevicePersonalizationVendorDataDao instance1Owner1 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, "owner1",
                        TEST_CERT_DIGEST);
        OnDevicePersonalizationVendorDataDao instance2Owner1 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, "owner1",
                        TEST_CERT_DIGEST);
        assertEquals(instance1Owner1, instance2Owner1);
        OnDevicePersonalizationVendorDataDao instance1Owner2 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, "owner2",
                        TEST_CERT_DIGEST);
        assertNotEquals(instance1Owner1, instance1Owner2);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
        OnDevicePersonalizationVendorDataDao.clearInstance(TEST_OWNER, TEST_CERT_DIGEST);
    }
}
