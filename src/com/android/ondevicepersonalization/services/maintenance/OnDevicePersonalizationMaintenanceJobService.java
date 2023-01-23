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

package com.android.ondevicepersonalization.services.maintenance;

import static android.app.job.JobScheduler.RESULT_FAILURE;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * JobService to handle the OnDevicePersonalization maintenance
 */
public class OnDevicePersonalizationMaintenanceJobService extends JobService {
    public static final String TAG = "OnDevicePersonalizationMaintenanceJobService";
    private ListenableFuture<Void> mFuture;

    /**
     * Schedules a unique instance of OnDevicePersonalizationMaintenanceJobService to be run.
     */
    public static int schedule(Context context) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID) != null) {
            Log.d(TAG, "Job is already scheduled. Doing nothing,");
            return RESULT_FAILURE;
        }
        ComponentName serviceComponent = new ComponentName(context,
                OnDevicePersonalizationMaintenanceJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(
                OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID, serviceComponent);

        // Constraints.
        builder.setRequiresDeviceIdle(true);
        builder.setRequiresBatteryNotLow(true);
        builder.setRequiresStorageNotLow(true);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);

        return jobScheduler.schedule(builder.build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob()");
        mFuture = Futures.submit(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Running maintenance job");
                // TODO(b/266345774): Implement maintenance work
            }
        }, OnDevicePersonalizationExecutors.getBackgroundExecutor());

        Futures.addCallback(
                mFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "Maintenance job completed.");
                        // Tell the JobScheduler that the job has completed and does not needs to be
                        // rescheduled.
                        jobFinished(params, /* wantsReschedule = */ false);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Failed to handle JobService: " + params.getJobId(), t);
                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        jobFinished(params, /* wantsReschedule = */ false);
                    }
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mFuture.cancel(true);
        // Reschedule the job since it ended before finishing
        return true;
    }
}
