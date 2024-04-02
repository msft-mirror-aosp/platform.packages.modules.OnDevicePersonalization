/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.app.job.JobScheduler.RESULT_FAILURE;

import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.RESET_DATA_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * JobService to handle the OnDevicePersonalization maintenance
 */
public class ResetDataJobService extends JobService {
    private static final String TAG = ResetDataJobService.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final int MILLIS = 1000;
    private ListenableFuture<Void> mFuture;

    /** Schedule the Reset job. */
    public static int schedule() {
        Flags flags = FlagsFactory.getFlags();
        Context context = OnDevicePersonalizationApplication.getAppContext();
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(RESET_DATA_JOB_ID) != null) {
            sLogger.d(TAG + ": Job is already scheduled. Doing nothing,");
            return RESULT_FAILURE;
        }

        ComponentName service = new ComponentName(context, ResetDataJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(RESET_DATA_JOB_ID, service)
                .setMinimumLatency(flags.getResetDataDelaySeconds() * MILLIS)
                .setOverrideDeadline(flags.getRenderFlowDeadlineSeconds() * MILLIS)
                .setRequiresBatteryNotLow(true)
                .build();

        return jobScheduler.schedule(jobInfo);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sLogger.d(TAG + ": onStartJob()");
        OdpJobServiceLogger.getInstance(this).recordOnStartJob(
                RESET_DATA_JOB_ID);

        mFuture = Futures.submit(new Runnable() {
            @Override
            public void run() {
                sLogger.d(TAG + ": Running reset job");
                try {
                    ResetDataTask.deleteMeasurementData();
                } catch (Exception e) {
                    sLogger.e(TAG + ": Failed to delete data", e);
                }
            }
        }, OnDevicePersonalizationExecutors.getBackgroundExecutor());

        Futures.addCallback(
                mFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.d(TAG + ": Reset job completed.");
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(
                                ResetDataJobService.this)
                                .recordJobFinished(
                                        RESET_DATA_JOB_ID,
                                        /* isSuccessful= */ true,
                                        wantsReschedule);
                        // Tell the JobScheduler that the job has completed and does not needs to be
                        // rescheduled.
                        jobFinished(params, wantsReschedule);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(TAG + ": Failed to handle JobService: " + params.getJobId(), t);
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(
                                ResetDataJobService.this)
                                .recordJobFinished(
                                        RESET_DATA_JOB_ID,
                                        /* isSuccessful= */ false,
                                        wantsReschedule);
                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        jobFinished(params, wantsReschedule);
                    }
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mFuture != null) {
            mFuture.cancel(true);
        }
        // Reschedule the job since it ended before finishing
        boolean wantsReschedule = true;
        OdpJobServiceLogger.getInstance(this)
                .recordOnStopJob(
                        params,
                        RESET_DATA_JOB_ID,
                        wantsReschedule);
        return wantsReschedule;
    }
}
