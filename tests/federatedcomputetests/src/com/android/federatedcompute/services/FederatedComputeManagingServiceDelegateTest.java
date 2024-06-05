/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.federatedcompute.services;

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;
import static android.federatedcompute.common.ClientConstants.STATUS_KILL_SWITCH_ENABLED;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.common.TrainingOptions;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.PhFlagsTestUtil;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;
import com.android.federatedcompute.services.statsd.ApiCallStats;
import com.android.federatedcompute.services.statsd.FederatedComputeStatsdLogger;
import com.android.odp.module.common.Clock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public final class FederatedComputeManagingServiceDelegateTest {
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 10_000;

    private static final String CALLING_PACKAGE_NAME = "callingPkg";
    private static final String CALLING_CLASS_NAME = "callingClass";

    public static final ComponentName OWNER_COMPONENT_NAME =
            ComponentName.createRelative(CALLING_PACKAGE_NAME, CALLING_CLASS_NAME);

    private FederatedComputeManagingServiceDelegate mFcpService;
    private Context mContext;
    private final FederatedComputeStatsdLogger mFcStatsdLogger =
            spy(FederatedComputeStatsdLogger.getInstance());

    @Mock FederatedComputeJobManager mMockJobManager;
    @Mock private Clock mClock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();

        mContext = ApplicationProvider.getApplicationContext();
        mFcpService =
                new FederatedComputeManagingServiceDelegate(
                        mContext, new TestInjector(), mFcStatsdLogger, mClock);
        when(mClock.elapsedRealtime()).thenReturn(100L, 200L);
    }

    @Test
    public void testScheduleMissingPackageName_throwsException() {
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();

        assertThrows(
                NullPointerException.class,
                () -> mFcpService.schedule(null, trainingOptions, new FederatedComputeCallback()));
    }

    @Test
    public void testScheduleMissingCallback_throwsException() {
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.schedule(mContext.getPackageName(), trainingOptions, null));
    }

    @Test
    public void testSchedule_returnsSuccess() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any())).thenReturn(STATUS_SUCCESS);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_SUCCESS);
    }

    @Test
    public void testScheduleFailed() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any()))
                .thenReturn(STATUS_INTERNAL_ERROR);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testScheduleThrowsRTE() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any()))
                .thenThrow(RuntimeException.class);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testScheduleThrowsNPE() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any()))
                .thenThrow(NullPointerException.class);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testScheduleClockThrowsRTE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(RuntimeException.class);
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();
        FederatedComputeCallback callback = spy(new FederatedComputeCallback());

        mFcpService.schedule(mContext.getPackageName(), trainingOptions, callback);

        ArgumentCaptor<Integer> argument = ArgumentCaptor.forClass(Integer.class);
        verify(callback).onFailure(argument.capture());
        assertThat(argument.getValue()).isEqualTo(STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testScheduleClockThrowsIAE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(IllegalArgumentException.class);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("fake-population")
                        .setOwnerComponentName(OWNER_COMPONENT_NAME)
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFcpService.schedule(
                                mContext.getPackageName(),
                                trainingOptions,
                                new FederatedComputeCallback()));
    }

    @Test
    public void testScheduleEnabledGlobalKillSwitch_returnsError() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        try {
            TrainingOptions trainingOptions =
                    new TrainingOptions.Builder()
                            .setPopulationName("fake-population")
                            .setOwnerComponentName(OWNER_COMPONENT_NAME)
                            .build();
            invokeScheduleAndVerifyLogging(trainingOptions, STATUS_KILL_SWITCH_ENABLED, 0);
        } finally {
            PhFlagsTestUtil.disableGlobalKillSwitch();
        }
    }

    @Test
    public void testCancelMissingPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.cancel(null, "fake-population", new FederatedComputeCallback()));
    }

    @Test
    public void testCancelMissingCallback_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.cancel(OWNER_COMPONENT_NAME, "fake-population", null));
    }

    @Test
    public void testCancel_returnsSuccess() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), anyString())).thenReturn(STATUS_SUCCESS);

        invokeCancelAndVerifyLogging("fake-population", STATUS_SUCCESS);
    }

    @Test
    public void testCancelFails() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), any())).thenReturn(STATUS_INTERNAL_ERROR);

        invokeCancelAndVerifyLogging("fake-population", STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testCancelEnabledGlobalKillSwitch_returnsError() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        try {
            invokeCancelAndVerifyLogging("fake-population", STATUS_KILL_SWITCH_ENABLED, 0);
        } finally {
            PhFlagsTestUtil.disableGlobalKillSwitch();
        }
    }

    @Test
    public void testCancelThrowsRTE() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), any())).thenThrow(RuntimeException.class);

        invokeCancelAndVerifyLogging("fake-population", STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testCancelThrowsNPE() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), any()))
                .thenThrow(NullPointerException.class);

        invokeCancelAndVerifyLogging("fake-population", STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testCancelClockThrowsRTE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(RuntimeException.class);
        FederatedComputeCallback callback = spy(new FederatedComputeCallback());

        mFcpService.cancel(OWNER_COMPONENT_NAME, "fake-population", callback);

        ArgumentCaptor<Integer> argument = ArgumentCaptor.forClass(Integer.class);
        verify(callback).onFailure(argument.capture());
        assertThat(argument.getValue()).isEqualTo(STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testCancelClockThrowsIAE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(IllegalArgumentException.class);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFcpService.cancel(
                                OWNER_COMPONENT_NAME,
                                "fake-population",
                                new FederatedComputeCallback()));
    }

    private void invokeScheduleAndVerifyLogging(
            TrainingOptions trainingOptions, int expectedResultCode) throws InterruptedException {
        invokeScheduleAndVerifyLogging(trainingOptions, expectedResultCode, 100L);
    }

    private void invokeScheduleAndVerifyLogging(
            TrainingOptions trainingOptions, int expectedResultCode, long latency)
            throws InterruptedException {
        ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);
        final CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                // The method logAPiCallStats is called.
                                invocation.callRealMethod();
                                logOperationCalledLatch.countDown();
                                return null;
                            }
                        })
                .when(mFcStatsdLogger)
                .logApiCallStats(argument.capture());

        var callback = new FederatedComputeCallback();
        mFcpService.schedule(mContext.getPackageName(), trainingOptions, callback);

        callback.mJobFinishCountDown.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        logOperationCalledLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(argument.getValue().getResponseCode()).isEqualTo(expectedResultCode);
        assertThat(argument.getValue().getLatencyMillis()).isEqualTo(latency);
        assertThat(argument.getValue().getApiName())
                .isEqualTo(FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE);
    }

    private void invokeCancelAndVerifyLogging(String populationName, int expectedResultCode)
            throws InterruptedException {
        invokeCancelAndVerifyLogging(populationName, expectedResultCode, 100);
    }

    private void invokeCancelAndVerifyLogging(
            String populationName, int expectedResultCode, long latency)
            throws InterruptedException {

        final CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);
        doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                // The method logAPiCallStats is called.
                                invocation.callRealMethod();
                                logOperationCalledLatch.countDown();
                                return null;
                            }
                        })
                .when(mFcStatsdLogger)
                .logApiCallStats(argument.capture());
        var callback = new FederatedComputeCallback();
        mFcpService.cancel(OWNER_COMPONENT_NAME, populationName, callback);

        callback.mJobFinishCountDown.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        logOperationCalledLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(argument.getValue().getResponseCode()).isEqualTo(expectedResultCode);
        assertThat(argument.getValue().getLatencyMillis()).isEqualTo(latency);
        assertThat(argument.getValue().getApiName())
                .isEqualTo(FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL);
    }

    static class FederatedComputeCallback extends IFederatedComputeCallback.Stub {
        public boolean mError = false;
        public int mErrorCode = 0;
        private final CountDownLatch mJobFinishCountDown = new CountDownLatch(1);

        @Override
        public void onSuccess() {
            mJobFinishCountDown.countDown();
        }

        @Override
        public void onFailure(int errorCode) {
            mError = true;
            mErrorCode = errorCode;
            mJobFinishCountDown.countDown();
        }
    }

    class TestInjector extends FederatedComputeManagingServiceDelegate.Injector {
        FederatedComputeJobManager getJobManager(Context mContext) {
            return mMockJobManager;
        }
    }
}
