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
import com.android.odp.module.common.encryption.OdpEncryptionKey;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RunWith(Parameterized.class)
public class AggregateErrorDataReportingServiceTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
    private boolean mGetGlobalKillSwitch = false;
    private boolean mAggregateErrorReportingEnabled = true;

    @Parameterized.Parameter(0)
    public boolean mAllowUnEncryptedPayload = true;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

    private AggregateErrorDataReportingService mService;

    private final Flags mTestFlags = new TestFlags();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(FlagsFactory.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Mock private AggregatedErrorReportingWorker mMockReportingWorker;

    @Mock private OdpEncryptionKeyManager mMockEncryptionKeyManager;

    @Mock private OdpEncryptionKey mMockEncryptionKey;

    @Before
    public void setup() throws Exception {
        ExtendedMockito.doReturn(mTestFlags).when(FlagsFactory::getFlags);
        MockitoAnnotations.initMocks(this);

        mService = spy(new AggregateErrorDataReportingService(new TestInjector()));
        doNothing().when(mService).jobFinished(any(), anyBoolean());
        FluentFuture<List<OdpEncryptionKey>> fluentFuture =
                FluentFuture.from(Futures.immediateFuture(List.of(mMockEncryptionKey)));
        when(mMockEncryptionKeyManager.fetchAndPersistActiveKeys(
                OdpEncryptionKey.KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true, Optional.empty()))
                .thenReturn(fluentFuture);

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
        OdpEncryptionKey expectedEncryptionKey =
                mAllowUnEncryptedPayload ? null : mMockEncryptionKey;
        when(mMockReportingWorker.reportAggregateErrors(any(), any()))
                .thenReturn(Futures.immediateVoidFuture());
        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mTestFlags));
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));

        // When the job is started.
        boolean result = mService.onStartJob(mock(JobParameters.class));

        // Expect that the worker is called once and the pending job is not cancelled.
        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        verify(mMockReportingWorker, times(1))
                .reportAggregateErrors(mService, expectedEncryptionKey);
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));
    }

    @Test
    public void onStartJob_errorReportingEnabled_futureResolves_callsWorker() {
        // Given that the aggregate error reporting is enabled and the job is
        // scheduled successfully.
        SettableFuture<Void> returnedFuture = SettableFuture.create();
        mGetGlobalKillSwitch = false;
        mAggregateErrorReportingEnabled = true;
        when(mMockReportingWorker.reportAggregateErrors(any(), any())).thenReturn(returnedFuture);
        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mTestFlags));
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));

        // When the job is started.
        boolean result = mService.onStartJob(mock(JobParameters.class));

        // Expect that the worker is called once and the pending job is not cancelled.
        // The job is marked finished only after the settable future resolves.
        assertTrue(result);
        verify(mService, times(0)).jobFinished(any(), eq(false));
        verify(mMockReportingWorker, times(1)).reportAggregateErrors(any(), any());
        assertNotNull(
                mJobScheduler.getPendingJob(
                        OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID));
        // jobFinished called after the future resolves.
        returnedFuture.set(null);
        verify(mService, times(1)).jobFinished(any(), eq(false));
    }

    @Test
    public void onStartJobTestKillSwitchEnabled_jobCancelled() {
        // Given that the aggregate error reporting job service is already scheduled and the global
        // kill switch is enabled (that is ODP is disabled).
        mGetGlobalKillSwitch = true;
        doReturn(mJobScheduler).when(mService).getSystemService(JobScheduler.class);
        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mTestFlags));
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
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mTestFlags));
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
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mTestFlags));
    }

    @Test
    public void scheduleIfNeeded_AggregateErrorReportingEnabled() {
        mAggregateErrorReportingEnabled = true;

        assertEquals(
                JobScheduler.RESULT_SUCCESS,
                AggregateErrorDataReportingService.scheduleIfNeeded(mContext, mTestFlags));
    }

    private class TestInjector extends AggregateErrorDataReportingService.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        Flags getFlags() {
            return mTestFlags;
        }

        @Override
        AggregatedErrorReportingWorker getErrorReportingWorker() {
            return mMockReportingWorker;
        }

        @Override
        OdpEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return mMockEncryptionKeyManager;
        }
    }

    private class TestFlags implements Flags {
        @Override
        public boolean getGlobalKillSwitch() {
            return mGetGlobalKillSwitch;
        }

        @Override
        public boolean getAggregatedErrorReportingEnabled() {
            return mAggregateErrorReportingEnabled;
        }

        @Override
        public boolean getAllowUnencryptedAggregatedErrorReportingPayload() {
            return mAllowUnEncryptedPayload;
        }
    }
}
