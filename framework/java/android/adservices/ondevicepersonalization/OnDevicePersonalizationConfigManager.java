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

package android.adservices.ondevicepersonalization;

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.MODIFY_ONDEVICEPERSONALIZATION_STATE;

import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigService;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigServiceCallback;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * OnDevicePersonalizationConfigManager provides system APIs
 * for GMSCore to control ODP's enablement status.
 *
 * @hide
 */
public class OnDevicePersonalizationConfigManager {
    /** @hide */
    public static final String ON_DEVICE_PERSONALIZATION_CONFIG_SERVICE =
            "on_device_personalization_config_service";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationConfigManager";
    private static final String ODP_CONFIG_SERVICE_INTENT =
            "android.OnDevicePersonalizationConfigService";
    private static final int BIND_SERVICE_TIMEOUT_SEC = 5;
    private final Context mContext;
    private final CountDownLatch mConnectionLatch = new CountDownLatch(1);
    private boolean mBound = false;
    private IOnDevicePersonalizationConfigService mService = null;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = IOnDevicePersonalizationConfigService.Stub.asInterface(binder);
            mBound = true;
            mConnectionLatch.countDown();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            mBound = false;
            mConnectionLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    /** @hide */
    public OnDevicePersonalizationConfigManager(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Modify ODP personalization status from privileged APKs.
     * Personalization status should be disabled by default.
     *
     * @param statusEnabled boolean whether personalization should be enabled in ODP.
     *                      False if it is never called to set status.
     * @param executor      the {@link Executor} on which to invoke the callback.
     * @param receiver      This either returns true on success or {@link Exception} on failure.
     * @hide
     */
    @RequiresPermission(MODIFY_ONDEVICEPERSONALIZATION_STATE)
    public void setPersonalizationStatus(boolean statusEnabled,
                                         @NonNull @CallbackExecutor Executor executor,
                                         @NonNull OutcomeReceiver<Boolean, Exception> receiver) {
        try {
            bindService(executor);

            mService.setPersonalizationStatus(statusEnabled,
                    new IOnDevicePersonalizationConfigServiceCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(true));
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            executor.execute(() -> receiver.onError(
                                    new OnDevicePersonalizationException(errorCode)));
                        }
                    });
        } catch (InterruptedException | RemoteException e) {
            receiver.onError(e);
        }
    }

    private void bindService(@NonNull Executor executor) throws InterruptedException {
        if (!mBound) {
            Intent intent = new Intent(ODP_CONFIG_SERVICE_INTENT);
            ComponentName serviceComponent = resolveService(intent);
            if (serviceComponent == null) {
                sLogger.e(TAG + ": Invalid component for ODP config service");
                return;
            }

            intent.setComponent(serviceComponent);
            boolean r = mContext.bindService(
                    intent, Context.BIND_AUTO_CREATE, executor, mConnection);
            if (!r) {
                return;
            }
            mConnectionLatch.await(BIND_SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
    }

    /**
     * Find the ComponentName of the service, given its intent.
     *
     * @return ComponentName of the service. Null if the service is not found.
     */
    @Nullable
    private ComponentName resolveService(@NonNull Intent intent) {
        List<ResolveInfo> services = mContext.getPackageManager().queryIntentServices(intent, 0);
        if (services == null || services.isEmpty()) {
            sLogger.e(TAG + ": Failed to find OnDevicePersonalizationConfigService");
            return null;
        }

        for (int i = 0; i < services.size(); i++) {
            ServiceInfo serviceInfo = services.get(i).serviceInfo;
            if (serviceInfo == null) {
                sLogger.e(TAG + ": Failed to find serviceInfo "
                        + "for OnDevicePersonalizationConfigService.");
                return null;
            }
            // There should only be one matching service inside the given package.
            // If there's more than one, return the first one found.
            return new ComponentName(serviceInfo.packageName, serviceInfo.name);
        }
        sLogger.e(TAG + ": Didn't find any matching OnDevicePersonalizationConfigService.");
        return null;
    }
}
