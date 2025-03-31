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

package com.android.federatedcompute.services;

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;
import static android.federatedcompute.common.ClientConstants.STATUS_KILL_SWITCH_ENABLED;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE;

import android.adservices.ondevicepersonalization.Constants;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IFederatedComputeService;
import android.federatedcompute.aidl.IIsFeatureEnabledCallback;
import android.federatedcompute.common.TrainingOptions;
import android.os.Binder;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FeatureStatusManager;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;
import com.android.federatedcompute.services.statsd.ApiCallStats;
import com.android.federatedcompute.services.statsd.FederatedComputeStatsdLogger;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/** Implementation of {@link IFederatedComputeService}. */
class FederatedComputeManagingServiceDelegate extends IFederatedComputeService.Stub {
    private static final String TAG = "FcpServiceDelegate";
    @NonNull private final Context mContext;
    private final FederatedComputeStatsdLogger mFcStatsdLogger;
    private final Clock mClock;

    @VisibleForTesting
    static class Injector {
        FederatedComputeJobManager getJobManager(Context context) {
            return FederatedComputeJobManager.getInstance(context);
        }
    }

    @NonNull private final Injector mInjector;

    FederatedComputeManagingServiceDelegate(
            @NonNull Context context, FederatedComputeStatsdLogger federatedComputeStatsdLogger) {
        this(context, new Injector(), federatedComputeStatsdLogger, MonotonicClock.getInstance());
    }

    @VisibleForTesting
    FederatedComputeManagingServiceDelegate(
            @NonNull Context context,
            @NonNull Injector injector,
            FederatedComputeStatsdLogger federatedComputeStatsdLogger,
            Clock clock) {
        mContext = Objects.requireNonNull(context);
        mInjector = Objects.requireNonNull(injector);
        mClock = clock;
        mFcStatsdLogger = federatedComputeStatsdLogger;
    }

    @Override
    public void schedule(
            String callingPackageName,
            TrainingOptions trainingOptions,
            IFederatedComputeCallback callback) {
        try {
            Objects.requireNonNull(callingPackageName);
            Objects.requireNonNull(callback);

            String sdkPackageName =
                    trainingOptions.getOwnerComponentName() == null
                            ? ""
                            : trainingOptions.getOwnerComponentName().getPackageName();
            if (isKillSwitchEnabled(
                    sdkPackageName,
                    FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE,
                    callback,
                    mFcStatsdLogger)) {
                return;
            }

            final long startServiceTime = mClock.elapsedRealtime();
            FederatedComputeJobManager jobManager = mInjector.getJobManager(mContext);
            FederatedComputeExecutors.getBackgroundExecutor()
                    .execute(
                            () -> {
                                int resultCode = STATUS_SUCCESS;
                                try {
                                    resultCode =
                                            jobManager.onTrainerStartCalled(
                                                    callingPackageName, trainingOptions);
                                } catch (Exception e) {
                                    resultCode = STATUS_INTERNAL_ERROR;
                                    LogUtil.e(TAG, e, "Got exception for schedule()");
                                } finally {
                                    sendResult(callback, resultCode);
                                    logServiceLatency(
                                            startServiceTime,
                                            FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE,
                                            resultCode,
                                            sdkPackageName,
                                            mFcStatsdLogger,
                                            mClock);
                                }
                            });
        } catch (NullPointerException | IllegalArgumentException ex) {
            LogUtil.e(TAG, ex, "Got exception for schedule()");
            throw ex;
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Got exception for schedule()");
            sendResult(callback, STATUS_INTERNAL_ERROR);
        }
    }

