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

import static com.android.adservices.shared.spe.JobServiceConstants.SKIP_REASON_JOB_NOT_CONFIGURED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

/** Unit tests for {@link OdpJobService}. */
@SpyStatic(FlagsFactory.class)
public final class OdpJobServiceTest {
    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);

    @Spy OdpJobService mSpyOdpJobService;
    @Mock JobServiceLogger mMockLogger;
    @Mock JobParameters mMockParameters;
    @Mock OdpJobServiceFactory mMockJobServiceFactory;
    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        assertWithMessage("The JobScheduler").that(mJobScheduler).isNotNull();

        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // By default, enable SPE.
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(true);

        doReturn(mMockLogger).when(mMockJobServiceFactory).getJobServiceLogger();

        doReturn(mMockJobServiceFactory).when(mSpyOdpJobService).getJobServiceFactory();
        mSpyOdpJobService.onCreate();
    }

    @After
    public void teardown() {
        mJobScheduler.cancelAll();

        assertWithMessage("Any pending job in JobScheduler")
                .that(mJobScheduler.getAllPendingJobs())
                .isEmpty();
    }

    @Test
    public void testOnStartJob_notSkip() {
        // Mock current job to be a not-configured job.
        when(mMockParameters.getJobId()).thenReturn(-1);
        doNothing().when(mMockLogger).recordOnStartJob(anyInt());

        // The Parent class's onStartJob() returns at the beginning due to null idToNameMapping.
        doNothing().when(mSpyOdpJobService).skipAndCancelBackgroundJob(any(), anyInt());

        assertThat(mSpyOdpJobService.onStartJob(mMockParameters)).isFalse();
        verify(mMockLogger).recordOnStartJob(anyInt());
        verify(mSpyOdpJobService)
                .skipAndCancelBackgroundJob(mMockParameters, SKIP_REASON_JOB_NOT_CONFIGURED);
    }

    @Test
    public void testOnStartJob_rescheduleWithLegacyMethod() {
        int jobId = 1;
        // Unreachable latency to prevent the job to execute.
        long minimumLatencyMs1 = 60 * 60 * 1000;
        long minimumLatencyMs2 = minimumLatencyMs1 + 1;

        // Create a job pending to but won't execute.
        when(mMockParameters.getJobId()).thenReturn(jobId);
        JobInfo jobInfo1 =
                new JobInfo.Builder(jobId, new ComponentName(mContext, OdpJobService.class))
                        .setMinimumLatency(minimumLatencyMs1)
                        .build();
        mJobScheduler.schedule(jobInfo1);

        // Mock the rescheduling method to reschedule the same job with a different minimum latency.
        doAnswer(
                        invocation -> {
                            JobInfo jobInfo2 =
                                    new JobInfo.Builder(
                                                    jobId,
                                                    new ComponentName(
                                                            mContext, OdpJobService.class))
                                            .setMinimumLatency(minimumLatencyMs2)
                                            .build();
                            mJobScheduler.schedule(jobInfo2);
                            return null;
                        })
                .when(mMockJobServiceFactory)
                .rescheduleJobWithLegacyMethod(mSpyOdpJobService, jobId);

        // Disable SPE and the job should be rescheduled by the legacy scheduling method.
        doReturn(true).when(mSpyOdpJobService).shouldRescheduleWithLegacyMethod(jobId);

        assertWithMessage("mSpyOdpJobService.onStartJob()")
                .that(mSpyOdpJobService.onStartJob(mMockParameters))
                .isFalse();

        // Verify the job is rescheduled.
        JobInfo actualJobInfo = mJobScheduler.getPendingJob(jobId);
        assertWithMessage("Actual minimum latency")
                .that(actualJobInfo.getMinLatencyMillis())
                .isEqualTo(minimumLatencyMs2);
        verify(mMockLogger, never()).recordOnStartJob(anyInt());
    }

    @Test
    public void testShouldRescheduleWithLegacyMethod_speDisabled() {
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(false);

        assertWithMessage(
                        "shouldRescheduleWithLegacyMethod() for"
                                + " OnDevicePersonalizationMaintenanceJob")
                .that(mSpyOdpJobService.shouldRescheduleWithLegacyMethod(MAINTENANCE_TASK_JOB_ID))
                .isTrue();
    }

    @Test
    public void testShouldRescheduleWithLegacyMethod_speDisabled_notConfiguredJobId() {
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(true);
        int invalidJobId = -1;

        assertWithMessage("shouldRescheduleWithLegacyMethod() for" + " not configured job ID")
                .that(mSpyOdpJobService.shouldRescheduleWithLegacyMethod(invalidJobId))
                .isFalse();
    }

    @Test
    public void testShouldRescheduleWithLegacyMethod_speEnabled() {
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(true);

        assertWithMessage(
                        "shouldRescheduleWithLegacyMethod() for"
                                + " OnDevicePersonalizationMaintenanceJob")
                .that(mSpyOdpJobService.shouldRescheduleWithLegacyMethod(MAINTENANCE_TASK_JOB_ID))
                .isFalse();
    }
}
