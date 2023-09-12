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

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.common.TrainingOptions;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.PhFlagsTestUtil;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public final class FederatedComputeManagingServiceDelegateTest {
    private FederatedComputeManagingServiceDelegate mFcpService;
    private Context mContext;
    @Mock FederatedComputeJobManager mMockJobManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();

        mContext = ApplicationProvider.getApplicationContext();
        mFcpService = new FederatedComputeManagingServiceDelegate(mContext, new TestInjector());
    }

    @Test
    public void testScheduleMissingPackageName_throwsException() throws Exception {
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();

        assertThrows(
                NullPointerException.class,
                () -> mFcpService.schedule(null, trainingOptions, new FederatedComputeCallback()));
    }

    @Test
    public void testScheduleMissingCallback_throwsException() throws Exception {
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.schedule(mContext.getPackageName(), trainingOptions, null));
    }

    @Test
    public void testSchedule_returnsSuccess() throws Exception {
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        mFcpService.schedule(
                mContext.getPackageName(), trainingOptions, new FederatedComputeCallback());
    }

    @Test
    public void testScheduleEnabledGlobalKillSwitch_throwsException() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            mFcpService.schedule(
                                    mContext.getPackageName(),
                                    trainingOptions,
                                    new FederatedComputeCallback()));
        } finally {
            PhFlagsTestUtil.disableGlobalKillSwitch();
        }
    }

    @Test
    public void testCancelMissingPackageName_throwsException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.cancel(null, "fake-population", new FederatedComputeCallback()));
    }

    @Test
    public void testCancelMissingCallback_throwsException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.cancel(mContext.getPackageName(), "fake-population", null));
    }

    @Test
    public void testCancel_returnsSuccess() throws Exception {
        mFcpService.cancel(
                mContext.getPackageName(), "fake-population", new FederatedComputeCallback());
    }

    @Test
    public void testCancelEnabledGlobalKillSwitch_throwsException() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            mFcpService.cancel(
                                    mContext.getPackageName(),
                                    "fake-population",
                                    new FederatedComputeCallback()));
        } finally {
            PhFlagsTestUtil.disableGlobalKillSwitch();
        }
    }

    static class FederatedComputeCallback extends IFederatedComputeCallback.Stub {
        public boolean mError = false;
        public int mErrorCode = 0;
        private CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSuccess() {
            mLatch.countDown();
        }

        @Override
        public void onFailure(int errorCode) {
            mError = true;
            mErrorCode = errorCode;
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await();
        }
    }

    class TestInjector extends FederatedComputeManagingServiceDelegate.Injector {
        FederatedComputeJobManager getJobManager(Context mContext) {
            return mMockJobManager;
        }
    }
}
