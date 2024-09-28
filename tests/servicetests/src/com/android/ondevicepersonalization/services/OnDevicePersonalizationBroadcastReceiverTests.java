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

package com.android.ondevicepersonalization.services;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.odp.module.common.DeviceUtils;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJob;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationBroadcastReceiverTests {
    private static final Intent BOOT_COMPLETED_INTENT = new Intent(Intent.ACTION_BOOT_COMPLETED);
    private final Context mContext = ApplicationProvider.getApplicationContext();

    // Use direct executor to keep all work sequential for the tests
    private final ListeningExecutorService mDirectExecutorService =
            MoreExecutors.newDirectExecutorService();

    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);

    private final OnDevicePersonalizationBroadcastReceiver mReceiverUnderTest =
            new OnDevicePersonalizationBroadcastReceiver(mDirectExecutorService);

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .addStaticMockFixtures(TestableDeviceConfig::new)
                    .spyStatic(DeviceUtils.class)
                    .spyStatic(OnDevicePersonalizationMaintenanceJob.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();

        // By default, disable SPE.
        PhFlagsTestUtil.setSpePilotJobEnabled(false);
        ExtendedMockito.doReturn(true).when(() -> DeviceUtils.isOdpSupported(any()));

        // Cancel any pending maintenance and MDD jobs
        mJobScheduler.cancel(OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID);
        mJobScheduler.cancel(OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID);
        mJobScheduler.cancel(
                OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID);
        mJobScheduler.cancel(OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
        mJobScheduler.cancel(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID);
        mJobScheduler.cancel(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID);
        mJobScheduler.cancel(OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID);
    }

    @Test
    public void testOnReceive() {
        PhFlagsTestUtil.setAggregatedErrorReportingEnabled(true);
        MobileDataDownloadFactory.getMdd(mContext, mDirectExecutorService, mDirectExecutorService);

        mReceiverUnderTest.onReceive(mContext, BOOT_COMPLETED_INTENT);

        verify(() -> OnDevicePersonalizationMaintenanceJob.schedule(mContext));
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        != null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID)
                        != null);
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        != null);
        // MDD tasks
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        != null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        != null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        != null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        != null);
    }

    @Test
    public void testOnReceiveKillSwitchOn() {
        PhFlagsTestUtil.enableGlobalKillSwitch();

        mReceiverUnderTest.onReceive(mContext, BOOT_COMPLETED_INTENT);

        verify(() -> OnDevicePersonalizationMaintenanceJob.schedule(mContext), never());
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID)
                        == null);
        // MDD tasks
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
    }

    @Test
    public void testOnReceiveDeviceNotSupported() {
        ExtendedMockito.doReturn(false).when(() -> DeviceUtils.isOdpSupported(any()));

        mReceiverUnderTest.onReceive(mContext, BOOT_COMPLETED_INTENT);

        verify(() -> OnDevicePersonalizationMaintenanceJob.schedule(mContext), never());
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID)
                        == null);
        // MDD tasks
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
    }

    @Test
    public void testOnReceiveInvalidIntent() {
        mReceiverUnderTest.onReceive(mContext, new Intent(Intent.ACTION_DIAL_EMERGENCY));

        verify(() -> OnDevicePersonalizationMaintenanceJob.schedule(mContext), never());
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID)
                        == null);
        // MDD tasks
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
        assertTrue(
                mJobScheduler.getPendingJob(
                                OnDevicePersonalizationConfig
                                        .MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID)
                        == null);
    }

    @Test
    public void testEnableReceiver() {
        ComponentName componentName =
                new ComponentName(mContext, OnDevicePersonalizationBroadcastReceiver.class);

        assertTrue(OnDevicePersonalizationBroadcastReceiver.enableReceiver(mContext));
        int result = mContext.getPackageManager().getComponentEnabledSetting(componentName);
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, result);
    }
}
