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

package com.android.ondevicepersonalization.services.maintenance;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJob.PERIOD_MILLIS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.data.DbUtils;
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
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Unit tests for {@link OnDevicePersonalizationMaintenanceJob}. */
@MockStatic(OdpJobScheduler.class)
@MockStatic(OdpJobServiceFactory.class)
@MockStatic(OnDevicePersonalizationMaintenanceJobService.class)
@MockStatic(FlagsFactory.class)
public final class OnDevicePersonalizationMaintenanceJobTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final String TEST_SERVICE_NAME = "com.test.TestPersonalizationService";
    private static final ComponentName TEST_OWNER = new ComponentName("ownerPkg", "ownerCls");
    private static final String TEST_CERT_DIGEST = "certDigest";
    private static final String TASK_IDENTIFIER = "task";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String KEY_LARGE = "large";
    private static final String KEY_LARGE_2 = "large2";
    private static final String VENDOR_DATA = "VendorData";
    private static final String LOCAL_DATA = "LocalData";

    private OnDevicePersonalizationVendorDataDao mTestDao;
    private OnDevicePersonalizationVendorDataDao mDao;
    private ComponentName mService;
    private EventsDao mEventsDao;

    @Spy private OnDevicePersonalizationMaintenanceJob mSpyOnDevicePersonalizationMaintenanceJob;
    @Mock private Flags mMockFlags;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private OdpJobScheduler mMockOdpJobScheduler;
    @Mock private OdpJobServiceFactory mMockOdpJobServiceFactory;

    @Before
    public void setup() throws Exception {
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        when(mMockFlags.isPersonalizationStatusOverrideEnabled()).thenReturn(false);
        doReturn(mMockOdpJobScheduler).when(() -> OdpJobScheduler.getInstance(any()));
        doReturn(mMockOdpJobServiceFactory).when(() -> OdpJobServiceFactory.getInstance(any()));

        // Mock processAsyncRecords() to do nothing unless asked.
        doNothing().when(mSpyOnDevicePersonalizationMaintenanceJob).cleanupVendorData(any());

        cleanUpDirectories();

        mTestDao =
                OnDevicePersonalizationVendorDataDao.getInstanceForTest(
                        sContext, TEST_OWNER, TEST_CERT_DIGEST);
        mService = new ComponentName(sContext.getPackageName(), TEST_SERVICE_NAME);
        mDao =
                OnDevicePersonalizationVendorDataDao.getInstanceForTest(
                        sContext,
                        mService,
                        PackageUtils.getCertDigest(sContext, sContext.getPackageName()));
        mEventsDao = EventsDao.getInstanceForTest(sContext);
    }

    @After
    public void teardown() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(sContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();

        cleanUpDirectories();
    }

    @Test
    public void testGetExecutionFuture() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mSpyOnDevicePersonalizationMaintenanceJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture().get()")
                .that(executionFuture.get())
                .isEqualTo(SUCCESS);
        verify(mSpyOnDevicePersonalizationMaintenanceJob).cleanupVendorData(sContext);
    }

    @Test
    public void testGetJobEnablementStatus_globalKillSwitchOn() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);

        assertWithMessage("getJobEnablementStatus() for global kill switch ON")
                .that(mSpyOnDevicePersonalizationMaintenanceJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus() for global kill switch OFF")
                .that(mSpyOnDevicePersonalizationMaintenanceJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(true);

        OnDevicePersonalizationMaintenanceJob.schedule(sContext);

        verify(mMockOdpJobScheduler).schedule(eq(sContext), any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(false);

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(mMockOdpJobServiceFactory.getJobSchedulingLogger()).thenReturn(loggerMock);
        doReturn(resultCode)
                .when(
                        () ->
                                OnDevicePersonalizationMaintenanceJobService.schedule(
                                        any(), anyBoolean()));

        OnDevicePersonalizationMaintenanceJob.schedule(sContext);

        verify(mMockOdpJobScheduler, never()).schedule(eq(sContext), any());
        verify(
                () ->
                        OnDevicePersonalizationMaintenanceJobService.schedule(
                                Objects.requireNonNull(sContext), /* forceSchedule */ false));
        verify(loggerMock).recordOnSchedulingLegacy(MAINTENANCE_TASK_JOB_ID, resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(MAINTENANCE_TASK_JOB_ID)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(PERIOD_MILLIS)
                                        .build())
                        .setIsPersisted(true)
                        .build();

        BackoffPolicy backoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("createDefaultJobSpec() for OnDevicePersonalizationMaintenanceJob")
                .that(OnDevicePersonalizationMaintenanceJob.createDefaultJobSpec())
                .isEqualTo(
                        new JobSpec.Builder(expectedJobPolicy)
                                .setBackoffPolicy(backoffPolicy)
                                .build());
    }

    @Test
    public void testVendorDataCleanup() throws Exception {
        // Restore cleanupVendorData() from doNothing().
        doCallRealMethod().when(mSpyOnDevicePersonalizationMaintenanceJob).cleanupVendorData(any());

        long timestamp = System.currentTimeMillis();
        addTestData(timestamp, mTestDao);
        addTestData(timestamp, mDao);
        addEventData(mService, timestamp);
        addEventData(mService, 100L);
        addEventData(TEST_OWNER, timestamp);

        when(mMockFlags.getIsolatedServiceAllowList()).thenReturn(sContext.getPackageName());

        mSpyOnDevicePersonalizationMaintenanceJob.cleanupVendorData(sContext);

        File dir =
                new File(
                        OnDevicePersonalizationVendorDataDao.getFileDir(
                                OnDevicePersonalizationVendorDataDao.getTableName(
                                        mService,
                                        PackageUtils.getCertDigest(
                                                sContext, sContext.getPackageName())),
                                sContext.getFilesDir()));
        File testDir =
                new File(
                        OnDevicePersonalizationVendorDataDao.getFileDir(
                                OnDevicePersonalizationVendorDataDao.getTableName(
                                        TEST_OWNER, TEST_CERT_DIGEST),
                                sContext.getFilesDir()));
        File localTestDir =
                new File(
                        OnDevicePersonalizationLocalDataDao.getFileDir(
                                OnDevicePersonalizationLocalDataDao.getTableName(
                                        TEST_OWNER, TEST_CERT_DIGEST),
                                sContext.getFilesDir()));

        assertThat(testDir.exists()).isFalse();
        assertThat(localTestDir.exists()).isFalse();
        assertThat(dir.listFiles()).hasLength(2);

        List<Map.Entry<String, String>> vendors =
                OnDevicePersonalizationVendorDataDao.getVendors(sContext);
        assertThat(vendors).hasSize(1);
        assertThat(
                        new AbstractMap.SimpleEntry<>(
                                DbUtils.toTableValue(mService),
                                PackageUtils.getCertDigest(sContext, sContext.getPackageName())))
                .isEqualTo(vendors.get(0));

        addTestData(timestamp + 10, mDao);
        addTestData(timestamp + 20, mDao);
        assertThat(dir.listFiles()).hasLength(6);

        mSpyOnDevicePersonalizationMaintenanceJob.cleanupVendorData(sContext);

        assertThat(dir.listFiles()).hasLength(2);
        assertThat(new File(dir, KEY_LARGE + "_" + (timestamp + 20)).exists()).isTrue();
        assertThat(new File(dir, KEY_LARGE_2 + "_" + (timestamp + 20)).exists()).isTrue();

        assertThat(mEventsDao.getEventState(TASK_IDENTIFIER, TEST_OWNER)).isNull();
        assertThat(mEventsDao.getEventState(TASK_IDENTIFIER, mService)).isNotNull();

        assertThat(mEventsDao.readAllNewRowsForPackage(TEST_OWNER, 0, 0)).hasSize(2);
        assertThat(mEventsDao.readAllNewRowsForPackage(mService, 0, 0)).hasSize(2);
    }

    @Test
    public void testLocalDataCleanup() throws Exception {
        // Restore cleanupVendorData() from doNothing().
        doCallRealMethod().when(mSpyOnDevicePersonalizationMaintenanceJob).cleanupVendorData(any());
        when(mMockFlags.getIsolatedServiceAllowList()).thenReturn(sContext.getPackageName());

        OnDevicePersonalizationLocalDataDao localDao =
                OnDevicePersonalizationLocalDataDao.getInstanceForTest(
                        sContext, TEST_OWNER, TEST_CERT_DIGEST);
        localDao.createTable();

        File localTestDir =
                new File(
                        OnDevicePersonalizationLocalDataDao.getFileDir(
                                OnDevicePersonalizationLocalDataDao.getTableName(
                                        TEST_OWNER, TEST_CERT_DIGEST),
                                sContext.getFilesDir()));
        assertThat(localTestDir.exists()).isTrue();

        String key = "key";
        localDao.updateOrInsertLocalData(
                new LocalData.Builder().setData(new byte[1]).setKey(key).build());
        assertThat(localDao.readSingleLocalDataRow(key)).isNotNull();

        mSpyOnDevicePersonalizationMaintenanceJob.cleanupVendorData(sContext);

        assertThat(localTestDir.exists()).isFalse();
        assertThat(localDao.readSingleLocalDataRow(key)).isNull();
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testVendorDataCleanupExtraDirs() throws Exception {
        // Restore cleanupVendorData() from doNothing().
        doCallRealMethod().when(mSpyOnDevicePersonalizationMaintenanceJob).cleanupVendorData(any());
        when(mMockFlags.getIsolatedServiceAllowList()).thenReturn(sContext.getPackageName());

        long timestamp = System.currentTimeMillis();
        addTestData(timestamp, mDao);
        File vendorDir = new File(sContext.getFilesDir(), VENDOR_DATA);
        File localDir = new File(sContext.getFilesDir(), LOCAL_DATA);

        // Write extra dirs and files
        String fileName = "randomFile.txt";
        String directoryName1 = "randomDirectory";
        String directoryName2 = "randomDirectory2";
        new File(vendorDir, directoryName1).mkdir();
        new File(vendorDir, directoryName2).mkdir();
        new File(localDir, directoryName1).mkdir();
        Files.write(new File(vendorDir, fileName).toPath(), new byte[10]);
        Files.write(new File(vendorDir + "/" + directoryName1, fileName).toPath(), new byte[10]);
        Files.write(new File(localDir, fileName).toPath(), new byte[10]);
        assertThat(vendorDir.listFiles()).hasLength(4);
        assertThat(localDir.listFiles()).hasLength(3);

        mSpyOnDevicePersonalizationMaintenanceJob.cleanupVendorData(sContext);

        assertThat(vendorDir.listFiles()).hasLength(1);
        assertThat(localDir.listFiles()).hasLength(1);
    }

    private static void addTestData(long timestamp, OnDevicePersonalizationVendorDataDao dao) {
        String key1 = "key1";
        String key2 = "key2";

        // Add vendor data
        List<VendorData> dataList =
                List.of(
                        new VendorData.Builder().setKey(key1).setData(new byte[10]).build(),
                        new VendorData.Builder().setKey(key2).setData(new byte[10]).build(),
                        new VendorData.Builder()
                                .setKey(KEY_LARGE)
                                .setData(new byte[111111])
                                .build(),
                        new VendorData.Builder()
                                .setKey(KEY_LARGE_2)
                                .setData(new byte[111111])
                                .build());

        List<String> retainedKeys = List.of(key1, key2, KEY_LARGE, KEY_LARGE_2);

        assertThat(dao.batchUpdateOrInsertVendorDataTransaction(dataList, retainedKeys, timestamp))
                .isTrue();
    }

    private void addEventData(ComponentName service, long timestamp) {
        Query query =
                new Query.Builder(
                                timestamp,
                                "com.app",
                                service,
                                TEST_CERT_DIGEST,
                                "query".getBytes(StandardCharsets.UTF_8))
                        .build();
        long queryId = mEventsDao.insertQuery(query);

        Event event =
                new Event.Builder()
                        .setType(1)
                        .setEventData("event".getBytes(StandardCharsets.UTF_8))
                        .setService(service)
                        .setQueryId(queryId)
                        .setTimeMillis(timestamp)
                        .setRowIndex(0)
                        .build();
        mEventsDao.insertEvent(event);

        EventState eventState =
                new EventState.Builder()
                        .setTaskIdentifier(TASK_IDENTIFIER)
                        .setService(service)
                        .setToken(new byte[] {1})
                        .build();
        mEventsDao.updateOrInsertEventState(eventState);
    }

    private void cleanUpDirectories() {
        File vendorDir = new File(sContext.getFilesDir(), VENDOR_DATA);
        File localDir = new File(sContext.getFilesDir(), LOCAL_DATA);
        FileUtils.deleteDirectory(vendorDir);
        FileUtils.deleteDirectory(localDir);
    }
}
