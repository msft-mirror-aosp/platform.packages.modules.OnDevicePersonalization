/*
 * Copyright 2024 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.content.Context;
import android.federatedcompute.common.TrainingOptions;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(Parameterized.class)
public class IsolatedServiceExceptionSafetyTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private IIsolatedService mIsolatedService;
    private AbstractServiceBinder<IIsolatedService> mServiceBinder;
    private int mCallbackErrorCode;
    private CountDownLatch mLatch;

    @Parameterized.Parameter(0)
    public String operation;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {RuntimeException.class.getName()},
                    {NullPointerException.class.getName()},
                    {IllegalArgumentException.class.getName()}
                });
    }

    @Before
    public void setUp() throws Exception {
        mServiceBinder = AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                mContext,
                "android.adservices.ondevicepersonalization.IsolatedServiceExceptionSafetyTestImpl",
                mContext.getPackageName(),
                "testIsolatedProcess",
                0,
                IIsolatedService.Stub::asInterface);

        mIsolatedService = mServiceBinder.getService(Runnable::run);
        mLatch = new CountDownLatch(1);
    }

    @After
    public void tearDown() {
        mServiceBinder.unbindFromService();
        mIsolatedService = null;
        mCallbackErrorCode = 0;
    }

    @Test
    public void testOnRequestExceptions() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString("op", operation);
        ExecuteInputParcel input =
                new ExecuteInputParcel.Builder()
                        .setAppPackageName("com.testapp")
                        .setAppParams(appParams)
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new TestFederatedComputeService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mIsolatedService.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        assertTrue(mLatch.await(5000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    class TestServiceCallback extends IIsolatedServiceCallback.Stub {
        @Override
        public void onSuccess(Bundle result) {
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mCallbackErrorCode = errorCode;
            mLatch.countDown();
        }
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {}
    }

    static class TestFederatedComputeService extends IFederatedComputeService.Stub {
        @Override
        public void schedule(TrainingOptions trainingOptions, IFederatedComputeCallback callback) {}

        public void cancel(String populationName, IFederatedComputeCallback callback) {}
    }

    static class TestIsolatedModelService extends IIsolatedModelService.Stub {
        @Override
        public void runInference(Bundle params, IIsolatedModelServiceCallback callback) {}
    }
}
