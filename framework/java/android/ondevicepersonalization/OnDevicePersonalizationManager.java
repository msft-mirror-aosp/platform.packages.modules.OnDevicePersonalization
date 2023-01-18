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
import android.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.util.Slog;
import android.view.SurfaceControlViewHost;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * OnDevicePersonalizationManager.
 *
 * @hide
 */
public class OnDevicePersonalizationManager {
    public static final String ON_DEVICE_PERSONALIZATION_SERVICE =
            "on_device_personalization_service";

    /**
     * The name of key to be used in the Bundle fields of {@link #requestSurfacePackage()},
     * its value should define the integer width of the {@link SurfacePackage} in pixels.
     */
    public static final String EXTRA_WIDTH_IN_PIXELS =
            "android.ondevicepersonalization.extra.WIDTH_IN_PIXELS";
    /**
     * The name of key to be used in the Bundle fields of {@link #requestSurfacePackage()},
     * its value should define the integer height of the {@link SurfacePackage} in pixels.
     */
    public static final String EXTRA_HEIGHT_IN_PIXELS =
            "android.ondevicepersonalization.extra.HEIGHT_IN_PIXELS";
    /**
     * The name of key to be used in the Bundle fields of {@link #requestSurfacePackage()},
     * its value should define the integer ID of the logical
     * display to display the {@link SurfacePackage}.
     */
    public static final String EXTRA_DISPLAY_ID =
            "android.ondevicepersonalization.extra.DISPLAY_ID";

    /**
     * The name of key to be used in the Bundle fields of {@link #requestSurfacePackage()},
     * its value should present the token returned by {@link
     * android.view.SurfaceView#getHostToken()} once the {@link android.view.SurfaceView}
     * has been added to the view hierarchy. Only a non-null value is accepted to enable
     * ANR reporting.
     */
    public static final String EXTRA_HOST_TOKEN =
            "android.ondevicepersonalization.extra.HOST_TOKEN";

    /**
     * The name of key in the Bundle which is passed to the {@code onResult} function of the {@link
     * OutcomeReceiver} which is field of {@link #requestSurfacePackage()},
     * its value presents the requested {@link SurfacePackage}.
     */
    public static final String EXTRA_SURFACE_PACKAGE =
            "android.ondevicepersonalization.extra.SURFACE_PACKAGE";

    private boolean mBound = false;
    private static final String TAG = "OdpManager";

    private IOnDevicePersonalizationManagingService mService;
    private final Context mContext;

    public OnDevicePersonalizationManager(Context context) {
        mContext = context;
    }

    private final CountDownLatch mConnectionLatch = new CountDownLatch(1);

    private final ServiceConnection mConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mService = IOnDevicePersonalizationManagingService.Stub.asInterface(service);
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

    private static final int BIND_SERVICE_TIMEOUT_SEC = 5;
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
     * Requests a surface package from an {@link Exchange} running in the OnDevicePersonalization
     * sandbox.
     *
     * @param exchangePackageName name of the {@link Exchange} that will handle the app request.
     * @param params the parameters from the client application, it must
     *     contain the following params: (EXTRA_WIDTH_IN_PIXELS, EXTRA_HEIGHT_IN_PIXELS,
     *     EXTRA_DISPLAY_ID, EXTRA_HOST_TOKEN). If any of these params is missing, an
     *     IllegalArgumentException will be thrown.
     * @param executor the {@link Executor} on which to invoke the callback
     * @param receiver This either returns a {@link Bundle} on success which should contain the key
     *     EXTRA_SURFACE_PACKAGE with value of {@link SurfacePackage} response, or {@link
     *     Exception} on failure.
     * @throws IllegalArgumentException if any of the following params (EXTRA_WIDTH_IN_PIXELS,
     *     EXTRA_HEIGHT_IN_PIXELS, EXTRA_DISPLAY_ID, EXTRA_HOST_TOKEN) are missing from the Bundle
     *     or passed with the wrong value or type.
     *
     * @hide
     */
    public void requestSurfacePackage(
            @NonNull String exchangePackageName,
            @NonNull Bundle params,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Bundle, Exception> receiver
    ) {
        try {
            int width = params.getInt(EXTRA_WIDTH_IN_PIXELS, -1); // -1 means invalid width
            if (width <= 0) {
                throw new IllegalArgumentException(
                        "Field params should have the entry for the key ("
                                + EXTRA_WIDTH_IN_PIXELS
                                + ") with positive integer value");
            }

            int height = params.getInt(EXTRA_HEIGHT_IN_PIXELS, -1); // -1 means invalid height
            if (height <= 0) {
                throw new IllegalArgumentException(
                        "Field params should have the entry for the key ("
                                + EXTRA_HEIGHT_IN_PIXELS
                                + ") with positive integer value");
            }

            int displayId = params.getInt(EXTRA_DISPLAY_ID, -1); // -1 means invalid displayId
            if (displayId < 0) {
                throw new IllegalArgumentException(
                        "Field params should have the entry for the key ("
                                + EXTRA_DISPLAY_ID
                                + ") with integer >= 0");
            }

            IBinder hostToken = params.getBinder(EXTRA_HOST_TOKEN);
            if (hostToken == null) {
                throw new IllegalArgumentException(
                        "Field params should have the entry for the key ("
                                + EXTRA_HOST_TOKEN
                                + ") with not null IBinder value");
            }

            bindService(executor);

            IRequestSurfacePackageCallback callbackWrapper =
                    new IRequestSurfacePackageCallback.Stub() {
                        @Override
                        public void onSuccess(
                                @NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
                            executor.execute(() -> {
                                Bundle result = new Bundle();
                                result.putParcelable(EXTRA_SURFACE_PACKAGE, surfacePackage);
                                receiver.onResult(result);
                            });
                        }

                        @Override
                        public void onError(int errorCode) {
                            executor.execute(() -> receiver.onError(
                                    new OnDevicePersonalizationException(errorCode)));
                        }
                    };

            mService.requestSurfacePackage(
                    mContext.getPackageName(), exchangePackageName, hostToken, displayId,
                    width, height, params, callbackWrapper);

        } catch (InterruptedException
                | NullPointerException
                | RemoteException e) {
            receiver.onError(e);
        }
    }

    /** Bind to the service, if not already bound. */
    private void bindService(@NonNull Executor executor) throws InterruptedException {
        if (!mBound) {
            Intent intent = new Intent("android.OnDevicePersonalizationService");
            ComponentName serviceComponent =
                    resolveService(intent, mContext.getPackageManager());
            if (serviceComponent == null) {
                Slog.e(TAG, "Invalid component for ondevicepersonalization service");
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
