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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationVendorDataDaoTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationVendorDataDao mDao;
    private static String sTestOwner = "owner";
    private static String sTestCertDigest = "certDigest";

    @Before
    public void setup() {
        mDao = OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, sTestOwner,
                sTestCertDigest);
    }

    @Test
    public void testInsert() {
        boolean insertResult = mDao.updateOrInsertVendorData("key", new byte[10], "fp");
        assertTrue(insertResult);
    }

    @Test
    public void testInsertNullData() {
        boolean insertResult = mDao.updateOrInsertVendorData("key", null, "fp");
        assertFalse(insertResult);
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
                        sTestCertDigest);
        OnDevicePersonalizationVendorDataDao instance2Owner1 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, "owner1",
                        sTestCertDigest);
        assertEquals(instance1Owner1, instance2Owner1);
        OnDevicePersonalizationVendorDataDao instance1Owner2 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, "owner2",
                        sTestCertDigest);
        assertNotEquals(instance1Owner1, instance1Owner2);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
        OnDevicePersonalizationVendorDataDao.clearInstance(sTestOwner, sTestCertDigest);
    }
}
