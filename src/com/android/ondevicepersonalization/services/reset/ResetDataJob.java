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

package com.android.ondevicepersonalization.services.reset;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.RESET_DATA_JOB_ID;

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
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * JobService to handle the OnDevicePersonalization maintenance
 */
public final class ResetDataJob implements JobWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final long MILLIS = 1000;

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(Context context,
            ExecutionRuntimeParameters executionRuntimeParameters) {
        return Futures.submit(
                () -> {
                    deleteMeasurementData();
                    return ExecutionResult.SUCCESS;
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (!FlagsFactory.getFlags().getSpeOnResetDataJobEnabled()) {
            sLogger.d("ResetDataJob is disabled; skipping and cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        return JOB_ENABLED_STATUS_ENABLED;
    }

    /** Schedules a unique instance of {@link ResetDataJob}. */
    public static void schedule(Context context) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpeOnResetDataJobEnabled()) {
            sLogger.d("SPE is not enabled. Schedule the job with ResetDataJobService.");

            int resultCode = ResetDataJobService.schedule(/* forceSchedule */ false);
            OdpJobServiceFactory.getInstance(context)
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(RESET_DATA_JOB_ID, resultCode);

            return;
        }

        OdpJobScheduler.getInstance(context).schedule(context, createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        Flags flags = FlagsFactory.getFlags();

        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(RESET_DATA_JOB_ID)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setOneOffJobParams(
                                JobPolicy.OneOffJobParams.newBuilder()
                                        .setMinimumLatencyMs(
                                                flags.getResetDataDelaySeconds() * MILLIS)
                                        .setOverrideDeadlineMs(
                                                flags.getResetDataDeadlineSeconds() * MILLIS)
                                        .build())
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy).build();
    }

    @Override
    public BackoffPolicy getBackoffPolicy() {
        return new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();
    }

    @Override
    public String getJobPolicyString(int jobId) {
        return FlagsFactory.getFlags().getResetDataJobPolicy();
    }

    @VisibleForTesting
    void deleteMeasurementData() {
        ResetDataTask.deleteMeasurementData();
    }
}
