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

package com.android.ondevicepersonalization.services.maintenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationLocalDataDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationMaintenanceJobServiceTest {
    private static final String TEST_OWNER = "owner";
    private static final String TEST_CERT_DIGEST = "certDigest";
    private static final String TASK_IDENTIFIER = "task";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
    private OnDevicePersonalizationVendorDataDao mTestDao;
    private OnDevicePersonalizationVendorDataDao mDao;

    private EventsDao mEventsDao;
    private OnDevicePersonalizationMaintenanceJobService mSpyService;
    private UserPrivacyStatus mPrivacyStatus = UserPrivacyStatus.getInstance();

    private static void addTestData(long timestamp, OnDevicePersonalizationVendorDataDao dao) {
        // Add vendor data
        List<VendorData> dataList = new ArrayList<>();
        dataList.add(new VendorData.Builder().setKey("key").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("key2").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("large").setData(new byte[111111]).build());
        dataList.add(new VendorData.Builder().setKey("large2").setData(new byte[111111]).build());
        List<String> retainedKeys = new ArrayList<>();
        retainedKeys.add("key");
        retainedKeys.add("key2");
        retainedKeys.add("large");
        retainedKeys.add("large2");
        assertTrue(dao.batchUpdateOrInsertVendorDataTransaction(dataList, retainedKeys,
                timestamp));
    }

    private void addEventData(String packageName, long timestamp) {
        Query query = new Query.Builder()
                .setTimeMillis(timestamp)
                .setServiceName(packageName)
                .setQueryData("query".getBytes(StandardCharsets.UTF_8))
                .build();
        long queryId = mEventsDao.insertQuery(query);

        Event event = new Event.Builder()
                .setType(1)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setServiceName(packageName)
                .setQueryId(queryId)
                .setTimeMillis(timestamp)
                .setRowIndex(0)
                .build();
        mEventsDao.insertEvent(event);

        EventState eventState = new EventState.Builder()
                .setTaskIdentifier(TASK_IDENTIFIER)
                .setServiceName(packageName)
                .setToken(new byte[]{1})
                .build();
        mEventsDao.updateOrInsertEventState(eventState);
    }

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        PhFlagsTestUtil.disablePersonalizationStatusOverride();
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization caller_app_allow_list "
                        + mContext.getPackageName());
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization isolated_service_allow_list "
                        + mContext.getPackageName());

        mPrivacyStatus.setPersonalizationStatusEnabled(true);
        mTestDao = OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, TEST_OWNER,
                TEST_CERT_DIGEST);
        mDao = OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext,
                mContext.getPackageName(),
                PackageUtils.getCertDigest(mContext, mContext.getPackageName()));
        mEventsDao = EventsDao.getInstanceForTest(mContext);

        mSpyService = spy(new OnDevicePersonalizationMaintenanceJobService());
    }

    @Test
    public void onStartJobTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().spyStatic(
                OnDevicePersonalizationExecutors.class).strictness(
                Strictness.LENIENT).startMocking();
        try {
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            doReturn(mContext.getPackageManager()).when(mSpyService).getPackageManager();
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getBackgroundExecutor);
            ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService()).when(
                    OnDevicePersonalizationExecutors::getLightweightExecutor);

            boolean result = mSpyService.onStartJob(mock(JobParameters.class));
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        MockitoSession session = ExtendedMockito.mockitoSession().startMocking();
        try {
            doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            mSpyService.schedule(mContext);
            assertTrue(mJobScheduler.getPendingJob(
                    OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                    != null);
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            boolean result = mSpyService.onStartJob(mock(JobParameters.class));
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
            assertTrue(mJobScheduler.getPendingJob(
                    OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                    == null);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStartJobTestPersonalizationBlocked() {
        mPrivacyStatus.setPersonalizationStatusEnabled(false);
        MockitoSession session = ExtendedMockito.mockitoSession().startMocking();
        try {
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            boolean result = mSpyService.onStartJob(mock(JobParameters.class));
            assertTrue(result);
            verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void onStopJobTest() {
        MockitoSession session = ExtendedMockito.mockitoSession().strictness(
                Strictness.LENIENT).startMocking();
        try {
            assertTrue(mSpyService.onStopJob(mock(JobParameters.class)));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testVendorDataCleanup() throws Exception {
        long timestamp = System.currentTimeMillis();
        addTestData(timestamp, mTestDao);
        addTestData(timestamp, mDao);
        addEventData(mContext.getPackageName(), timestamp);
        addEventData(mContext.getPackageName(), 100L);
        addEventData(TEST_OWNER, timestamp);

        OnDevicePersonalizationMaintenanceJobService.cleanupVendorData(mContext);
        File dir = new File(OnDevicePersonalizationVendorDataDao.getFileDir(
                OnDevicePersonalizationVendorDataDao.getTableName(mContext.getPackageName(),
                        PackageUtils.getCertDigest(mContext, mContext.getPackageName())),
                mContext.getFilesDir()));
        File testDir = new File(OnDevicePersonalizationVendorDataDao.getFileDir(
                OnDevicePersonalizationVendorDataDao.getTableName(TEST_OWNER, TEST_CERT_DIGEST),
                mContext.getFilesDir()));
        File localTestDir = new File(OnDevicePersonalizationLocalDataDao.getFileDir(
                OnDevicePersonalizationLocalDataDao.getTableName(TEST_OWNER, TEST_CERT_DIGEST),
                mContext.getFilesDir()));
        assertFalse(testDir.exists());
        assertFalse(localTestDir.exists());
        assertEquals(2, dir.listFiles().length);

        List<Map.Entry<String, String>> vendors = OnDevicePersonalizationVendorDataDao.getVendors(
                mContext);
        assertEquals(1, vendors.size());
        assertEquals(new AbstractMap.SimpleEntry<>(mContext.getPackageName(),
                PackageUtils.getCertDigest(mContext, mContext.getPackageName())), vendors.get(0));

        addTestData(timestamp + 10, mDao);
        addTestData(timestamp + 20, mDao);
        assertEquals(6, dir.listFiles().length);

        OnDevicePersonalizationMaintenanceJobService.cleanupVendorData(mContext);
        assertEquals(2, dir.listFiles().length);
        assertTrue(new File(dir, "large_" + (timestamp + 20)).exists());
        assertTrue(new File(dir, "large2_" + (timestamp + 20)).exists());

        assertNull(mEventsDao.getEventState(TASK_IDENTIFIER, TEST_OWNER));
        assertNotNull(mEventsDao.getEventState(TASK_IDENTIFIER, mContext.getPackageName()));

        assertEquals(2, mEventsDao.readAllNewRowsForPackage(TEST_OWNER, 0, 0).size());
        assertEquals(2,
                mEventsDao.readAllNewRowsForPackage(mContext.getPackageName(), 0, 0).size());
    }

    @Test
    public void testVendorDataCleanupExtraDirs() throws Exception {
        long timestamp = System.currentTimeMillis();
        addTestData(timestamp, mDao);
        File vendorDir = new File(mContext.getFilesDir(), "VendorData");
        File localDir = new File(mContext.getFilesDir(), "LocalData");

        // Write extra dirs and files
        new File(vendorDir, "randomDirectory").mkdir();
        new File(vendorDir, "randomDirectory2").mkdir();
        new File(localDir, "randomDirectory").mkdir();
        Files.write(new File(vendorDir, "randomFile.txt").toPath(), new byte[10]);
        Files.write(new File(vendorDir + "/randomDirectory", "randomFile.txt").toPath(),
                new byte[10]);
        Files.write(new File(localDir, "randomFile.txt").toPath(), new byte[10]);
        assertEquals(4, vendorDir.listFiles().length);
        assertEquals(3, localDir.listFiles().length);

        OnDevicePersonalizationMaintenanceJobService.cleanupVendorData(mContext);
        assertEquals(1, vendorDir.listFiles().length);
        assertEquals(1, localDir.listFiles().length);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
