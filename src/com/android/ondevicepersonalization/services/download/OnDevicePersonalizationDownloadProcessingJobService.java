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

package com.android.ondevicepersonalization.services.download;

import static android.app.job.JobScheduler.RESULT_FAILURE;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * JobService to handle the processing of the downloaded vendor data
 */
public class OnDevicePersonalizationDownloadProcessingJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationDownloadProcessingJobService";
    private List<ListenableFuture<Void>> mFutures;

    /**
     * Schedules a unique instance of OnDevicePersonalizationDownloadProcessingJobService to be run.
     */
    public static int schedule(Context context) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(
                DOWNLOAD_PROCESSING_TASK_JOB_ID) != null) {
            sLogger.d(TAG + ": Job is already scheduled. Doing nothing,");
            return RESULT_FAILURE;
        }
        ComponentName serviceComponent = new ComponentName(context,
                OnDevicePersonalizationDownloadProcessingJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(
                DOWNLOAD_PROCESSING_TASK_JOB_ID, serviceComponent);

        // Constraints.
        builder.setRequiresDeviceIdle(true);
        builder.setRequiresBatteryNotLow(true);
        builder.setRequiresStorageNotLow(true);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        builder.setPersisted(true);

        return jobScheduler.schedule(builder.build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sLogger.d(TAG + ": onStartJob()");
        OdpJobServiceLogger.getInstance(this).recordOnStartJob(DOWNLOAD_PROCESSING_TASK_JOB_ID);
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, finishing job.");
            OdpJobServiceLogger.getInstance(this).recordJobSkipped(
                    DOWNLOAD_PROCESSING_TASK_JOB_ID,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
            jobFinished(params, /* wantsReschedule = */ false);
            return true;
        }

        OnDevicePersonalizationExecutors.getHighPriorityBackgroundExecutor()
                .execute(
                        () -> {
                            mFutures = new ArrayList<>();
                            // Processing installed packages
                            for (String packageName :
                                    AppManifestConfigHelper.getOdpPackages(
                                            /* context= */ this, /* enrolledOnly= */ true)) {
                                mFutures.add(
                                        Futures.submitAsync(
                                                new OnDevicePersonalizationDataProcessingAsyncCallable(
                                                        packageName, /* context= */ this),
                                                OnDevicePersonalizationExecutors
                                                        .getBackgroundExecutor()));
                            }

                            // Handling task completion asynchronously
                            var unused =
                                    Futures.whenAllComplete(mFutures)
                                            .call(
                                                    () -> {
                                                        boolean wantsReschedule = false;
                                                        boolean allSuccess = true;
                                                        int successTaskCount = 0;
                                                        int failureTaskCount = 0;
                                                        for (ListenableFuture<Void> future :
                                                                mFutures) {
                                                            try {
                                                                future.get();
                                                                successTaskCount++;
                                                            } catch (Exception e) {
                                                                sLogger.e(
                                                                        e,
                                                                        TAG
                                                                                + ": Error"
                                                                                + " processing"
                                                                                + " future");
                                                                failureTaskCount++;
                                                                allSuccess = false;
                                                            }
                                                        }
                                                        sLogger.d(
                                                                TAG
                                                                        + ": all download"
                                                                        + " processing tasks"
                                                                        + " finished, %d succeeded,"
                                                                        + " %d failed",
                                                                successTaskCount,
                                                                failureTaskCount);
                                                        OdpJobServiceLogger.getInstance(
                                                                        OnDevicePersonalizationDownloadProcessingJobService
                                                                                .this)
                                                                .recordJobFinished(
                                                                        DOWNLOAD_PROCESSING_TASK_JOB_ID,
                                                                        /* isSuccessful= */ allSuccess,
                                                                        wantsReschedule);
                                                        jobFinished(params, wantsReschedule);
                                                        return null;
                                                    },
                                                    OnDevicePersonalizationExecutors
                                                            .getLightweightExecutor());
                        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mFutures != null) {
            for (ListenableFuture<Void> f : mFutures) {
                f.cancel(true);
            }
        }
        // Reschedule the job since it ended before finishing
        boolean wantsReschedule = true;
        OdpJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params,
                        DOWNLOAD_PROCESSING_TASK_JOB_ID,
                        wantsReschedule);
        return wantsReschedule;
    }
}
