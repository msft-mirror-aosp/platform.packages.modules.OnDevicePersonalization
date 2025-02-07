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

package com.android.ondevicepersonalization.services.data.user;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_NONE;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID;

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
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@MockStatic(OdpJobScheduler.class)
@MockStatic(OdpJobServiceFactory.class)
@MockStatic(UserDataCollectionJobService.class)
@MockStatic(FlagsFactory.class)
@MockStatic(UserPrivacyStatus.class)
@MockStatic(UserDataCollector.class)
public final class UserDataCollectionJobTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private UserDataCollectionJob mSpyUserDataCollectionJob;
    @Mock
    private Flags mMockFlags;
    @Mock
    private UserPrivacyStatus mMockUserPrivacyStatus;
    @Mock
    private UserDataCollector mMockUserDataCollector;
    @Mock
    private ExecutionRuntimeParameters mMockParams;
    @Mock
    private OdpJobScheduler mMockOdpJobScheduler;
    @Mock
    private OdpJobServiceFactory mMockOdpJobServiceFactory;

    @Before
    public void setup() throws Exception {
        mSpyUserDataCollectionJob = new UserDataCollectionJob(new TestInjector());
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(mMockUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(mMockUserDataCollector).when(() -> UserDataCollector.getInstance(any()));
        doReturn(mMockOdpJobScheduler).when(() -> OdpJobScheduler.getInstance(any()));
        doReturn(mMockOdpJobServiceFactory).when(() -> OdpJobServiceFactory.getInstance(any()));
    }

    @Test
    public void testGetExecutionFuture_executionSuccess() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mSpyUserDataCollectionJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture_executionSuccess()")
                .that(executionFuture.get())
                .isEqualTo(ExecutionResult.SUCCESS);
        verify(mMockUserDataCollector).updateUserData(any());
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnUserDataCollectionJobEnabled()).thenReturn(true);
        when(mMockUserPrivacyStatus
                .isProtectedAudienceAndMeasurementBothDisabled()).thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus_enabled()")
                .that(mSpyUserDataCollectionJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_globalKillSwitch() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        when(mMockFlags.getSpeOnUserDataCollectionJobEnabled()).thenReturn(true);
        when(mMockUserPrivacyStatus
                .isProtectedAudienceAndMeasurementBothDisabled()).thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus_disabled_globalKillSwitch()")
                .that(mSpyUserDataCollectionJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_speOff() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnUserDataCollectionJobEnabled()).thenReturn(false);
        when(mMockUserPrivacyStatus
                .isProtectedAudienceAndMeasurementBothDisabled()).thenReturn(false);

        assertWithMessage("testGetJobEnablementStatus_disabled_speOff()")
                .that(mSpyUserDataCollectionJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_disabled_noMeasurementNorProtectedAudienceConsent() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getAggregatedErrorReportingEnabled()).thenReturn(true);
        when(mMockUserPrivacyStatus
                .isProtectedAudienceAndMeasurementBothDisabled()).thenReturn(true);

        assertWithMessage(
                "testGetJobEnablementStatus_disabled_noMeasurementNorProtectedAudienceConsent()")
                .that(mSpyUserDataCollectionJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnUserDataCollectionJobEnabled()).thenReturn(true);

        UserDataCollectionJob.schedule(sContext);

        verify(mMockOdpJobScheduler).schedule(eq(sContext), any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnUserDataCollectionJobEnabled()).thenReturn(false);

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(mMockOdpJobServiceFactory.getJobSchedulingLogger()).thenReturn(loggerMock);
        doReturn(resultCode).when(() -> UserDataCollectionJobService
                .schedule(any(), /* forceSchedule */ eq(false)));

        UserDataCollectionJob.schedule(sContext);

        verify(mMockOdpJobScheduler, never()).schedule(eq(sContext), any());
        verify(() -> UserDataCollectionJobService
                .schedule(any(), /* forceSchedule */ eq(false)));
        verify(loggerMock).recordOnSchedulingLegacy(USER_DATA_COLLECTION_ID,
                resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        long expectedMillis = 1000L * 60L * 60L * 4L; // 4 hours
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(USER_DATA_COLLECTION_ID)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setNetworkType(NETWORK_TYPE_NONE)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(expectedMillis).build())
                        .setIsPersisted(true)
                        .build();

        assertWithMessage("createDefaultJobSpec() for UserDataCollectionJob")
                .that(UserDataCollectionJob.createDefaultJobSpec())
                .isEqualTo(new JobSpec.Builder(expectedJobPolicy).build());
    }

    @Test
    public void testGetBackoffPolicy() {
        BackoffPolicy expectedBackoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("getBackoffPolicy() for UserDataCollectionJob")
                .that(new UserDataCollectionJob().getBackoffPolicy())
                .isEqualTo(expectedBackoffPolicy);
    }

    public class TestInjector extends UserDataCollectionJob.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }
        @Override
        Flags getFlags() {
            return mMockFlags;
        }
    }
}
