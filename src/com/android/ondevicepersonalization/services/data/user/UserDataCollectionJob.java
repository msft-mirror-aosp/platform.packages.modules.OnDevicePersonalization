/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data.user;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_NONE;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID;

import android.content.Context;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/** JobService to collect user data in the background thread. */
public final class UserDataCollectionJob implements JobWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = UserDataCollectionJob.class.getSimpleName();
    // 4-hour interval.
    private static final long PERIOD_SECONDS = 14400;
    private UserDataCollector mUserDataCollector;
    private RawUserData mUserData;

    private final Injector mInjector;

    public UserDataCollectionJob() {
        mInjector = new Injector();
    }

    @VisibleForTesting
    public UserDataCollectionJob(Injector injector) {
        mInjector = injector;
    }

    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }
    }

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        return Futures.submit(() -> {
            startUserDataCollectionJob(context);
            return ExecutionResult.SUCCESS;
        }, mInjector.getExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (mInjector.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, skip execution of UserDataCollectionJob.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (!mInjector.getFlags().getSpeOnUserDataCollectionJobEnabled()) {
            sLogger.d(TAG + ": user data collection is disabled; skipping and cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (UserPrivacyStatus.getInstance().isProtectedAudienceAndMeasurementBothDisabled()) {
            sLogger.d(TAG + ": consent revoked; "
                    + "skipping, cancelling job, and deleting existing user data");
            handlePrivacyControlsRevoked(OnDevicePersonalizationApplication.getAppContext());
            return JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
        }
        return JOB_ENABLED_STATUS_ENABLED;
    }

    @Override
    public BackoffPolicy getBackoffPolicy() {
        return new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();
    }

    @Override
    public String getJobPolicyString(int jobId) {
        return FlagsFactory.getFlags().getUserDataCollectionJobPolicy();
    }

    /** Schedules a unique instance of {@link UserDataCollectionJob}. */
    public static void schedule(Context context) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpeOnUserDataCollectionJobEnabled()) {
            sLogger.d("SPE is not enabled. Schedule the job with UserDataCollectionJobService.");

            int resultCode =
                    UserDataCollectionJobService.schedule(context, /* forceSchedule */ false);
            OdpJobServiceFactory.getInstance(context)
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(USER_DATA_COLLECTION_ID, resultCode);

            return;
        }

        OdpJobScheduler.getInstance(context).schedule(context, createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(USER_DATA_COLLECTION_ID)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setNetworkType(NETWORK_TYPE_NONE)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(1000 * PERIOD_SECONDS))
                        .setIsPersisted(true)
                        .build();
        return new JobSpec.Builder(jobPolicy).build();
    }

    private void startUserDataCollectionJob(Context context) {
        mUserDataCollector = UserDataCollector.getInstance(context);
        mUserData = RawUserData.getInstance();
        mUserDataCollector.updateUserData(mUserData);
    }

    private void handlePrivacyControlsRevoked(Context context) {
        mUserDataCollector = UserDataCollector.getInstance(context);
        mUserData = RawUserData.getInstance();
        mUserDataCollector.clearUserData(mUserData);
        mUserDataCollector.clearMetadata();
    }
}
