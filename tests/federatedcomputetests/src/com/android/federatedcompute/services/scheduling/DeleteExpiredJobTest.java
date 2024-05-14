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

package com.android.federatedcompute.services.scheduling;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.federatedcompute.services.common.FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_TASK_HISTORY_TTL_MILLIS;
import static com.android.federatedcompute.services.common.Flags.ODP_AUTHORIZATION_TOKEN_DELETION_PERIOD_SECONDs;

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
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.federatedcompute.services.scheduling.DeleteExpiredJob.Injector;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobScheduler;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.odp.module.common.Clock;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.Objects;

/** Unit tests for {@link DeleteExpiredJob}. */
@MockStatic(DeleteExpiredJobService.class)
@MockStatic(FederatedComputeJobScheduler.class)
@MockStatic(FederatedComputeJobServiceFactory.class)
@MockStatic(FlagsFactory.class)
public class DeleteExpiredJobTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private DeleteExpiredJob mDeleteExpiredJob;

    @Mock private Flags mMockFlags;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private FederatedComputeJobScheduler mMockFederatedComputeJobScheduler;
    @Mock private FederatedComputeJobServiceFactory mMockFederatedComputeJobServiceFactory;

    @Mock private ODPAuthorizationTokenDao mMockOdpAuthorizationTokenDao;
    @Mock private FederatedTrainingTaskDao mMockFederatedTrainingTaskDao;
    @Mock private Clock mMockClock;

    @Before
    public void setup() throws Exception {
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockFederatedComputeJobScheduler)
                .when(() -> FederatedComputeJobScheduler.getInstance(any()));
        doReturn(mMockFederatedComputeJobServiceFactory)
                .when(() -> FederatedComputeJobServiceFactory.getInstance(any()));

        mDeleteExpiredJob = new DeleteExpiredJob(new TestInjector());
    }

    @Test
    public void testGetExecutionFuture() throws Exception {
        // Mock the deleteTime logic
        when(mMockFlags.getTaskHistoryTtl()).thenReturn(DEFAULT_TASK_HISTORY_TTL_MILLIS);
        long deleteTime = 1L;
        long currentTime = DEFAULT_TASK_HISTORY_TTL_MILLIS + deleteTime;
        when(mMockClock.currentTimeMillis()).thenReturn(currentTime);

        ListenableFuture<ExecutionResult> executionFuture =
                mDeleteExpiredJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture().get()")
                .that(executionFuture.get())
                .isEqualTo(SUCCESS);

        verify(mMockOdpAuthorizationTokenDao).deleteExpiredAuthorizationTokens();
        verify(mMockFederatedTrainingTaskDao).deleteExpiredTaskHistory(deleteTime);
    }

    @Test
    public void testGetJobEnablementStatus_globalKillSwitchOn() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);

        assertWithMessage("getJobEnablementStatus() for global kill switch ON")
                .that(mDeleteExpiredJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus() for global kill switch OFF")
                .that(mDeleteExpiredJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(true);

        DeleteExpiredJob.schedule(sContext, mMockFlags);

        verify(mMockFederatedComputeJobScheduler).schedule(eq(sContext), any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpePilotJobEnabled()).thenReturn(false);

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(mMockFederatedComputeJobServiceFactory.getJobSchedulingLogger())
                .thenReturn(loggerMock);
        doReturn(resultCode)
                .when(
                        () ->
                                DeleteExpiredJobService.scheduleJobIfNeeded(
                                        any(), any(), anyBoolean()));

        DeleteExpiredJob.schedule(sContext, mMockFlags);

        verify(mMockFederatedComputeJobScheduler, never()).schedule(any(), any());
        verify(
                () ->
                        DeleteExpiredJobService.scheduleJobIfNeeded(
                                Objects.requireNonNull(sContext),
                                mMockFlags, /* forceSchedule */
                                false));
        verify(loggerMock).recordOnSchedulingLegacy(DELETE_EXPIRED_JOB_ID, resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(DELETE_EXPIRED_JOB_ID)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(
                                                ODP_AUTHORIZATION_TOKEN_DELETION_PERIOD_SECONDs
                                                        * 1000)
                                        .build())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireDeviceIdle(true)
                        .setIsPersisted(true)
                        .build();

        assertWithMessage("createDefaultJobSpec() for DeleteExpiredJob")
                .that(DeleteExpiredJob.createDefaultJobSpec())
                .isEqualTo(new JobSpec.Builder(expectedJobPolicy).build());
    }

    private class TestInjector extends Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ODPAuthorizationTokenDao getODPAuthorizationTokenDao(Context context) {
            return mMockOdpAuthorizationTokenDao;
        }

        @Override
        FederatedTrainingTaskDao getTrainingTaskDao(Context context) {
            return mMockFederatedTrainingTaskDao;
        }

        @Override
        Clock getClock() {
            return mMockClock;
        }

        @Override
        Flags getFlags() {
            return mMockFlags;
        }
    }
}
