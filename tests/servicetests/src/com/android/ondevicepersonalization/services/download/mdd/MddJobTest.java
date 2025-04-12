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

import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.CHARGING_PERIODIC_TASK;
import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJob;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@MockStatic(FlagsFactory.class)
@MockStatic(MddTaskScheduler.class)
@MockStatic(MobileDataDownloadFactory.class)
@MockStatic(OnDevicePersonalizationDownloadProcessingJob.class)
@MockStatic(OdpJobScheduler.class)
@MockStatic(OdpJobServiceFactory.class)
@MockStatic(UserPrivacyStatus.class)
public final class MddJobTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private MddJob mMddJobChargingPeriodic;
    @Mock private Flags mMockFlags;
    @Mock private UserPrivacyStatus mMockUserPrivacyStatus;
    @Mock private MobileDataDownload mMockMobileDataDownload;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private OdpJobScheduler mMockOdpJobScheduler;
    @Mock private OdpJobServiceFactory mMockOdpJobServiceFactory;

    @Before
    public void setup() throws Exception {
        mMddJobChargingPeriodic = new MddJob(CHARGING_PERIODIC_TASK, new TestInjector());
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(mMockOdpJobScheduler).when(() -> OdpJobScheduler.getInstance(any()));
        doReturn(mMockOdpJobServiceFactory).when(() -> OdpJobServiceFactory.getInstance(any()));
        doReturn(mMockMobileDataDownload).when(() -> MobileDataDownloadFactory.getMdd(any()));
        doReturn(immediateVoidFuture()).when(mMockMobileDataDownload).handleTask(any());
    }

    @Test
    public void testGetExecutionFuture_executionSuccess() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mMddJobChargingPeriodic.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_executionSuccess()")
                .that(executionFuture.get())
                .isEqualTo(ExecutionResult.SUCCESS);
        verify(() -> OnDevicePersonalizationDownloadProcessingJob.schedule(any()), never());
    }

    @Test
    public void testGetExecutionFuture_wifiChargingPeriodic_scheduleDownloadJob() throws Exception {
        MddJob mddWifiChargingPeriodicJob = createWifiChargingPeriodicMddJob();
        ListenableFuture<ExecutionResult> executionFuture =
                mddWifiChargingPeriodicJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_wifiChargingPeriodic_scheduleDownloadJob()")
                .that(executionFuture.get())
                .isEqualTo(ExecutionResult.SUCCESS);
        verify(() -> OnDevicePersonalizationDownloadProcessingJob.schedule(any()));
    }

    @Test
    public void testGetExecutionStopFuture_notWifiChargingPeriodic_dontScheduleDownloadJob()
            throws Exception {
        ListenableFuture<Void> executionFuture =
                mMddJobChargingPeriodic.getExecutionStopFuture(sContext, mMockParams);

        assertWithMessage(
                        "testGetExecutionStopFuture_notWifiChargingPeriodic_dontScheduleDownloadJob()")
                .that(executionFuture.get())
                .isNull();
        verify(() -> OnDevicePersonalizationDownloadProcessingJob.schedule(any()), never());
    }

    @Test
    public void testGetExecutionStopFuture_wifiChargingPeriodic_scheduleDownloadJob()
            throws Exception {
        MddJob mddWifiChargingPeriodicJob = createWifiChargingPeriodicMddJob();
        ListenableFuture<Void> executionFuture =
                mddWifiChargingPeriodicJob.getExecutionStopFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionStopFuture_wifiChargingPeriodic_scheduleDownloadJob()")
                .that(executionFuture.get())
                .isNull();
        verify(() -> OnDevicePersonalizationDownloadProcessingJob.schedule(any()));
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(true);
        when(mMockUserPrivacyStatus.isProtectedAudienceAndMeasurementBothDisabled())
                .thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus_enabled()")
                .that(mMddJobChargingPeriodic.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_globalKillSwitch() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(true);
        when(mMockUserPrivacyStatus.isProtectedAudienceAndMeasurementBothDisabled())
                .thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus_disabled_globalKillSwitch()")
                .that(mMddJobChargingPeriodic.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_speOff() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(false);
        when(mMockUserPrivacyStatus.isProtectedAudienceAndMeasurementBothDisabled())
                .thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus_disabled_speOff()")
                .that(mMddJobChargingPeriodic.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_noMeasurementNorProtectedAudienceConsent() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnMddJobEnabled()).thenReturn(true);
        when(mMockUserPrivacyStatus.isProtectedAudienceAndMeasurementBothDisabled())
                .thenReturn(true);

        assertWithMessage(
                        "testGetJobEnablementStatus_disabled_noMeasurementNorProtectedAudienceConsent()")
                .that(mMddJobChargingPeriodic.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED);
    }

    @Test
    public void testGetBackoffPolicy() {
        BackoffPolicy expectedBackoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("getBackoffPolicy() for MddJob")
                .that(mMddJobChargingPeriodic.getBackoffPolicy())
                .isEqualTo(expectedBackoffPolicy);
    }

    @Test
    public void testGetJobPolicyString() {
        String testMddMaintenanceJobPolicyString = "mdd_maintenance_job_policy_string";
        String testMddChargingJobPolicyString = "mdd_charging_job_policy_string";
        String testMddCellularChargingJobPolicyString = "mdd_cellular_charging_job_policy_string";
        String testMddWifiChargingJobPolicyString = "mdd_wifi_charging_job_policy_string";

        when(mMockFlags.getMddMaintenanceJobPolicy()).thenReturn(testMddMaintenanceJobPolicyString);
        when(mMockFlags.getMddChargingJobPolicy()).thenReturn(testMddChargingJobPolicyString);
        when(mMockFlags.getMddCellularChargingJobPolicy())
                .thenReturn(testMddCellularChargingJobPolicyString);
        when(mMockFlags.getMddWifiChargingJobPolicy())
                .thenReturn(testMddWifiChargingJobPolicyString);

        assertThat(mMddJobChargingPeriodic.getJobPolicyString(MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID))
                .isEqualTo(testMddMaintenanceJobPolicyString);
        assertThat(mMddJobChargingPeriodic.getJobPolicyString(MDD_CHARGING_PERIODIC_TASK_JOB_ID))
                .isEqualTo(testMddChargingJobPolicyString);
        assertThat(
                        mMddJobChargingPeriodic.getJobPolicyString(
                                MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID))
                .isEqualTo(testMddCellularChargingJobPolicyString);
        assertThat(
                        mMddJobChargingPeriodic.getJobPolicyString(
                                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID))
                .isEqualTo(testMddWifiChargingJobPolicyString);

        assertThat(mMddJobChargingPeriodic.getJobPolicyString(/* jobId= */ 0)).isNull();
    }

    private MddJob createWifiChargingPeriodicMddJob() {
        return new MddJob(WIFI_CHARGING_PERIODIC_TASK);
    }

    public class TestInjector extends MddJob.Injector {
        @Override
        ListeningExecutorService getBackgroundExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        Flags getFlags() {
            return mMockFlags;
        }
    }
}
