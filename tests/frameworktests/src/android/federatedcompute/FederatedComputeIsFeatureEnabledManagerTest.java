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

package android.federatedcompute;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IFederatedComputeService;
import android.federatedcompute.aidl.IIsFeatureEnabledCallback;
import android.federatedcompute.common.TrainingOptions;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FederatedComputeIsFeatureEnabledManagerTest {
    private static final String TAG = "FederatedComputeIsFeatureEnabledManagerTest";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final TestServiceBinder mTestBinder = new TestServiceBinder(
            IFederatedComputeService.Stub.asInterface(new TestService()));
    private final FederatedComputeManager mManager =
            new FederatedComputeManager(mContext, mTestBinder);

    @Test
    public void testIsFeatureEnabledSuccess() throws Exception {
        var receiver = new ResultReceiver<Integer>();

        mManager.isFeatureEnabled(
                "success", Executors.newSingleThreadExecutor(), receiver);
        assertTrue(receiver.isError());
        assertFalse(receiver.isSuccess());
    }

    @Test
    public void testIsFeatureEnabledException() throws Exception {
        var receiver = new ResultReceiver<Integer>();

        mManager.isFeatureEnabled(
                "error", Executors.newSingleThreadExecutor(), receiver);
        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof IllegalStateException);
    }

    private class TestService extends IFederatedComputeService.Stub {

        @Override
        public void schedule(String s, TrainingOptions trainingOptions,
                IFederatedComputeCallback iFederatedComputeCallback) throws RemoteException {
            throw new UnsupportedOperationException();        }

        @Override
        public void cancel(ComponentName componentName, String s,
                IFederatedComputeCallback iFederatedComputeCallback) throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void isFeatureEnabled(
                String featureName,
                IIsFeatureEnabledCallback callback) throws RemoteException {
            if (featureName.equals("success")) {
                callback.onResult(OnDevicePersonalizationManager.FEATURE_DISABLED);
            } else if (featureName.equals("error")) {
                throw new IllegalStateException();
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private static class TestServiceBinder extends AbstractServiceBinder<IFederatedComputeService> {

        private final IFederatedComputeService mService;

        TestServiceBinder(IFederatedComputeService service) {
            mService = service;
        }
        @Override
        public IFederatedComputeService getService(Executor executor) {
            return mService;
        }

        @Override
        public void unbindFromService() {}
    }
}
