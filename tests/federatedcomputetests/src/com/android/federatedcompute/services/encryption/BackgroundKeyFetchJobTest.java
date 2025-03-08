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

package com.android.federatedcompute.services.encryption;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.federatedcompute.services.common.FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
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
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobScheduler;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.odp.module.common.EventLogger;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;

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
import java.util.concurrent.ExecutionException;

@MockStatic(FederatedComputeJobScheduler.class)
@MockStatic(FederatedComputeJobServiceFactory.class)
@MockStatic(BackgroundKeyFetchJobService.class)
@MockStatic(FlagsFactory.class)
@MockStatic(OdpEncryptionKeyManager.class)
@MockStatic(BackgroundKeyFetchJobEventLogger.class)
public final class BackgroundKeyFetchJobTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private BackgroundKeyFetchJob mBackgroundKeyFetchJob;
    @Mock
    private Flags mMockFlags;
    @Mock
    private ExecutionRuntimeParameters mMockParams;
    @Mock
    private FederatedComputeJobScheduler mMockFederatedComputeJobScheduler;
    @Mock
    private FederatedComputeJobServiceFactory mMockFederatedComputeJobServiceFactory;
    @Mock
    private EventLogger mMockBackgroundKeyFetchJobEventLogger;
    @Mock
    private OdpEncryptionKeyManager mMockOdpEncryptionKeyManager;

    @Before
    public void setup() throws Exception {
        mBackgroundKeyFetchJob = new BackgroundKeyFetchJob(new TestInjector());
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockFederatedComputeJobScheduler)
                .when(() -> FederatedComputeJobScheduler.getInstance(any()));
        doReturn(mMockFederatedComputeJobServiceFactory)
                .when(() -> FederatedComputeJobServiceFactory.getInstance(any()));
        doReturn(FluentFuture.from(Futures.immediateFuture(List.of())))
                .when(mMockOdpEncryptionKeyManager)
                .fetchAndPersistActiveKeys(anyInt(), anyBoolean(), any());
    }

    @Test
    public void testGetExecutionFuture_executionSuccess() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mBackgroundKeyFetchJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_executionSuccess()")
                .that(executionFuture.get())
                .isEqualTo(ExecutionResult.SUCCESS);
    }

    @Test
    public void testGetExecutionFuture_executionFailure() {
        doReturn(FluentFuture.from(Futures.immediateFailedFuture(new IllegalStateException())))
                .when(mMockOdpEncryptionKeyManager)
                .fetchAndPersistActiveKeys(anyInt(), anyBoolean(), any());

        ListenableFuture<ExecutionResult> executionFuture =
                mBackgroundKeyFetchJob.getExecutionFuture(sContext, mMockParams);

        assertThrows(ExecutionException.class, () -> executionFuture.get());
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnBackgroundKeyFetchJobEnabled()).thenReturn(true);
        when(mMockFlags.getEnableBackgroundEncryptionKeyFetch()).thenReturn(true);

        assertWithMessage("testGetJobEnablementStatus_enabled()")
                .that(mBackgroundKeyFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testGetJobEnablementStatus_disabledByGlobalKillSwitch() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        when(mMockFlags.getSpeOnBackgroundKeyFetchJobEnabled()).thenReturn(true);
        when(mMockFlags.getEnableBackgroundEncryptionKeyFetch()).thenReturn(true);

        assertWithMessage("testGetJobEnablementStatus_disabledByGlobalKillSwitch()")
                .that(mBackgroundKeyFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_disabledBySpeOff() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnBackgroundKeyFetchJobEnabled()).thenReturn(false);
        when(mMockFlags.getEnableBackgroundEncryptionKeyFetch()).thenReturn(true);

        assertWithMessage("testGetJobEnablementStatus_disabledBySpeOff()")
                .that(mBackgroundKeyFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_disabledByBackgroundEncryptionFetchFlag() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnBackgroundKeyFetchJobEnabled()).thenReturn(true);
        when(mMockFlags.getEnableBackgroundEncryptionKeyFetch()).thenReturn(false);


        assertWithMessage(
                "testGetJobEnablementStatus_disabledByBackgroundEncryptionFetchFlag()")
                .that(mBackgroundKeyFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnBackgroundKeyFetchJobEnabled()).thenReturn(true);

        BackgroundKeyFetchJob.schedule(sContext);

        verify(mMockFederatedComputeJobScheduler).schedule(eq(sContext), any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnBackgroundKeyFetchJobEnabled()).thenReturn(false);

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(mMockFederatedComputeJobServiceFactory
                .getJobSchedulingLogger()).thenReturn(loggerMock);
        doReturn(resultCode).when(() -> BackgroundKeyFetchJobService
                .scheduleJobIfNeeded(any(), any(), /* forceSchedule */ eq(false)));

        BackgroundKeyFetchJob.schedule(sContext);

        verify(mMockFederatedComputeJobScheduler, never()).schedule(eq(sContext), any());
        verify(() -> BackgroundKeyFetchJobService
                .scheduleJobIfNeeded(any(), any(), /* forceSchedule */ eq(false)));
        verify(loggerMock).recordOnSchedulingLegacy(ENCRYPTION_KEY_FETCH_JOB_ID, resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        long expectedIntervalSeconds = 60L;
        doReturn(expectedIntervalSeconds).when(mMockFlags).getEncryptionKeyFetchPeriodSeconds();
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(ENCRYPTION_KEY_FETCH_JOB_ID)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(expectedIntervalSeconds * 1000)
                                        .build())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireDeviceIdle(true)
                        .setNetworkType(NETWORK_TYPE_UNMETERED)
                        .setIsPersisted(true)
                        .build();

        assertWithMessage("createDefaultJobSpec() for BackgroundKeyFetchJob")
                .that(BackgroundKeyFetchJob.createDefaultJobSpec())
                .isEqualTo(new JobSpec.Builder(expectedJobPolicy).build());
    }

    @Test
    public void testGetBackoffPolicy() {
        BackoffPolicy expectedBackoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("getBackoffPolicy() for BackgroundKeyFetchJob")
                .that(new BackgroundKeyFetchJob().getBackoffPolicy())
                .isEqualTo(expectedBackoffPolicy);
    }

    public class TestInjector extends BackgroundKeyFetchJob.Injector {
        @Override
        ListeningExecutorService getLightWeightExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        OdpEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return mMockOdpEncryptionKeyManager;
        }

        @Override
        EventLogger getEventLogger() {
            return mMockBackgroundKeyFetchJobEventLogger;
        }

        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }
    }
}
