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

package com.android.ondevicepersonalization.services.download;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_NONE;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

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
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

/** Unit tests for {@link OnDevicePersonalizationDownloadProcessingJob}. */
@MockStatic(OdpJobScheduler.class)
@MockStatic(OdpJobServiceFactory.class)
@MockStatic(OnDevicePersonalizationDownloadProcessingJobService.class)
@MockStatic(FlagsFactory.class)
@SpyStatic(AppManifestConfigHelper.class)
public final class OnDevicePersonalizationDownloadProcessingJobTests {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private OnDevicePersonalizationDownloadProcessingJob mSpyOdpDownloadProcessingJob;
    @Mock
    private Flags mMockFlags;
    @Mock
    private ExecutionRuntimeParameters mMockParams;
    @Mock
    private OdpJobScheduler mMockOdpJobScheduler;
    @Mock
    private OdpJobServiceFactory mMockOdpJobServiceFactory;

    @Before
    public void setup() throws Exception {
        mSpyOdpDownloadProcessingJob = new OnDevicePersonalizationDownloadProcessingJob();
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockOdpJobScheduler).when(() -> OdpJobScheduler.getInstance(any()));
        doReturn(mMockOdpJobServiceFactory).when(() -> OdpJobServiceFactory.getInstance(any()));
    }

    @Test
    public void testGetExecutionFuture_success() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mSpyOdpDownloadProcessingJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_success().get()")
                .that(executionFuture.get())
                .isEqualTo(ExecutionResult.SUCCESS);
    }

    @Test
    public void testGetExecutionFuture_invalidPackageName_returnFailure() throws Exception {
        doReturn(ImmutableList.of("invalidPackageName_thisWillThrowPackageNameNotFoundException"))
                .when(() ->AppManifestConfigHelper.getOdpPackages(any(), anyBoolean()));

        ListenableFuture<ExecutionResult> executionFuture =
                mSpyOdpDownloadProcessingJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_invalidPackageName_returnFailure().get()")
                .that(executionFuture.get())
                .isEqualTo(ExecutionResult.FAILURE_WITHOUT_RETRY);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnOdpDownloadProcessingJobEnabled()).thenReturn(true);

        assertWithMessage("testGetJobEnablementStatus_enabled()")
                .that(mSpyOdpDownloadProcessingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testGetJobEnablementStatus_globalKillSwitchOff_disabledByKillSwitch() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        when(mMockFlags.getSpeOnOdpDownloadProcessingJobEnabled()).thenReturn(true);

        assertWithMessage(
                "testGetJobEnablementStatus_globalKillSwitchOff_disabledByKillSwitch()")
                .that(mSpyOdpDownloadProcessingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_speOff_disabledByKillSwitch() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnOdpDownloadProcessingJobEnabled()).thenReturn(false);

        assertWithMessage(
                "testGetJobEnablementStatus_speOff_disabledByKillSwitch()")
                .that(mSpyOdpDownloadProcessingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnOdpDownloadProcessingJobEnabled()).thenReturn(true);

        OnDevicePersonalizationDownloadProcessingJob.schedule(sContext);

        verify(mMockOdpJobScheduler).schedule(eq(sContext), any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnOdpDownloadProcessingJobEnabled()).thenReturn(false);

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(mMockOdpJobServiceFactory.getJobSchedulingLogger()).thenReturn(loggerMock);
        doReturn(resultCode).when(() -> OnDevicePersonalizationDownloadProcessingJobService
                .schedule(any(), /* forceSchedule */ eq(false)));

        OnDevicePersonalizationDownloadProcessingJob.schedule(sContext);

        verify(mMockOdpJobScheduler, never()).schedule(eq(sContext), any());
        verify(() -> OnDevicePersonalizationDownloadProcessingJobService
                .schedule(any(), /* forceSchedule */ eq(false)));
        verify(loggerMock).recordOnSchedulingLegacy(DOWNLOAD_PROCESSING_TASK_JOB_ID, resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(DOWNLOAD_PROCESSING_TASK_JOB_ID)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setNetworkType(NETWORK_TYPE_NONE)
                        .setIsPersisted(true)
                        .build();

        assertWithMessage("createDefaultJobSpec() for "
                + "OnDevicePersonalizationDownloadProcessingJob")
                .that(OnDevicePersonalizationDownloadProcessingJob.createDefaultJobSpec())
                .isEqualTo(new JobSpec.Builder(expectedJobPolicy).build());
    }

    @Test
    public void testGetBackoffPolicy() {
        BackoffPolicy expectedBackoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("getBackoffPolicy() for OnDevicePersonalizationDownloadProcessingJob")
                .that(new OnDevicePersonalizationDownloadProcessingJob().getBackoffPolicy())
                .isEqualTo(expectedBackoffPolicy);
    }

    @Test
    public void testGetJobPolicyString() {
        String testPolicyString = "test_string";

        when(mMockFlags.getDownloadProcessingJobPolicy()).thenReturn(testPolicyString);

        assertThat(mSpyOdpDownloadProcessingJob.getJobPolicyString(/* jobId= */ 0))
                .isEqualTo(testPolicyString);
    }
}
