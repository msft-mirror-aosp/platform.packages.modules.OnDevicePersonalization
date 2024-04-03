/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.reset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.vendor.FileUtils;
import com.android.ondevicepersonalization.services.data.vendor.LocalData;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationLocalDataDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class ResetDataTaskTest {
    private static final ComponentName TEST_OWNER = new ComponentName("ownerPkg", "ownerCls");
    private static final String TEST_CERT_DIGEST = "certDigest";
    private static final String TASK_IDENTIFIER = "task";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationVendorDataDao mDao;
    private OnDevicePersonalizationLocalDataDao mLocalDao;
    private EventsDao mEventsDao;

    @Before
    public void setup() {
        mDao = OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, TEST_OWNER,
                TEST_CERT_DIGEST);
        mLocalDao = OnDevicePersonalizationLocalDataDao.getInstanceForTest(mContext, TEST_OWNER,
                TEST_CERT_DIGEST);
        mEventsDao = EventsDao.getInstanceForTest(mContext);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();

        File vendorDir = new File(mContext.getFilesDir(), "VendorData");
        File localDir = new File(mContext.getFilesDir(), "LocalData");
        FileUtils.deleteDirectory(vendorDir);
        FileUtils.deleteDirectory(localDir);
    }

    @Test
    public void testResetDataTask() throws Exception {
        addTestData(System.currentTimeMillis() - 100);

        assertEquals(1, OnDevicePersonalizationVendorDataDao.getVendors(mContext).size());
        assertEquals(2, mEventsDao.readAllNewRowsForPackage(TEST_OWNER, 0, 0).size());
        assertNotNull(mLocalDao.readSingleLocalDataRow("key"));

        ResetDataTask.deleteMeasurementData();

        assertEquals(0, OnDevicePersonalizationVendorDataDao.getVendors(mContext).size());
        assertEquals(0, mEventsDao.readAllNewRowsForPackage(TEST_OWNER, 0, 0).size());
        assertNull(mLocalDao.readSingleLocalDataRow("key"));
    }

    private void addTestData(long timestamp) {
        List<VendorData> dataList = new ArrayList<>();
        dataList.add(new VendorData.Builder().setKey("key").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("key2").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("large").setData(new byte[111111]).build());
        dataList.add(new VendorData.Builder().setKey("large2").setData(new byte[111111]).build());
        dataList.add(new VendorData.Builder().setKey("xlarge").setData(new byte[5555555]).build());

        List<String> retainedKeys = new ArrayList<>();
        retainedKeys.add("key");
        retainedKeys.add("key2");
        retainedKeys.add("large");
        retainedKeys.add("large2");
        retainedKeys.add("xlarge");
        assertTrue(mDao.batchUpdateOrInsertVendorDataTransaction(dataList, retainedKeys,
                timestamp));

        mLocalDao.updateOrInsertLocalData(new LocalData.Builder()
                        .setData(new byte[1])
                        .setKey("key")
                .build());

        Query query = new Query.Builder(
                timestamp,
                "com.app",
                TEST_OWNER,
                TEST_CERT_DIGEST,
                "query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId = mEventsDao.insertQuery(query);

        Event event = new Event.Builder()
                .setType(1)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setService(TEST_OWNER)
                .setQueryId(queryId)
                .setTimeMillis(timestamp)
                .setRowIndex(0)
                .build();
        mEventsDao.insertEvent(event);

        EventState eventState = new EventState.Builder()
                .setTaskIdentifier(TASK_IDENTIFIER)
                .setService(TEST_OWNER)
                .setToken(new byte[]{1})
                .build();
        mEventsDao.updateOrInsertEventState(eventState);
    }
}
