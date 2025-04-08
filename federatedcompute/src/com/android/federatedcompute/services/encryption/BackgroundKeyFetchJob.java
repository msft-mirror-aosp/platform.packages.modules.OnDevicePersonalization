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

package com.android.federatedcompute.services.encryption;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;

import android.content.Context;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobScheduler;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.EventLogger;
import com.android.odp.module.common.encryption.OdpEncryptionKey;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Optional;

/** Job to fetch and persist active keys from a server and deletes expired keys */
public final class BackgroundKeyFetchJob implements JobWorker {
    private static final String TAG = BackgroundKeyFetchJob.class.getSimpleName();
    private static final int ENCRYPTION_KEY_FETCH_JOB_ID =
            FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID;

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return FederatedComputeExecutors.getBackgroundExecutor();
        }

        ListeningExecutorService getLightWeightExecutor() {
            return FederatedComputeExecutors.getLightweightExecutor();
        }

        OdpEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return FederatedComputeEncryptionKeyManagerUtils.getInstance(context);
        }

        EventLogger getEventLogger() {
            return new BackgroundKeyFetchJobEventLogger();
        }
    }

    private final Injector mInjector;

    public BackgroundKeyFetchJob() {
        this(new Injector());
    }

    @VisibleForTesting
    BackgroundKeyFetchJob(Injector injector) {
        mInjector = injector;
    }

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(Context context,
            ExecutionRuntimeParameters executionRuntimeParameters) {
        return
                FluentFuture.from(Futures.submitAsync(
                        () -> {
                            mInjector.getEventLogger().logEncryptionKeyFetchStartEventKind();
                            return mInjector
                                    .getEncryptionKeyManager(context)
                                    .fetchAndPersistActiveKeys(
                                            OdpEncryptionKey.KEY_TYPE_ENCRYPTION,
                                            /* isScheduledJob= */ true,
                                            Optional.of(mInjector.getEventLogger()));
                        },
                        mInjector.getLightWeightExecutor())
                ).transform(odpEncryptionKeys -> {
                    LogUtil.d(TAG, "BackgroundKeyFetchJob %d is done, fetched %d keys",
                            ENCRYPTION_KEY_FETCH_JOB_ID, odpEncryptionKeys.size());
                    return ExecutionResult.SUCCESS;
                }, mInjector.getExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d(TAG, "GlobalKillSwitch enabled, skip execution of BackgroundKeyFetchJob.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (!FlagsFactory.getFlags().getEnableBackgroundEncryptionKeyFetch()) {
            LogUtil.d(TAG, "Background key fetch is disabled; skipping execution.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (!FlagsFactory.getFlags().getSpeOnBackgroundKeyFetchJobEnabled()) {
            LogUtil.d(TAG, "SPE background key fetch job is disabled; skipping execution.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        return JOB_ENABLED_STATUS_ENABLED;
    }

    @Override
    public BackoffPolicy getBackoffPolicy() {
        return new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();
    }

    @Override
    public String getJobPolicyString(int jobId) {
        return FlagsFactory.getFlags().getBackgroundKeyFetchJobPolicy();
    }

    /** Schedules a unique instance of {@link BackgroundKeyFetchJobService}. */
    public static void schedule(Context context) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpeOnBackgroundKeyFetchJobEnabled()) {
            LogUtil.d(
                    TAG,
                    "SPE is not enabled. Schedule the job with " + "BackgroundKeyFetchJobService.");

            int resultCode =
                    BackgroundKeyFetchJobService.scheduleJobIfNeeded(
                            context, FlagsFactory.getFlags(), /* forceSchedule */ false);
            FederatedComputeJobServiceFactory.getInstance(context)
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(ENCRYPTION_KEY_FETCH_JOB_ID, resultCode);

            return;
        }

        FederatedComputeJobScheduler.getInstance(context).schedule(context, createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        Flags flags = FlagsFactory.getFlags();

        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(ENCRYPTION_KEY_FETCH_JOB_ID)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(
                                                flags.getEncryptionKeyFetchPeriodSeconds() * 1000))
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireDeviceIdle(true)
                        .setNetworkType(NETWORK_TYPE_UNMETERED)
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy).build();
    }
}
