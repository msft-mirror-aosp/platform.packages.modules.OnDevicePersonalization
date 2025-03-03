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

package com.android.ondevicepersonalization.services.download.mdd;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_PERSONALIZATION_NOT_ENABLED;
import static com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler.getMddTaskTag;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJob;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * MDD JobService. This will download MDD files in background tasks.
 */
public class MddJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "MddJobService";

    private final Injector mInjector;

    public MddJobService() {
        mInjector = new Injector();
    }

    @VisibleForTesting
    public MddJobService(Injector injector) {
        mInjector = injector;
    }

    static class Injector {
        ListeningExecutorService getBackgroundExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sLogger.d(TAG + ": onStartJob()");
        OdpJobServiceLogger.getInstance(this).recordOnStartJob(params.getJobId());

        if (mInjector.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, finishing job.");
            return cancelAndFinishJob(params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }

        // Reschedule jobs with SPE if it's enabled.
        // Note: scheduled jobs by this MddJobService will be cancelled for the same job ID
        if (mInjector.getFlags().getSpeOnMddJobEnabled()) {
            sLogger.d("SPE is enabled. Reschedule MddJobService with MddJob.");
            MddTaskScheduler.schedule(/* context */ this, params.getExtras());
            return false;
        }

        // Run privacy status checks in the background
        runPrivacyStatusChecksInBackgroundAndExecute(params);
        return true;
    }

    private void runPrivacyStatusChecksInBackgroundAndExecute(final JobParameters params) {
        OnDevicePersonalizationExecutors.getHighPriorityBackgroundExecutor().execute(() -> {
            if (UserPrivacyStatus.getInstance().isProtectedAudienceAndMeasurementBothDisabled()) {
                // User control is revoked; handle this case
                sLogger.d(TAG + ": User control is not given for all ODP services.");
                OdpJobServiceLogger.getInstance(MddJobService.this)
                        .recordJobSkipped(params.getJobId(),
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_PERSONALIZATION_NOT_ENABLED);
                jobFinished(params, false);
            } else {
                // User control is given; handle the MDD task
                ListenableFuture<Void> handleTaskFuture =
                        PropagatedFutures.submitAsync(
                                () -> MobileDataDownloadFactory.getMdd(this)
                                        .handleTask(getMddTaskTag(params.getExtras())),
                                mInjector.getBackgroundExecutor());

                Futures.addCallback(
                        handleTaskFuture,
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                handleSuccess(params);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                handleFailure(params, t);
                            }
                        },
                        mInjector.getBackgroundExecutor());
            }
        });
    }

    private void handleSuccess(JobParameters params) {
        sLogger.d(TAG + ": MddJobService.MddHandleTask succeeded!");
        if (WIFI_CHARGING_PERIODIC_TASK.equals(getMddTaskTag(params.getExtras()))) {
            OnDevicePersonalizationDownloadProcessingJob.schedule(/* context */ this);
        }
        recordJobFinished(params.getJobId(), true);
        jobFinished(params, false);
    }

    private void handleFailure(JobParameters params, Throwable throwable) {
        sLogger.e(TAG + ": Failed to handle JobService: " + params.getJobId(), throwable);
        recordJobFinished(params.getJobId(), false);
        jobFinished(params, false);
    }

    private void recordJobFinished(int jobId, boolean isSuccessful) {
        boolean wantsReschedule = false;
        OdpJobServiceLogger.getInstance(this)
                .recordJobFinished(jobId, isSuccessful, wantsReschedule);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Attempt to process any data downloaded before the worker was stopped.
        if (WIFI_CHARGING_PERIODIC_TASK.equals(getMddTaskTag(params.getExtras()))) {
            OnDevicePersonalizationDownloadProcessingJob.schedule(/* context */ this);
        }
        // Reschedule the job since it ended before finishing
        boolean wantsReschedule = true;
        OdpJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, params.getJobId(), wantsReschedule);
        return wantsReschedule;
    }

    private boolean cancelAndFinishJob(final JobParameters params, int skipReason) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        int jobId = params.getJobId();
        if (jobScheduler != null) {
            jobScheduler.cancel(jobId);
        }
        OdpJobServiceLogger.getInstance(this).recordJobSkipped(jobId, skipReason);
        jobFinished(params, /* wantsReschedule = */ false);
        return true;
    }
}
