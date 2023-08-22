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

package com.android.ondevicepersonalization.services.federatedcompute;

import static android.federatedcompute.common.ClientConstants.RESULT_HANDLING_SERVICE_ACTION;
import static android.federatedcompute.common.TrainingInterval.SCHEDULING_MODE_ONE_TIME;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IResultHandlingService;
import android.federatedcompute.common.ExampleConsumption;
import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class OdpResultHandlingServiceTests {
    @Rule public final ServiceTestRule serviceRule = new ServiceTestRule();
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final CountDownLatch mLatch = new CountDownLatch(1);

    private boolean mCallbackOnSuccessCalled = false;
    private boolean mCallbackOnFailureCalled = false;

    @Test
    public void testHandleResult() throws Exception {
        Intent mIntent = new Intent();
        mIntent.setAction(RESULT_HANDLING_SERVICE_ACTION).setPackage(mContext.getPackageName());
        mIntent.setData(
                new Uri.Builder()
                        .scheme("app")
                        .authority(mContext.getPackageName())
                        .path("collection")
                        .build());
        IBinder binder = serviceRule.bindService(mIntent);
        assertNotNull(binder);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName("population")
                        .setTrainingInterval(
                                new TrainingInterval.Builder()
                                        .setSchedulingMode(SCHEDULING_MODE_ONE_TIME)
                                        .build())
                        .build();
        ImmutableList<ExampleConsumption> exampleConsumptions =
                ImmutableList.of(
                        new ExampleConsumption.Builder()
                                .setCollectionName("collection")
                                .setExampleCount(100)
                                .setSelectionCriteria(new byte[] {10, 0, 1})
                                .build());

        ((IResultHandlingService.Stub) binder)
                .handleResult(trainingOptions, true, exampleConsumptions, new TestCallback());
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mCallbackOnSuccessCalled);
        assertFalse(mCallbackOnFailureCalled);
    }

    public class TestCallback implements IFederatedComputeCallback {
        @Override
        public void onSuccess() throws RemoteException {
            mCallbackOnSuccessCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onFailure(int i) throws RemoteException {
            mCallbackOnFailureCalled = true;
            mLatch.countDown();
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }
}
