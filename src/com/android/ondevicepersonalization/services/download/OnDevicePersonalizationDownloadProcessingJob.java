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

package com.android.ondevicepersonalization.services.download;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_NONE;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;

import android.content.Context;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * JobService to handle the processing of the downloaded vendor data
 */
public final class OnDevicePersonalizationDownloadProcessingJob implements JobWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationDownloadProcessingJob";

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(Context context,
            ExecutionRuntimeParameters executionRuntimeParameters) {
        ListenableFuture<List<ListenableFuture<Void>>> outerFeature =
                Futures.submit(
                        () -> {
                            List<ListenableFuture<Void>> innerFeatures = new ArrayList<>();
                            // Processing installed packages
                            for (String packageName : AppManifestConfigHelper.getOdpPackages(
                                    context, /* enrolledOnly= */ true)) {
                                innerFeatures.add(Futures.submitAsync(
                                        new OnDevicePersonalizationDataProcessingAsyncCallable(
                                                packageName, context),
                                        OnDevicePersonalizationExecutors.getBackgroundExecutor()));
                            }
                            return innerFeatures;
                        },
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());

        // Handling task completion asynchronously
        return Futures.transformAsync(
                outerFeature,
                innerFutures -> Futures.whenAllComplete(innerFutures).call(() -> {
                    boolean allSuccess = true;
                    int successTaskCount = 0;
                    int failureTaskCount = 0;
                    for (ListenableFuture<Void> future : innerFutures) {
                        try {
                            future.get();
                            successTaskCount++;
                        } catch (Exception e) {
                            sLogger.e(e, TAG + ": Error processing future");
                            failureTaskCount++;
                            allSuccess = false;
                        }
                    }
                    sLogger.d(TAG + ": all download processing tasks finished, %d succeeded,"
                            + " %d failed", successTaskCount, failureTaskCount);
                    // Manually trigger MDD garbage collection after finishing processing all
                    // downloads.
                    MobileDataDownload mdd = MobileDataDownloadFactory.getMdd(context);
                    var unused = mdd.collectGarbage();

                    return allSuccess ? ExecutionResult.SUCCESS
                            : ExecutionResult.FAILURE_WITHOUT_RETRY;
                }, OnDevicePersonalizationExecutors.getLightweightExecutor()),
                OnDevicePersonalizationExecutors.getLightweightExecutor()
        );
    }

    @Override
    public int getJobEnablementStatus() {
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, skip execution of "
                    + "OnDevicePersonalizationDownloadProcessingJob.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (!FlagsFactory.getFlags().getSpeOnOdpDownloadProcessingJobEnabled()) {
            sLogger.d(TAG + ": download processing is disabled; skipping and cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        return JOB_ENABLED_STATUS_ENABLED;
    }

    @Override
    public BackoffPolicy getBackoffPolicy() {
        return new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();
    }

    /** Schedules a unique instance of {@link OnDevicePersonalizationDownloadProcessingJob}. */
    public static void schedule(Context context) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpeOnOdpDownloadProcessingJobEnabled()) {
            sLogger.d("SPE is not enabled. Schedule the job with "
                    + "OnDevicePersonalizationDownloadProcessingJobService.");

            int resultCode = OnDevicePersonalizationDownloadProcessingJobService.schedule(
                    context, /* forceSchedule */ false);
            OdpJobServiceFactory.getInstance(context)
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(DOWNLOAD_PROCESSING_TASK_JOB_ID, resultCode);

            return;
        }

        OdpJobScheduler.getInstance(context).schedule(context, createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(DOWNLOAD_PROCESSING_TASK_JOB_ID)
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setNetworkType(NETWORK_TYPE_NONE)
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy).build();
    }
}
