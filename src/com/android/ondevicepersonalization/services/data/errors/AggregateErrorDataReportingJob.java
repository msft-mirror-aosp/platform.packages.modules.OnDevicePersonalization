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

package com.android.ondevicepersonalization.services.data.errors;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID;

import android.content.Context;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.encryption.OdpEncryptionKey;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.EncryptionUtils;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;
import java.util.Optional;

/**
 * The {@link JobWorker} to perform daily reporting of aggregated error codes.
 *
 * <p>The actual reporting task is offloaded to {@link AggregatedErrorReportingWorker}.
 */
public final class AggregateErrorDataReportingJob implements JobWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AggregateErrorDataReportingJob.class.getSimpleName();

    private final Injector mInjector;

    public AggregateErrorDataReportingJob() {
        this(new Injector());
    }

    @VisibleForTesting
    AggregateErrorDataReportingJob(Injector injector) {
        mInjector = injector;
    }

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        AggregatedErrorReportingWorker getErrorReportingWorker() {
            return AggregatedErrorReportingWorker.createWorker();
        }

        OdpEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return EncryptionUtils.getEncryptionKeyManager(context);
        }
    }

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        // By default, the aggregated error data payload is encrypted.
        FluentFuture<List<OdpEncryptionKey>> encryptionKeyFuture =
                mInjector.getFlags().getAllowUnencryptedAggregatedErrorReportingPayload()
                        ? FluentFuture.from(Futures.immediateFuture(List.of()))
                        : mInjector.getEncryptionKeyManager(context)
                                .fetchAndPersistActiveKeys(
                                        OdpEncryptionKey.KEY_TYPE_ENCRYPTION,
                                        /* isScheduledJob */ true,
                                        /* loggerOptional*/ Optional.empty());
        return
                encryptionKeyFuture.transformAsync(
                        encryptionKeys ->
                                FluentFuture.from(
                                        mInjector.getErrorReportingWorker().reportAggregateErrors(
                                                context,
                                                OdpEncryptionKeyManager
                                                        .getRandomKey(encryptionKeys))),
                        mInjector.getExecutor())
                        .transform(voidResult -> ExecutionResult.SUCCESS, mInjector.getExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (mInjector.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG
                    + ": GlobalKillSwitch enabled, skip execution of"
                    + " AggregateErrorDataReportingJob.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (!mInjector.getFlags().getAggregatedErrorReportingEnabled()) {
            sLogger.d(TAG + ": aggregate error reporting disabled, finishing job.");
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
        return FlagsFactory.getFlags().getAggregateErrorDataReportingJobPolicy();
    }

    /** Schedules a unique instance of {@link AggregateErrorDataReportingJob}. */
    public static void schedule(Context context) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpeOnAggregateErrorDataReportingJobEnabled()) {
            sLogger.d(
                    "SPE is not enabled. Schedule the job with"
                            + " AggregateErrorDataReportingService.");

            int resultCode =
                    AggregateErrorDataReportingService.scheduleIfNeeded(
                            context, /* forceSchedule */ false);
            OdpJobServiceFactory.getInstance(context)
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID, resultCode);

            return;
        }

        OdpJobScheduler.getInstance(context).schedule(context, createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setNetworkType(NETWORK_TYPE_UNMETERED)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder().setPeriodicIntervalMs(
                                        FlagsFactory.getFlags()
                                                .getAggregatedErrorReportingIntervalInHours()
                                        * 3600L * 1000L
                                ))
                        .setIsPersisted(true)
                        .build();
        return new JobSpec.Builder(jobPolicy).build();
    }
}
