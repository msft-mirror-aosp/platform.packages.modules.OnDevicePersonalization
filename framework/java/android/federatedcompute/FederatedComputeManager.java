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

package android.federatedcompute;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IFederatedComputeService;
import android.federatedcompute.common.ScheduleFederatedComputeRequest;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.federatedcompute.internal.util.LogUtil;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * FederatedCompute Manager.
 *
 * @hide
 */
public final class FederatedComputeManager {
    /**
     * Constant that represents the service name for {@link FederatedComputeManager} to be used in
     * {@link android.ondevicepersonalization.OnDevicePersonalizationFrameworkInitializer
     * #registerServiceWrappers}
     *
     * @hide
     */
    public static final String FEDERATED_COMPUTE_SERVICE = "federated_compute_service";

    private static final String TAG = FederatedComputeManager.class.getSimpleName();
    private static final String FEDERATED_COMPUTATION_SERVICE_INTENT_FILTER_NAME =
            "android.federatedcompute.FederatedComputeService";

    private final Context mContext;

    private final AbstractServiceBinder<IFederatedComputeService> mServiceBinder;

    public FederatedComputeManager(Context context) {
        this.mContext = context;
        this.mServiceBinder =
                AbstractServiceBinder.getServiceBinder(
                        context,
                        FEDERATED_COMPUTATION_SERVICE_INTENT_FILTER_NAME,
                        IFederatedComputeService.Stub::asInterface);
    }

    /**
     * Schedule FederatedCompute task.
     *
     * @hide
     */
    public void schedule(
            @NonNull ScheduleFederatedComputeRequest request,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(request);
        final IFederatedComputeService service = mServiceBinder.getService(executor);
        try {
            IFederatedComputeCallback federatedComputeCallback =
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            LogUtil.d(TAG, ": schedule onSuccess() called");
                            executor.execute(() -> callback.onResult(null));
                            unbindFromService();
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            LogUtil.d(TAG, ": schedule onFailure() called with errorCode %d",
                                    errorCode);
                            executor.execute(
                                    () ->
                                            callback.onError(
                                                    new OnDevicePersonalizationException(
                                                            errorCode)));
                            unbindFromService();
                        }
                    };
            service.schedule(
                    mContext.getPackageName(),
                    request.getTrainingOptions(),
                    federatedComputeCallback);
        } catch (RemoteException e) {
            LogUtil.e(TAG, e, "Remote Exception");
            executor.execute(() -> callback.onError(e));
            unbindFromService();
        }
    }

    /**
     * Cancel FederatedCompute task.
     *
     * @hide
     */
    public void cancel(
            @NonNull String populationName,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(populationName);
        final IFederatedComputeService service = mServiceBinder.getService(executor);
        try {
            IFederatedComputeCallback federatedComputeCallback =
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            LogUtil.d(TAG, ": cancel onSuccess() called");
                            executor.execute(() -> callback.onResult(null));
                            unbindFromService();
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            LogUtil.d(TAG, ": cancel onFailure() called with errorCode %d",
                                    errorCode);
                            executor.execute(
                                    () ->
                                            callback.onError(
                                                    new OnDevicePersonalizationException(
                                                            errorCode)));
                            unbindFromService();
                        }
                    };
            service.cancel(mContext.getPackageName(), populationName, federatedComputeCallback);
        } catch (RemoteException e) {
            LogUtil.e(TAG, e, "Remote Exception");
            executor.execute(() -> callback.onError(e));
            unbindFromService();
        }
    }

    public void unbindFromService() {
        mServiceBinder.unbindFromService();
    }
}
