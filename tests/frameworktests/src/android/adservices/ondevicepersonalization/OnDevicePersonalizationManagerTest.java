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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRegisterWebTriggerCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public final class OnDevicePersonalizationManagerTest {
    private static final String TAG = "OnDevicePersonalizationManagerTest";
    private Context mContext = ApplicationProvider.getApplicationContext();
    private TestServiceBinder mTestBinder = new TestServiceBinder(
            IOnDevicePersonalizationManagingService.Stub.asInterface(new TestService()));
    private OnDevicePersonalizationManager mManager =
            new OnDevicePersonalizationManager(mContext, mTestBinder);
    private boolean mCallbackSuccess = false;
    private boolean mCallbackError = false;
    private CountDownLatch mLatch = new CountDownLatch(1);

    @Test
    public void testExecuteSuccess() throws Exception {
        mManager.execute(
                ComponentName.createRelative("com.example.service", ".Example"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<SurfacePackageToken, Exception>() {
                    @Override
                    public void onResult(SurfacePackageToken token) {
                        mCallbackSuccess = true;
                        mLatch.countDown();
                    }
                    @Override
                    public void onError(Exception e) {
                        mCallbackError = true;
                        mLatch.countDown();
                    }
                });
        mLatch.await();
        assertTrue(mCallbackSuccess);
        assertFalse(mCallbackError);
    }

    class TestService extends IOnDevicePersonalizationManagingService.Stub {
        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public void execute(
                String callingPackageName,
                ComponentName handler,
                PersistableBundle params,
                CallerMetadata metadata,
                IExecuteCallback callback) {
            try {
                callback.onSuccess("aaaa");
            } catch (RemoteException e) {
                Log.e(TAG, "callback error", e);
            }
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
        public void registerWebTrigger(
                String destinationUrl,
                String registrationUrl,
                String triggerHeader,
                String appPackageName,
                CallerMetadata metadata,
                IRegisterWebTriggerCallback callback) {
            throw new UnsupportedOperationException();
        }
    }

    class TestServiceBinder
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
