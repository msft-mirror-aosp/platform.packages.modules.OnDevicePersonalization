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

package android.ondevicepersonalization;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.ondevicepersonalization.aidl.IInitOnDevicePersonalizationCallback;
import android.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * OnDevicePersonalizationManager.
 *
 * @hide
 */
public class OnDevicePersonalizationManager {
    public static final String ON_DEVICE_PERSONALIZATION_SERVICE =
            "on_device_personalization_service";

    private boolean mBound = false;
    private static final String TAG = "OdpManager";

    private IOnDevicePersonalizationManagingService mService;
    private final Context mContext;

    /**
     * Callback that returns the result of the init() API.
     *
     * @hide
     */
    public interface InitCallback {
        /** Called when init() succeeds. */
        void onSuccess(IBinder token);

        /** Called when init() fails */
        void onError(int errorCode);
    }

    public OnDevicePersonalizationManager(Context context) {
        mContext = context;
    }

    private final ServiceConnection mConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mService = IOnDevicePersonalizationManagingService.Stub.asInterface(service);
                    mBound = true;
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mService = null;
                    mBound = false;
                }
            };

    private static final int BIND_SERVICE_INTERVAL_MS = 1000;
    private static final int BIND_SERVICE_RETRY_TIMES = 3;
    private static final String VERSION = "1.0";

    /**
     * Gets OnDevicePersonalization version.
     * This function is a temporary place holder. It will be removed when new APIs are added.
     *
     * @hide
     */
    public String getVersion() {
        return VERSION;
    }

    /**
     * Initializes the OnDevicePersonalizationManager.
     *
     * @hide
     */
    public void init(
            @NonNull Bundle params,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull InitCallback callback) {
        try {
            bindService();
            if (mBound) {
                IInitOnDevicePersonalizationCallback callbackWrapper =
                        new IInitOnDevicePersonalizationCallback.Stub() {
                            @Override
                            public void onSuccess(IBinder token) {
                                executor.execute(() -> callback.onSuccess(token));
                            }

                            @Override
                            public void onError(int errorCode) {
                                executor.execute(() -> callback.onError(errorCode));
                            }
                        };
                mService.init(params, callbackWrapper);
            }
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    /** Bind to the service, if not already bound. */
    private void bindService() {
        if (!mBound) {
            try {
                Intent intent = new Intent("android.OnDevicePersonalizationService");
                ComponentName serviceComponent =
                        resolveService(intent, mContext.getPackageManager());
                if (serviceComponent == null) {
                    Slog.e(TAG, "Invalid component for ondevicepersonalization service");
                    return;
                }

                intent.setComponent(serviceComponent);
                boolean r = mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
                if (!r) {
                    return;
                }
                int retries = 0;
                while (!mBound && retries++ < BIND_SERVICE_RETRY_TIMES) {
                    Thread.sleep(BIND_SERVICE_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Find the ComponentName of the service, given its intent and package manager.
     *
     * @return ComponentName of the service. Null if the service is not found.
     */
    private @Nullable ComponentName resolveService(
            @NonNull Intent intent, @NonNull PackageManager pm) {
        List<ResolveInfo> services =
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0));
        if (services == null || services.isEmpty()) {
            Slog.e(TAG, "Failed to find ondevicepersonalization service");
            return null;
        }

        for (int i = 0; i < services.size(); i++) {
            ResolveInfo ri = services.get(i);
            ComponentName resolved =
                    new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
            // There should only be one matching service inside the given package.
            // If there's more than one, return the first one found.
            return resolved;
        }
        Slog.e(TAG, "Didn't find any matching ondevicepersonalization service.");
        return null;
    }
}
