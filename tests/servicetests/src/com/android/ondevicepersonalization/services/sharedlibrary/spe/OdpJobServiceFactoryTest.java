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

package com.android.ondevicepersonalization.services.sharedlibrary.spe;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.RESET_DATA_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.data.errors.AggregateErrorDataReportingJob;
import com.android.ondevicepersonalization.services.data.errors.AggregateErrorDataReportingService;
import com.android.ondevicepersonalization.services.data.user.UserDataCollectionJob;
import com.android.ondevicepersonalization.services.data.user.UserDataCollectionJobService;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJob;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJobService;
import com.android.ondevicepersonalization.services.download.mdd.MddJob;
import com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJob;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJobService;
import com.android.ondevicepersonalization.services.reset.ResetDataJob;
import com.android.ondevicepersonalization.services.reset.ResetDataJobService;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Unit tests for {@link OdpJobServiceFactory}. */
@MockStatic(OnDevicePersonalizationDownloadProcessingJobService.class)
@MockStatic(OnDevicePersonalizationMaintenanceJobService.class)
@MockStatic(AggregateErrorDataReportingService.class)
@MockStatic(ResetDataJobService.class)
@MockStatic(UserDataCollectionJobService.class)
@MockStatic(MddTaskScheduler.class)
public final class OdpJobServiceFactoryTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Rule(order = 1)
    public final Expect expect = Expect.create();

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor sExecutor = Executors.newCachedThreadPool();
    private static final Map<Integer, String> sJobIdToNameMap = Map.of();

    private OdpJobServiceFactory mFactory;

    @Mock private JobServiceLogger mMockJobServiceLogger;

    @Mock private ModuleJobPolicy mMockModuleJobPolicy;

    @Mock private ClientErrorLogger mMockErrorLogger;

    @Mock private Flags mMockFlags;

    @Mock private JobSchedulingLogger mMockJobSchedulingLogger;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mFactory =
                new OdpJobServiceFactory(
                        mMockJobServiceLogger,
                        mMockJobSchedulingLogger,
                        mMockModuleJobPolicy,
                        mMockErrorLogger,
                        sJobIdToNameMap,
                        sExecutor,
                        mMockFlags);
    }

    @Test
    public void testGetJobInstance_notConfiguredJob() {
        int notConfiguredJobId = -1;

        assertThat(mFactory.getJobWorkerInstance(notConfiguredJobId)).isNull();
    }

    @Test
    public void testGetJobInstance_onDevicePersonalizationMaintenanceJob() {
        expect.withMessage("getJobWorkerInstance() for OnDevicePersonalizationMaintenanceJob")
                .that(mFactory.getJobWorkerInstance(MAINTENANCE_TASK_JOB_ID))
                .isInstanceOf(OnDevicePersonalizationMaintenanceJob.class);
    }

    @Test
    public void testGetJobInstance_aggregateErrorDataReportingJob() {
        expect.withMessage("getJobWorkerInstance() for AggregateErrorDataReportingJob")
                .that(mFactory.getJobWorkerInstance(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID))
                .isInstanceOf(AggregateErrorDataReportingJob.class);
    }

    @Test
    public void testGetJobInstance_resetDataJob() {
        expect.withMessage("getJobWorkerInstance() for ResetDataJob")
                .that(mFactory.getJobWorkerInstance(RESET_DATA_JOB_ID))
                .isInstanceOf(ResetDataJob.class);
    }

    @Test
    public void testGetJobInstance_userDataCollectionJob() {
        expect.withMessage("getJobWorkerInstance() for UserDataCollectionJob")
                .that(mFactory.getJobWorkerInstance(USER_DATA_COLLECTION_ID))
                .isInstanceOf(UserDataCollectionJob.class);
    }

    @Test
    public void testGetJobInstance_odpDownloadProcessingJob() {
        expect.withMessage(
                "getJobWorkerInstance() for OnDevicePersonalizationDownloadProcessingJob")
                .that(mFactory.getJobWorkerInstance(DOWNLOAD_PROCESSING_TASK_JOB_ID))
                .isInstanceOf(OnDevicePersonalizationDownloadProcessingJob.class);
    }

    @Test
    public void testGetJobInstance_mddJob_cellularChargingPeriodicJobId() {
        expect.withMessage(
                "getJobWorkerInstance() for MddJob cellular charging periodic")
                .that(mFactory.getJobWorkerInstance(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID))
                .isInstanceOf(MddJob.class);
    }

    @Test
    public void testGetJobInstance_mddJob_chargingPeriodicJobId() {
        expect.withMessage(
                "getJobWorkerInstance() for MddJob charging periodic")
                .that(mFactory.getJobWorkerInstance(MDD_CHARGING_PERIODIC_TASK_JOB_ID))
                .isInstanceOf(MddJob.class);
    }

    @Test
    public void testGetJobInstance_mddJob_maintenancePeriodicJobId() {
        expect.withMessage(
                "getJobWorkerInstance() for MddJob maintenance periodic")
                .that(mFactory.getJobWorkerInstance(MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID))
                .isInstanceOf(MddJob.class);
    }

    @Test
    public void testGetJobInstance_mddJob_wifiChargingPeriodicJobId() {
        expect.withMessage(
                "getJobWorkerInstance() for MddJob wifi charging periodic")
                .that(mFactory.getJobWorkerInstance(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID))
                .isInstanceOf(MddJob.class);
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_notConfiguredJob() {
        int notConfiguredJobId = -1;

        mFactory.rescheduleJobWithLegacyMethod(
                sContext, notConfiguredJobId, /* extras */ null);
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_onDevicePersonalizationMaintenanceJobService() {
        boolean forceSchedule = true;

        mFactory.rescheduleJobWithLegacyMethod(
                sContext, MAINTENANCE_TASK_JOB_ID, /* extras */ null);
        verify(
                () ->
                        OnDevicePersonalizationMaintenanceJobService.schedule(
                                sContext, forceSchedule));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_aggregateErrorDataReportingService() {
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, AGGREGATE_ERROR_DATA_REPORTING_JOB_ID, /* extras */ null);
        verify(() -> AggregateErrorDataReportingService
                .scheduleIfNeeded(sContext, /* forceSchedule */ true));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_resetDataJobService() {
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, RESET_DATA_JOB_ID, /* extras */ null);
        verify(() -> ResetDataJobService.schedule(/* forceSchedule */ true));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_userDataCollectionJobService() {
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, USER_DATA_COLLECTION_ID, /* extras */ null);
        verify(() -> UserDataCollectionJobService.schedule(sContext, /* forceSchedule */ true));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_odpDownloadProcessingJobService() {
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, DOWNLOAD_PROCESSING_TASK_JOB_ID, /* extras */ null);
        verify(() -> OnDevicePersonalizationDownloadProcessingJobService
                .schedule(sContext, /* forceSchedule */ true));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_mddJobService_cellularChargingPeriodicJobId() {
        PersistableBundle extras = createExtras();
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID, extras);
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(sContext, extras, /* forceSchedule */ true));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_mddJobService_chargingPeriodicJobId() {
        PersistableBundle extras = createExtras();
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, MDD_CHARGING_PERIODIC_TASK_JOB_ID, extras);
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(sContext, extras, /* forceSchedule */ true));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_mddJobService_maintenancePeriodicJobId() {
        PersistableBundle extras = createExtras();
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID, extras);
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(sContext, extras, /* forceSchedule */ true));
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_mddJobService_wifiChargingPeriodicJobId() {
        PersistableBundle extras = createExtras();
        mFactory.rescheduleJobWithLegacyMethod(
                sContext, MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID, extras);
        verify(() -> MddTaskScheduler
                .scheduleWithLegacy(sContext, extras, /* forceSchedule */ true));
    }

    @Test
    public void testGetJobIdToNameMap() {
        assertThat(mFactory.getJobIdToNameMap()).isSameInstanceAs(sJobIdToNameMap);
    }

    @Test
    public void testGetJobServiceLogger() {
        assertThat(mFactory.getJobServiceLogger()).isSameInstanceAs(mMockJobServiceLogger);
    }

    @Test
    public void testGetJobSchedulingLogger() {
        assertThat(mFactory.getJobSchedulingLogger()).isSameInstanceAs(mMockJobSchedulingLogger);
    }

    @Test
    public void testGetErrorLogger() {
        assertThat(mFactory.getErrorLogger()).isSameInstanceAs(mMockErrorLogger);
    }

    @Test
    public void testGetExecutor() {
        assertThat(mFactory.getBackgroundExecutor()).isSameInstanceAs(sExecutor);
    }

    @Test
    public void testGetModuleJobPolicy() {
        assertThat(mFactory.getModuleJobPolicy()).isSameInstanceAs(mMockModuleJobPolicy);
    }

    @Test
    public void testGetFlags() {
        assertThat(mFactory.getFlags()).isSameInstanceAs(mMockFlags);
    }

    private PersistableBundle createExtras() {
        return new PersistableBundle();
    }
}