    @Override
    public void cancel(
            ComponentName ownerComponent,
            String populationName,
            IFederatedComputeCallback callback) {
        try {
            Objects.requireNonNull(ownerComponent);
            Objects.requireNonNull(callback);
            Objects.requireNonNull(populationName);

            if (isKillSwitchEnabled(
                    ownerComponent.getPackageName(),
                    FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL,
                    callback,
                    mFcStatsdLogger)) {
                return;
            }

            final long startServiceTime = mClock.elapsedRealtime();
            FederatedComputeJobManager jobManager = mInjector.getJobManager(mContext);
            FederatedComputeExecutors.getBackgroundExecutor()
                    .execute(
                            () -> {
                                int resultCode = STATUS_SUCCESS;
                                try {
                                    resultCode =
                                            jobManager.onTrainerStopCalled(
                                                    ownerComponent, populationName);
                                } catch (Exception e) {
                                    resultCode = STATUS_INTERNAL_ERROR;
                                    LogUtil.e(
                                            TAG,
                                            e,
                                            "Got exception when calling cancel for population: %s, "
                                                    + "owner: %s",
                                            populationName,
                                            ownerComponent.flattenToString());
                                } finally {
                                    sendResult(callback, resultCode);
                                    logServiceLatency(
                                            startServiceTime,
                                            FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL,
                                            resultCode,
                                            ownerComponent.getPackageName(),
                                            mFcStatsdLogger,
                                            mClock);
                                }
                            });
        } catch (NullPointerException | IllegalArgumentException ex) {
            LogUtil.e(TAG, ex, "Got exception for cancel()");
            throw ex;
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Got exception for cancel()");
            sendResult(callback, STATUS_INTERNAL_ERROR);
        }
    }

    /** Helper method that logs service latency for the given api. */
    private static void logServiceLatency(
            long startServiceTime,
            int apiName,
            int resultCode,
            String sdkPackageName,
            FederatedComputeStatsdLogger fcStatsdLogger,
            Clock clock) {
        int serviceLatency = (int) (clock.elapsedRealtime() - startServiceTime);
        fcStatsdLogger.logApiCallStats(
                new ApiCallStats.Builder()
                        .setApiName(apiName)
                        .setLatencyMillis(serviceLatency)
                        .setResponseCode(resultCode)
                        .setSdkPackageName(sdkPackageName)
                        .build());
    }

    /**
     * Helper method that checks if the kill switch is enabled, returns true/false accordingly.
     *
     * <p>Should be called on the calling binder thread.
     */
    private static boolean isKillSwitchEnabled(
            String sdkPackageName,
            int apiName,
            @NonNull IFederatedComputeCallback callback,
            FederatedComputeStatsdLogger fcStatsdLogger) {
        // Use FederatedCompute instead of caller permission to read experiment flags. It requires
        // READ_DEVICE_CONFIG permission.
        long origId = Binder.clearCallingIdentity();
        boolean killSwitchEnabled = false;
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            ApiCallStats.Builder apiCallStatsBuilder =
                    new ApiCallStats.Builder()
                            .setApiName(apiName)
                            .setResponseCode(STATUS_KILL_SWITCH_ENABLED)
                            .setSdkPackageName(sdkPackageName);

            fcStatsdLogger.logApiCallStats(apiCallStatsBuilder.build());
            sendResult(callback, STATUS_KILL_SWITCH_ENABLED);
            killSwitchEnabled = true;
        }
        Binder.restoreCallingIdentity(origId);
        return killSwitchEnabled;
    }

    @Override
    public void isFeatureEnabled(
            String featureName,
            IIsFeatureEnabledCallback callback) {
        if (!FlagsFactory.getFlags().isFeatureEnabledApiEnabled()) {
            throw new IllegalStateException("isFeatureEnabled flag is not enabled.");
        }

        long serviceEntryTimeMillis = SystemClock.elapsedRealtime();

        FeatureStatusManager.getFeatureStatusAndSendResult(featureName,
                serviceEntryTimeMillis,
                callback);

        mFcStatsdLogger.logApiCallStats(
                new ApiCallStats.Builder().setApiName(
                                Constants.API_NAME_IS_FEATURE_ENABLED)
                        .setLatencyMillis((int) (mClock.elapsedRealtime() - serviceEntryTimeMillis))
                        .setResponseCode(STATUS_SUCCESS)
                        .setSdkPackageName("")
                        .build());
    }

    private static void sendResult(@NonNull IFederatedComputeCallback callback, int resultCode) {
        try {
            if (resultCode == STATUS_SUCCESS) {
                callback.onSuccess();
                return;
            }
            callback.onFailure(resultCode);
        } catch (RemoteException e) {
            LogUtil.e(TAG, e, "Callback error");
        }
    }
}
