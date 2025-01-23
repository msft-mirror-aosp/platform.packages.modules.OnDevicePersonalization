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

package com.android.ondevicepersonalization.services.reset;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.RESET_DATA_JOB_ID;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
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
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
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

/** Unit tests for {@link ResetDataJob}. */
@MockStatic(OdpJobScheduler.class)
@MockStatic(OdpJobServiceFactory.class)
@MockStatic(ResetDataJobService.class)
@MockStatic(FlagsFactory.class)
public final class ResetDataJobTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final long MILLIS = 1000;

    @Spy private ResetDataJob mSpyResetDataJob;
    @Mock private Flags mMockFlags;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private OdpJobScheduler mMockOdpJobScheduler;
    @Mock private OdpJobServiceFactory mMockOdpJobServiceFactory;

    @Before
    public void setup() throws Exception {
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockOdpJobScheduler).when(() -> OdpJobScheduler.getInstance(any()));
        doReturn(mMockOdpJobServiceFactory).when(() -> OdpJobServiceFactory.getInstance(any()));

        // Mock execution main function to do nothing unless asked.
        doNothing().when(mSpyResetDataJob).deleteMeasurementData();
    }

    @After
    public void teardown() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(sContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testGetExecutionFuture() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mSpyResetDataJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture().get()")
                .that(executionFuture.get())
                .isEqualTo(SUCCESS);
        verify(mSpyResetDataJob).deleteMeasurementData();
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getSpeOnResetDataJobEnabled()).thenReturn(true);

        assertWithMessage("getJobEnablementStatus()")
                .that(mSpyResetDataJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testGetJobEnablementStatus_disabled() {
        when(mMockFlags.getSpeOnResetDataJobEnabled()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus()")
                .that(mSpyResetDataJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnResetDataJobEnabled()).thenReturn(true);

        ResetDataJob.schedule(sContext);

        verify(mMockOdpJobScheduler).schedule(eq(sContext), any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(false);

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(mMockOdpJobServiceFactory.getJobSchedulingLogger()).thenReturn(loggerMock);
        doReturn(resultCode).when(() ->
                ResetDataJobService.schedule(/* forceSchedule */ eq(false)));

        ResetDataJob.schedule(sContext);

        verify(mMockOdpJobScheduler, never()).schedule(eq(sContext), any());
        verify(() -> ResetDataJobService.schedule(/* forceSchedule */ eq(false)));
        verify(loggerMock).recordOnSchedulingLegacy(RESET_DATA_JOB_ID, resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(RESET_DATA_JOB_ID)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setOneOffJobParams(
                                JobPolicy.OneOffJobParams.newBuilder()
                                        .setMinimumLatencyMs(
                                                mMockFlags.getResetDataDelaySeconds() * MILLIS)
                                        .setOverrideDeadlineMs(
                                                mMockFlags.getResetDataDeadlineSeconds() * MILLIS)
                                        .build())
                        .setIsPersisted(true)
                        .build();

        assertWithMessage("createDefaultJobSpec() for ResetDataJob")
                .that(ResetDataJob.createDefaultJobSpec())
                .isEqualTo(new JobSpec.Builder(expectedJobPolicy).build());
    }

    @Test
    public void testGetBackoffPolicy() {
        BackoffPolicy expectedBackoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("getBackoffPolicy() for ResetDataJob")
                .that(new ResetDataJob().getBackoffPolicy())
                .isEqualTo(expectedBackoffPolicy);
    }
}
