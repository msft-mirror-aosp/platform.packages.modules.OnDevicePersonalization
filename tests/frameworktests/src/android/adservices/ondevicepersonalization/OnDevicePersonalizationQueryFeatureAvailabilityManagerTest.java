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

package android.adservices.ondevicepersonalization;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IIsFeatureEnabledCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class OnDevicePersonalizationQueryFeatureAvailabilityManagerTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationIsFeatureEnabledManagerTest";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final TestServiceBinder mTestBinder = new TestServiceBinder(
                    IOnDevicePersonalizationManagingService.Stub.asInterface(new TestService()));
    private final OnDevicePersonalizationManager mManager =
            new OnDevicePersonalizationManager(mContext, mTestBinder);

    private volatile boolean mLogApiStatsCalled = false;

    @Before
    public void setUp() throws Exception {
        mLogApiStatsCalled = false;
        MockitoAnnotations.initMocks(this);
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + SdkLevel.isAtLeastU());
    }

    @Test
    public void queryFeatureAvailabilitySuccess() throws Exception {
        var receiver = new ResultReceiver<Integer>();

        mManager.queryFeatureAvailability(
                "success", Executors.newSingleThreadExecutor(), receiver);
        assertTrue(receiver.isSuccess());
        assertFalse(receiver.isError());
        assertNotNull(receiver.getResult());
        assertTrue(mLogApiStatsCalled);
        assertThat(receiver.getResult()).isEqualTo(OnDevicePersonalizationManager.FEATURE_DISABLED);
    }

    @Test
    public void queryFeatureAvailabilityException() throws Exception {
        var receiver = new ResultReceiver<Integer>();

        mManager.queryFeatureAvailability(
                "error", Executors.newSingleThreadExecutor(), receiver);
        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof IllegalStateException);
        assertTrue(mLogApiStatsCalled);
    }

    private class TestService extends IOnDevicePersonalizationManagingService.Stub {
        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public void execute(
                String callingPackageName,
                ComponentName handler,
                Bundle wrappedParams,
                CallerMetadata metadata,
                ExecuteOptionsParcel options,
                IExecuteCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void requestSurfacePackage(
                String surfacePackageToken,
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                CallerMetadata metadata,
                IRequestSurfacePackageCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerMeasurementEvent(
                int eventType,
                Bundle params,
                CallerMetadata metadata,
                IRegisterMeasurementEventCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void isFeatureEnabled(
                String featureName,
                CallerMetadata metadata,
                IIsFeatureEnabledCallback callback) throws RemoteException {
            if (featureName.equals("success")) {
                callback.onResult(OnDevicePersonalizationManager.FEATURE_DISABLED,
                        new CalleeMetadata.Builder()
                        .setCallbackInvokeTimeMillis(SystemClock.elapsedRealtime())
                        .build());
            } else if (featureName.equals("error")) {
                throw new IllegalStateException();
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void logApiCallStats(
                String sdkPackageName,
                int apiName,
                long latencyMillis,
                long rpcCallLatencyMillis,
                long rpcReturnLatencyMillis,
                int responseCode) {
            mLogApiStatsCalled = true;
        }
    }
    private static class TestServiceBinder
            extends AbstractServiceBinder<IOnDevicePersonalizationManagingService> {
        private final IOnDevicePersonalizationManagingService mService;

        TestServiceBinder(IOnDevicePersonalizationManagingService service) {
            mService = service;
        }

        @Override
        public IOnDevicePersonalizationManagingService getService(Executor executor) {
            return mService;
        }

        @Override
        public void unbindFromService() {}
    }

}
