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

import static android.app.job.JobScheduler.RESULT_SUCCESS;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.shared.spe.JobServiceConstants;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.common.util.concurrent.FutureCallback;
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
    @JobServiceConstants.JobSchedulingResultCode
    public static int schedule(Context context, boolean forceSchedule) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            sLogger.e(TAG, "Failed to get job scheduler from system service.");
            return SCHEDULING_RESULT_CODE_FAILED;
        }

        if (!forceSchedule && jobScheduler.getPendingJob(DOWNLOAD_PROCESSING_TASK_JOB_ID) != null) {
            sLogger.d(TAG + ": Job is already scheduled. Doing nothing,");
            return SCHEDULING_RESULT_CODE_SKIPPED;
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

        int schedulingResult = jobScheduler.schedule(builder.build());
        return RESULT_SUCCESS == schedulingResult ? SCHEDULING_RESULT_CODE_SUCCESSFUL
                : SCHEDULING_RESULT_CODE_FAILED;
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

        // Reschedule jobs with SPE if it's enabled. Note scheduled jobs by this
        // OnDevicePersonalizationDownloadProcessingJobService will be cancelled for the same job ID
        if (FlagsFactory.getFlags().getSpeOnOdpDownloadProcessingJobEnabled()) {
            sLogger.d(
                    "SPE is enabled. Reschedule OnDevicePersonalizationDownloadProcessingJobService"
                            + " with OnDevicePersonalizationDownloadProcessingJob.");
            OnDevicePersonalizationDownloadProcessingJob.schedule(/* context */ this);
            return false;
        }

        OnDevicePersonalizationExecutors.getHighPriorityBackgroundExecutor().execute(() -> {
            mFutures = new ArrayList<>();
            // Processing installed packages
            for (String packageName : AppManifestConfigHelper.getOdpPackages(
                    /* context= */ this, /* enrolledOnly= */ true)) {
                mFutures.add(Futures.submitAsync(
                        new OnDevicePersonalizationDataProcessingAsyncCallable(
                                packageName, /* context= */ this),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor()));
            }

            // Handling task completion asynchronously
            var unused = Futures.whenAllComplete(mFutures).call(() -> {
                boolean wantsReschedule = false;
                boolean allSuccess = true;
                int successTaskCount = 0;
                int failureTaskCount = 0;
                for (ListenableFuture<Void> future : mFutures) {
                    try {
                        future.get();
                        successTaskCount++;
                    } catch (Exception e) {
                        sLogger.e(e, TAG + ": Error" + " processing" + " future");
                        failureTaskCount++;
                        allSuccess = false;
                    }
                }
                sLogger.d(TAG + ": all download" + " processing tasks"
                        + " finished, %d succeeded,"
                        + " %d failed", successTaskCount, failureTaskCount);
                // Manually trigger MDD garbage collection after finishing processing all downloads.
                MobileDataDownload mdd = MobileDataDownloadFactory.getMdd(this);
                boolean isSuccessful = allSuccess;
                Futures.addCallback(mdd.collectGarbage(), new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        OdpJobServiceLogger.getInstance(
                                OnDevicePersonalizationDownloadProcessingJobService.this)
                                .recordJobFinished(
                                    DOWNLOAD_PROCESSING_TASK_JOB_ID,
                                    /* isSuccessful= */ isSuccessful,
                                    wantsReschedule);
                        jobFinished(params, wantsReschedule);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        OdpJobServiceLogger.getInstance(
                                OnDevicePersonalizationDownloadProcessingJobService.this)
                                    .recordJobFinished(
                                        DOWNLOAD_PROCESSING_TASK_JOB_ID,
                                        /* isSuccessful= */ false,
                                        wantsReschedule);
                        jobFinished(params, wantsReschedule);
                    }
                }, OnDevicePersonalizationExecutors.getLightweightExecutor());
                return null;
            }, OnDevicePersonalizationExecutors.getLightweightExecutor());
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
