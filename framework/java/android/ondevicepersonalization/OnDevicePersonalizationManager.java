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
import android.ondevicepersonalization.aidl.IExecuteCallback;
import android.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.view.SurfaceControlViewHost;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

// TODO(b/289102463): Add a link to the public ODP developer documentation.
/**
 * OnDevicePersonalizationManager provides APIs for apps to load an
 * {@link IsolatedComputationService} in an isolated process and interact with it.
 *
 * An app can request an {@link IsolatedComputationService} to generate content for display
 * within a {@link SurfaceView} within the app's view hierarchy, and also write persistent results
 * to on-device storage which can be consumed by Federated Analytics for cross-device statistical
 * analysis or by Federated Learning for model training. The displayed content and the persistent
 * output are both not directly accessible by the calling app.
 *
 */
public class OnDevicePersonalizationManager {
    static final String ON_DEVICE_PERSONALIZATION_SERVICE =
            "on_device_personalization_service";

    private boolean mBound = false;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OdpManager";

    private IOnDevicePersonalizationManagingService mService;
    private final Context mContext;

    OnDevicePersonalizationManager(Context context) {
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
     * Executes a {@link IsolatedComputationService} in the OnDevicePersonalization sandbox. The
     * platform binds to the specified {@link IsolatedComputationService} in an isolated process
     * and calls {@link IsolatedComputationService#onExecute()} with the caller-provided
     * parameters. When the {@link IsolatedComputationService} finishes execution, the platform
     * returns tokens that refer to the results from the service to the caller. These tokens can
     * be subsequently used to display results in a {@link SurfaceView} within the calling app.
     *
     * @param handler The {@link ComponentName} of the {@link IsolatedComputationService}.
     * @param params a {@link PersistableBundle} that is passed from the calling app to the
     *     {@link IsolatedComputationService}. The expected contents of this parameter are defined
     *     by the{@link IsolatedComputationService}. The platform does not interpret this parameter.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param receiver This returns a list of {@link SurfacePackageToken} objects, each of which is
     *     an opaque reference to a {@link RenderingConfig} returned by an
     *     {@link IsolatedComputationService}, or an {@link Exception} on failure. The returned
     *     {@link SurfacePackageToken} objects can be used in a subsequent
     *     {@link requestSurfacePackage} call to display the result in a view. The calling app and
     *     the {@link IsolatedComputationService} must agree on the expected size of this list.
     *     An entry in the returned list of {@link SurfacePackageToken} objects may be null to
     *     indicate that the service has no output for that specific surface.
     */
    public void execute(
            @NonNull ComponentName handler,
            @NonNull PersistableBundle params,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<List<SurfacePackageToken>, Exception> receiver
    ) {
        try {
            bindService(executor);

            IExecuteCallback callbackWrapper = new IExecuteCallback.Stub() {
                @Override
                public void onSuccess(
                        @NonNull List<String> tokenStrings) {
                    executor.execute(() -> {
                        try {
                            ArrayList<SurfacePackageToken> tokens =
                                    new ArrayList<>(tokenStrings.size());
                            for (String tokenString : tokenStrings) {
                                if (tokenString == null) {
                                    tokens.add(null);
                                } else {
                                    tokens.add(new SurfacePackageToken(tokenString));
                                }
                            }
                            receiver.onResult(tokens);
                        } catch (Exception e) {
                            receiver.onError(e);
                        }
                    });
                }

                @Override
                public void onError(int errorCode) {
                    executor.execute(() -> receiver.onError(
                            new OnDevicePersonalizationException(errorCode)));
                }
            };

            mService.execute(
                    mContext.getPackageName(), handler, params, callbackWrapper);

        } catch (Exception e) {
            receiver.onError(e);
        }
    }

    /**
     * Requests a {@link SurfacePackage} to be inserted into a {@link SurfaceView} inside the
     * calling app. The surface package will contain a {@link View} with the content from a result
     * of a prior call to {@link #execute()} running in the OnDevicePersonalization sandbox.
     *
     * @param surfacePackageToken a reference to a {@link SurfacePackageToken} returned by a prior
     *     call to {@link execute}.
     * @param hostToken the hostToken of the {@link SurfaceView}, which is returned by
     *     {@link SurfaceView#getHostToken()} after the {@link SurfaceView} has been added to the
     *     view hierarchy.
     * @param displayId the integer ID of the logical display on which to display the
     *     {@link SurfacePackage}, returned by {@code Context.getDisplay().getDisplayId()}.
     * @param width the width of the {@link SurfacePackage} in pixels.
     * @param height the height of the {@link SurfacePackage} in pixels.
     * @param executor the {@link Executor} on which to invoke the callback
     * @param receiver This either returns a {@link SurfacePackage} on success, or {@link
     *     Exception} on failure.
     *
     */
    public void requestSurfacePackage(
            @NonNull SurfacePackageToken surfacePackageToken,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<SurfaceControlViewHost.SurfacePackage, Exception> receiver
    ) {
        try {
            bindService(executor);

            IRequestSurfacePackageCallback callbackWrapper =
                    new IRequestSurfacePackageCallback.Stub() {
                        @Override
                        public void onSuccess(
                                @NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
                            executor.execute(() -> {
                                receiver.onResult(surfacePackage);
                            });
                        }

                        @Override
                        public void onError(int errorCode) {
                            executor.execute(() -> receiver.onError(
                                    new OnDevicePersonalizationException(errorCode)));
                        }
                    };

            mService.requestSurfacePackage(
                    surfacePackageToken.getTokenString(), hostToken, displayId,
                    width, height, callbackWrapper);

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
                sLogger.e(TAG + ": Invalid component for ondevicepersonalization service");
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
            sLogger.e(TAG + ": Failed to find ondevicepersonalization service");
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
        sLogger.e(TAG + ": Didn't find any matching ondevicepersonalization service.");
        return null;
    }
}
