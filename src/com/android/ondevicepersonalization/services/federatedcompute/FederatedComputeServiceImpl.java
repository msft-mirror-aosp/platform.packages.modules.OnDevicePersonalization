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

import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.annotation.NonNull;
import android.content.Context;
import android.federatedcompute.FederatedComputeManager;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ScheduleFederatedComputeRequest;
import android.federatedcompute.common.TrainingOptions;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.IOException;
import java.util.Objects;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to schedule federatedCompute jobs.
 */
public class FederatedComputeServiceImpl extends IFederatedComputeService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "FederatedComputeServiceImpl";

    @NonNull
    private final Context mApplicationContext;
    @NonNull
    private final String mServicePackageName;
    @NonNull
    private final Injector mInjector;

    @NonNull
    private final FederatedComputeManager mFederatedComputeManager;

    @VisibleForTesting
    public FederatedComputeServiceImpl(
            @NonNull String servicePackageName,
            @NonNull Context applicationContext,
            @NonNull Injector injector) {
        this.mApplicationContext = Objects.requireNonNull(applicationContext);
        this.mServicePackageName = Objects.requireNonNull(servicePackageName);
        this.mInjector = Objects.requireNonNull(injector);
        this.mFederatedComputeManager = Objects.requireNonNull(
                injector.getFederatedComputeManager(mApplicationContext));
    }

    public FederatedComputeServiceImpl(
            @NonNull String servicePackageName,
            @NonNull Context applicationContext) {
        this(servicePackageName, applicationContext, new Injector());
    }

    @Override
    public void schedule(TrainingOptions trainingOptions,
            IFederatedComputeCallback callback) {
        try {
            // TODO(300696702): Read server address from manifest
            ContextData contextData = new ContextData(mServicePackageName);
            TrainingOptions trainingOptionsWithContext = new TrainingOptions.Builder()
                    .setContextData(ContextData.toByteArray(contextData))
                    .setTrainingInterval(trainingOptions.getTrainingInterval())
                    .setPopulationName(trainingOptions.getPopulationName())
                    .setServerAddress(trainingOptions.getServerAddress())
                    .build();
            ScheduleFederatedComputeRequest request =
                    new ScheduleFederatedComputeRequest.Builder()
                            .setTrainingOptions(trainingOptionsWithContext)
                            .build();
            mFederatedComputeManager.schedule(
                    request,
                    mInjector.getExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Object result) {
                            sendSuccess(callback);
                        }

                        @Override
                        public void onError(Exception e) {
                            sLogger.e(TAG + ": Error while scheduling federatedCompute", e);
                            sendError(callback);
                        }
                    });
        } catch (IOException e) {
            sLogger.e(TAG + ": Error while scheduling federatedCompute", e);
            sendError(callback);
        }
    }

    @Override
    public void cancel(String populationName,
            IFederatedComputeCallback callback) {
        // TODO(300696702): Validate package->population
        mFederatedComputeManager.cancel(
                populationName,
                mInjector.getExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Object result) {
                        sendSuccess(callback);
                    }

                    @Override
                    public void onError(Exception e) {
                        sLogger.e(TAG + ": Error while cancelling federatedCompute", e);
                        sendError(callback);
                    }
                });
    }

    private void sendSuccess(
            @NonNull IFederatedComputeCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    private void sendError(@NonNull IFederatedComputeCallback callback) {
        try {
            callback.onFailure(ClientConstants.STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        FederatedComputeManager getFederatedComputeManager(Context context) {
            return context.getSystemService(FederatedComputeManager.class);
        }
    }
}
