/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_ANY;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler.MDD_NETWORK_STATE_KEY;
import static com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler.MDD_PERIOD_SECONDS_KEY;
import static com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler.MDD_TASK_TAG_KEY;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.CELLULAR_CHARGING_PERIODIC_TASK;
import static com.google.android.libraries.mobiledatadownload.TaskScheduler.CHARGING_PERIODIC_TASK;
import static com.google.android.libraries.mobiledatadownload.TaskScheduler.MAINTENANCE_PERIODIC_TASK;
import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.proto.JobPolicy.NetworkType;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.android.libraries.mobiledatadownload.TaskScheduler.NetworkState;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@MockStatic(FlagsFactory.class)
@MockStatic(OdpJobScheduler.class)
@MockStatic(OdpJobServiceFactory.class)
@SpyStatic(MddTaskScheduler.class)
public final class MddTaskSchedulerTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final long DEFAULT_PERIOD_SECONDS = 5;
    private static final String DEFAULT_MDD_TASK_TAG = MAINTENANCE_PERIODIC_TASK;
    private static final NetworkState DEFAULT_NETWORK_STATE = NetworkState.NETWORK_STATE_ANY;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);

    private MddTaskScheduler mMddTaskScheduler;
    @Mock
    private Flags mMockFlags;
    @Mock
    private OdpJobScheduler mMockOdpJobScheduler;
    @Mock
    private OdpJobServiceFactory mMockOdpJobServiceFactory;
    @Mock
    private JobSchedulingLogger mMockJobSchedulingLogger;

    @Before
    public void setup() throws Exception {
        mMddTaskScheduler = new MddTaskScheduler(mContext);
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockOdpJobScheduler).when(() -> OdpJobScheduler.getInstance(any()));
        doReturn(mMockOdpJobServiceFactory).when(() -> OdpJobServiceFactory.getInstance(any()));
        when(mMockOdpJobServiceFactory.getJobSchedulingLogger())
                .thenReturn(mMockJobSchedulingLogger);
    }

    @After
    public void teardown() {
        mJobScheduler.cancelAll();
        assertWithMessage("Any pending job in JobScheduler")
                .that(mJobScheduler.getAllPendingJobs())
                .isEmpty();
    }

    @Test
    public void testSchedulePeriodicTask_withSpeSchedulingEnabled() {
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(true);

        mMddTaskScheduler.schedulePeriodicTask(
                DEFAULT_MDD_TASK_TAG, DEFAULT_PERIOD_SECONDS, DEFAULT_NETWORK_STATE);

        verify(mMockOdpJobScheduler).schedule(eq(mContext), any());
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(any(), any(), anyLong(), any(), anyBoolean()), never());
    }

    @Test
    public void testSchedulePeriodicTask_withLegacySchedulingEnabled() {
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(false);

        mMddTaskScheduler.schedulePeriodicTask(
                DEFAULT_MDD_TASK_TAG,
                DEFAULT_PERIOD_SECONDS,
                DEFAULT_NETWORK_STATE);

        verify(mMockOdpJobScheduler, never()).schedule(eq(mContext), any());
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(any(), any(), anyLong(), any(), anyBoolean()));
    }

    @Test
    public void testSchedule_withSpeSchedulingEnabled() {
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(true);

        MddTaskScheduler.schedule(
                mContext,
                createMddExtras(
                        DEFAULT_MDD_TASK_TAG,
                        DEFAULT_PERIOD_SECONDS,
                        DEFAULT_NETWORK_STATE));

        verify(mMockOdpJobScheduler).schedule(eq(mContext), any());
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(any(), any(), anyLong(), any(), anyBoolean()), never());
    }

    @Test
    public void testSchedule_withLegacySchedulingEnabled() {
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(false);

        MddTaskScheduler.schedule(
                mContext,
                createMddExtras(
                        DEFAULT_MDD_TASK_TAG,
                        DEFAULT_PERIOD_SECONDS,
                        DEFAULT_NETWORK_STATE));

        verify(mMockOdpJobScheduler, never()).schedule(eq(mContext), any());
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(any(), any(), anyLong(), any(), anyBoolean()));
    }

    @Test
    public void testScheduleWithLegacy_scheduledSuccessful() {
        int actualResultCode = MddTaskScheduler
                .scheduleWithLegacy(mContext, createDefaultMddExtras(), /* forceSchedule */ false);

        verify(mMockOdpJobScheduler, never()).schedule(eq(mContext), any());
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(any(), any(), anyLong(), any(), anyBoolean()));
        assertWithMessage("Scheduling failed")
                .that(actualResultCode)
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testScheduleWithLegacy_alreadyScheduled_skippedScheduling() {
        PersistableBundle extras = createDefaultMddExtras();

        MddTaskScheduler.scheduleWithLegacy(mContext, extras, /* forceSchedule */ false);
        int actualResultCode = MddTaskScheduler
                .scheduleWithLegacy(mContext, extras, /* forceSchedule */ false);

        assertWithMessage(
                "Scheduling not skipped")
                .that(actualResultCode)
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);
    }

    @Test
    public void testScheduleWithLegacy_alreadyScheduled_forcedScheduledSuccessful() {
        PersistableBundle extras = createDefaultMddExtras();

        MddTaskScheduler.scheduleWithLegacy(mContext, extras, /* forceSchedule */ false);
        int actualResultCode = MddTaskScheduler
                .scheduleWithLegacy(mContext, extras, /* forceSchedule */ true);

        assertWithMessage(
                "Scheduling failed")
                .that(actualResultCode)
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testScheduleWithLegacy_alreadyScheduled_changedPeriodSchedulingSuccessful() {
        PersistableBundle extras = createMddExtras(
                DEFAULT_MDD_TASK_TAG, DEFAULT_PERIOD_SECONDS, DEFAULT_NETWORK_STATE);
        MddTaskScheduler.scheduleWithLegacy(mContext, extras, /* forceSchedule */ false);

        PersistableBundle extrasPeriodUpdated = createMddExtras(
                DEFAULT_MDD_TASK_TAG, DEFAULT_PERIOD_SECONDS + 1, DEFAULT_NETWORK_STATE);
        int actualResultCode = MddTaskScheduler
                .scheduleWithLegacy(mContext, extrasPeriodUpdated, /* forceSchedule */ false);

        assertWithMessage(
                "Scheduling failed")
                .that(actualResultCode)
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testCreateJobSpec_maintenancePeriodicJob() {
        JobPolicy jobPolicy =
                createJobPolicy(MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID, NETWORK_TYPE_UNMETERED);
        JobSpec expectedJobSpec = new JobSpec.Builder(jobPolicy).setExtras(createMddExtras(
                MAINTENANCE_PERIODIC_TASK,
                DEFAULT_PERIOD_SECONDS,
                NetworkState.NETWORK_STATE_UNMETERED))
                .build();

        assertWithMessage("testCreateJobSpec() for MddJob#maintenancePeriodic")
                .that(MddTaskScheduler.createJobSpec(
                        MAINTENANCE_PERIODIC_TASK,
                        DEFAULT_PERIOD_SECONDS,
                        NetworkState.NETWORK_STATE_UNMETERED))
                .isEqualTo(expectedJobSpec);
    }

    @Test
    public void testCreateJobSpec_chargingPeriodicJob() {
        JobPolicy jobPolicy =
                createJobPolicy(MDD_CHARGING_PERIODIC_TASK_JOB_ID, NETWORK_TYPE_UNMETERED);
        JobSpec expectedJobSpec = new JobSpec.Builder(jobPolicy).setExtras(createMddExtras(
                CHARGING_PERIODIC_TASK,
                DEFAULT_PERIOD_SECONDS,
                NetworkState.NETWORK_STATE_UNMETERED))
                .build();

        assertWithMessage("testCreateJobSpec() for MddJob#charging")
                .that(MddTaskScheduler.createJobSpec(
                        CHARGING_PERIODIC_TASK,
                        DEFAULT_PERIOD_SECONDS,
                        NetworkState.NETWORK_STATE_UNMETERED))
                .isEqualTo(expectedJobSpec);
    }

    @Test
    public void testCreateJobSpec_cellularChargingPeriodicJob() {
        JobPolicy jobPolicy =
                createJobPolicy(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID, NETWORK_TYPE_ANY);
        JobSpec expectedJobSpec = new JobSpec.Builder(jobPolicy).setExtras(createMddExtras(
                CELLULAR_CHARGING_PERIODIC_TASK,
                DEFAULT_PERIOD_SECONDS,
                NetworkState.NETWORK_STATE_CONNECTED))
                .build();

        assertWithMessage("testCreateJobSpec() for MddJob#cellularChargingPeriodic")
                .that(MddTaskScheduler.createJobSpec(
                        CELLULAR_CHARGING_PERIODIC_TASK,
                        DEFAULT_PERIOD_SECONDS,
                        NetworkState.NETWORK_STATE_CONNECTED))
                .isEqualTo(expectedJobSpec);
    }

    @Test
    public void testCreateJobSpec_wifiPeriodicJob() {
        JobPolicy jobPolicy =
                createJobPolicy(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID, NETWORK_TYPE_ANY);
        JobSpec expectedJobSpec = new JobSpec.Builder(jobPolicy).setExtras(createMddExtras(
                WIFI_CHARGING_PERIODIC_TASK,
                DEFAULT_PERIOD_SECONDS,
                NetworkState.NETWORK_STATE_ANY))
                .build();

        assertWithMessage("testCreateJobSpec() for MddJob#wifiPeriodic")
                .that(MddTaskScheduler.createJobSpec(
                        WIFI_CHARGING_PERIODIC_TASK,
                        DEFAULT_PERIOD_SECONDS,
                        NetworkState.NETWORK_STATE_ANY))
                .isEqualTo(expectedJobSpec);
    }

    private JobPolicy createJobPolicy(int jobId, NetworkType networkType) {
        return JobPolicy.newBuilder()
                .setJobId(jobId)
                .setRequireDeviceIdle(true)
                .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                .setPeriodicJobParams(
                        JobPolicy.PeriodicJobParams.newBuilder()
                                .setPeriodicIntervalMs(DEFAULT_PERIOD_SECONDS * 1000)
                                .build())
                .setNetworkType(networkType)
                .setIsPersisted(true)
                .build();
    }

    private PersistableBundle createMddExtras(
            String mddTaskTag, long periodSeconds, NetworkState networkState) {
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, mddTaskTag);
        extras.putLong(MDD_PERIOD_SECONDS_KEY, periodSeconds);
        extras.putString(MDD_NETWORK_STATE_KEY, networkState.name());
        return extras;
    }

    private PersistableBundle createDefaultMddExtras() {
        return createMddExtras(
                DEFAULT_MDD_TASK_TAG, DEFAULT_PERIOD_SECONDS, DEFAULT_NETWORK_STATE);
    }
}
