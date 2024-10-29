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

package com.android.ondevicepersonalization.services.data.errors;

import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID;

import static org.junit.Assert.assertEquals;
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
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class AggregateErrorDataReportingServiceTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
    private boolean mGetGlobalKillSwitch = false;
    private boolean mAggregateErrorReportingEnabled = true;

    private AggregateErrorDataReportingService mService;

    private Flags mMockFlags = new Flags() {
        @Override public boolean getGlobalKillSwitch() {
            return mGetGlobalKillSwitch;
        }
        @Override public boolean getAggregatedErrorReportingEnabled() {
            return mAggregateErrorReportingEnabled;
        }
    };

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(FlagsFactory.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Mock private AggregatedErrorReportingWorker mMockReportingWorker;

    @Before
    public void setup() throws Exception {
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        MockitoAnnotations.initMocks(this);

        mService = spy(new AggregateErrorDataReportingService(new TestInjector()));
        doNothing().when(mService).jobFinished(any(), anyBoolean());

        // Setup tests with the global kill switch is disabled and error reporting enabled.
        if (mJobScheduler != null) {
            // Cleanup any pending jobs
            mJobScheduler.cancel(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID);
        }
    }

    @Test
    public void onStartJob_errorReportingEnabled_callsWorker() {
        // Given that the aggregate error reporting is enabled and the job is
        // scheduled successfully.
        when(mMockReportingWorker.reportAggregateErrors(any()))
                .thenReturn(Futures.immediateVoidFuture());
        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mMockFlags));
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));

        // When the job is started.
        boolean result = mService.onStartJob(mock(JobParameters.class));

        // Expect that the worker is called once and the pending job is not cancelled.
        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        verify(mMockReportingWorker, times(1)).reportAggregateErrors(any());
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));
    }

    @Test
    public void onStartJobTestKillSwitchEnabled_jobCancelled() {
        // Given that the aggregate error reporting job service is already scheduled and the global
        // kill switch is enabled (that is ODP is disabled).
        mGetGlobalKillSwitch = true;
        doReturn(mJobScheduler).when(mService).getSystemService(JobScheduler.class);
        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mMockFlags));
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));

        // When the job is started.
        boolean result = mService.onStartJob(mock(JobParameters.class));

        // Expect that the pending job is cancelled.
        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        assertNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));
    }

    @Test
    public void onStartJobTestAggregateReportingDisabled_jobCancelled() {
        // Given that the aggregate error reporting job service is already scheduled and the error
        // reporting flag has been disabled.
        doReturn(mJobScheduler).when(mService).getSystemService(JobScheduler.class);
        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mMockFlags));
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));

        // When the job is started with error reporting disabled.
        mAggregateErrorReportingEnabled = false;
        boolean result = mService.onStartJob(mock(JobParameters.class));

        // Expect that the job is cancelled and no more pending jobs.
        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        assertNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));
    }

    @Test
    public void onStopJobTest() {
        assertTrue(mService.onStopJob(mock(JobParameters.class)));
    }

    @Test
    public void scheduleIfNeeded_AggregateErrorReportingDisabled() {
        mAggregateErrorReportingEnabled = false;

        assertEquals(
                JobScheduler.RESULT_FAILURE,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mMockFlags));
    }

    @Test
    public void scheduleIfNeeded_AggregateErrorReportingEnabled() {
        mAggregateErrorReportingEnabled = true;

        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mMockFlags));
    }

    private class TestInjector extends AggregateErrorDataReportingService.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        Flags getFlags() {
            return mMockFlags;
        }

        @Override
        AggregatedErrorReportingWorker getErrorReportingWorker() {
            return mMockReportingWorker;
        }
    }
}
