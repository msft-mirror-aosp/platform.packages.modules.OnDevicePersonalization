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

package com.android.ondevicepersonalization.services.data.errors;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.List;

/** Unit tests for {@link AggregateErrorDataReportingJob}. */
@MockStatic(OdpJobScheduler.class)
@MockStatic(OdpJobServiceFactory.class)
@MockStatic(AggregateErrorDataReportingService.class)
@MockStatic(FlagsFactory.class)
public class AggregateErrorDataReportingJobTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private AggregateErrorDataReportingJob mSpyAggregateErrorDataReportingJob;
    @Mock
    private Flags mMockFlags;
    @Mock
    private ExecutionRuntimeParameters mMockParams;
    @Mock
    private OdpJobScheduler mMockOdpJobScheduler;
    @Mock
    private OdpJobServiceFactory mMockOdpJobServiceFactory;
    @Mock
    private AggregatedErrorReportingWorker mMockReportingWorker;
    @Mock
    private OdpEncryptionKeyManager mMockEncryptionKeyManager;

    @Before
    public void setup() throws Exception {
        mSpyAggregateErrorDataReportingJob = new AggregateErrorDataReportingJob(new TestInjector());
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockOdpJobScheduler).when(() -> OdpJobScheduler.getInstance(any()));
        doReturn(mMockOdpJobServiceFactory).when(() -> OdpJobServiceFactory.getInstance(any()));
    }

    @Test
    public void testGetExecutionFuture_unencryptedFlow() throws Exception {
        when(mMockFlags.getAllowUnencryptedAggregatedErrorReportingPayload()).thenReturn(true);
        when(mMockReportingWorker.reportAggregateErrors(any(), any()))
                .thenReturn(Futures.immediateVoidFuture());

        ListenableFuture<ExecutionResult> executionFuture =
                mSpyAggregateErrorDataReportingJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_unencryptedFlow().get()")
                .that(executionFuture.get())
                .isEqualTo(SUCCESS);
        verify(mMockEncryptionKeyManager, never())
                .fetchAndPersistActiveKeys(anyInt(), anyBoolean(), any());
        verify(mMockReportingWorker).reportAggregateErrors(any(), any());
    }

    @Test
    public void testGetExecutionFuture_encryptedFlow() throws Exception {
        when(mMockFlags.getAllowUnencryptedAggregatedErrorReportingPayload())
                .thenReturn(false);
        when(mMockReportingWorker.reportAggregateErrors(any(), any()))
                .thenReturn(Futures.immediateVoidFuture());
        when(mMockEncryptionKeyManager.fetchAndPersistActiveKeys(anyInt(), anyBoolean(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(List.of())));

        ListenableFuture<ExecutionResult> executionFuture =
                mSpyAggregateErrorDataReportingJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_encryptedFlow().get()")
                .that(executionFuture.get())
                .isEqualTo(SUCCESS);
        verify(mMockEncryptionKeyManager).fetchAndPersistActiveKeys(anyInt(), anyBoolean(), any());
        verify(mMockReportingWorker).reportAggregateErrors(any(), any());
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getAggregatedErrorReportingEnabled()).thenReturn(true);

        assertWithMessage("testGetJobEnablementStatus_enabled()")
                .that(mSpyAggregateErrorDataReportingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_globalKillSwitch() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        when(mMockFlags.getAggregatedErrorReportingEnabled()).thenReturn(true);

        assertWithMessage("testGetJobEnablementStatus_disabled_globalKillSwitch()")
                .that(mSpyAggregateErrorDataReportingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_featureOff() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getAggregatedErrorReportingEnabled()).thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus_disabled_featureOff()")
                .that(mSpyAggregateErrorDataReportingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnAggregateErrorDataReportingJobEnabled()).thenReturn(true);

        AggregateErrorDataReportingJob.schedule(sContext);

        verify(mMockOdpJobScheduler).schedule(eq(sContext), any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnAggregateErrorDataReportingJobEnabled()).thenReturn(false);

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(mMockOdpJobServiceFactory.getJobSchedulingLogger()).thenReturn(loggerMock);
        doReturn(resultCode).when(() -> AggregateErrorDataReportingService
                .scheduleIfNeeded(any(), /* forceSchedule */ eq(false)));

        AggregateErrorDataReportingJob.schedule(sContext);

        verify(mMockOdpJobScheduler, never()).schedule(eq(sContext), any());
        verify(() -> AggregateErrorDataReportingService
                .scheduleIfNeeded(any(), /* forceSchedule */ eq(false)));
        verify(loggerMock).recordOnSchedulingLegacy(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID,
                resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setNetworkType(NETWORK_TYPE_UNMETERED)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder().setPeriodicIntervalMs(
                                        mMockFlags.getAggregatedErrorReportingIntervalInHours()
                                                * 1000L * 3600L
                                        ).build())
                        .setIsPersisted(true)
                        .build();

        assertWithMessage("createDefaultJobSpec() for AggregateErrorDataReportingJob")
                .that(AggregateErrorDataReportingJob.createDefaultJobSpec())
                .isEqualTo(new JobSpec.Builder(expectedJobPolicy).build());
    }

    @Test
    public void testGetBackoffPolicy() {
        BackoffPolicy expectedBackoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("getBackoffPolicy() for ResetDataJob")
                .that(new AggregateErrorDataReportingJob().getBackoffPolicy())
                .isEqualTo(expectedBackoffPolicy);
    }

    public class TestInjector extends AggregateErrorDataReportingJob.Injector {
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

        @Override
        OdpEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return mMockEncryptionKeyManager;
        }
    }
}
