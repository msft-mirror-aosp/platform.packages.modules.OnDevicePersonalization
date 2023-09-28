/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.federatedcompute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.content.Context;
import android.federatedcompute.FederatedComputeManager;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;
import android.os.OutcomeReceiver;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class FederatedComputeServiceImplTest {
    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();
    ArgumentCaptor<OutcomeReceiver<Object, Exception>> mCallbackCapture;
    private TestInjector mInjector = new TestInjector();
    private CountDownLatch mLatch = new CountDownLatch(1);
    private int mErrorCode = 0;
    private boolean mOnSuccessCalled = false;
    private boolean mOnErrorCalled = false;
    private FederatedComputeServiceImpl mServiceImpl;
    private IFederatedComputeService mServiceProxy;
    private FederatedComputeManager mMockManager;

    @Before
    public void setup() throws Exception {
        mInjector = new TestInjector();
        mMockManager = Mockito.mock(FederatedComputeManager.class);
        mCallbackCapture = ArgumentCaptor.forClass(OutcomeReceiver.class);
        doNothing().when(mMockManager).cancel(any(), any(), mCallbackCapture.capture());
        doNothing().when(mMockManager).schedule(any(), any(), mCallbackCapture.capture());

        mServiceImpl = new FederatedComputeServiceImpl(
                mApplicationContext.getPackageName(), mApplicationContext, mInjector);
        mServiceProxy = IFederatedComputeService.Stub.asInterface(mServiceImpl);
    }

    @Test
    public void testSchedule() throws Exception {
        TrainingInterval interval = new TrainingInterval.Builder()
                .setMinimumIntervalMillis(100)
                .setSchedulingMode(1)
                .build();
        TrainingOptions options = new TrainingOptions.Builder()
                .setPopulationName("population")
                .setTrainingInterval(interval)
                .build();
        mServiceProxy.schedule(
                options,
                new TestCallback());
        mCallbackCapture.getValue().onResult(null);
        mLatch.await();
        assertTrue(mOnSuccessCalled);
    }

    @Test
    public void testScheduleErr() throws Exception {
        TrainingInterval interval = new TrainingInterval.Builder()
                .setMinimumIntervalMillis(100)
                .setSchedulingMode(1)
                .build();
        TrainingOptions options = new TrainingOptions.Builder()
                .setPopulationName("population")
                .setTrainingInterval(interval)
                .build();
        mServiceProxy.schedule(
                options,
                new TestCallback());
        mCallbackCapture.getValue().onError(new Exception());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertEquals(ClientConstants.STATUS_INTERNAL_ERROR, mErrorCode);
    }

    @Test
    public void testCancel() throws Exception {
        mServiceProxy.cancel(
                "population",
                new TestCallback());
        mCallbackCapture.getValue().onResult(null);
        mLatch.await();
        assertTrue(mOnSuccessCalled);
    }

    @Test
    public void testCancelErr() throws Exception {
        mServiceProxy.cancel(
                "population",
                new TestCallback());
        mCallbackCapture.getValue().onError(new Exception());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertEquals(ClientConstants.STATUS_INTERNAL_ERROR, mErrorCode);
    }

    class TestCallback extends IFederatedComputeCallback.Stub {
        @Override
        public void onSuccess() {
            mOnSuccessCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onFailure(int i) {
            mErrorCode = i;
            mOnErrorCalled = true;
            mLatch.countDown();
        }
    }

    class TestInjector extends FederatedComputeServiceImpl.Injector {

        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        FederatedComputeManager getFederatedComputeManager(Context context) {
            return mMockManager;
        }
    }
}
