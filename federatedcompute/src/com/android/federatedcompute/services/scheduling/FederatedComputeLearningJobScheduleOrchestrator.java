/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.federatedcompute.services.scheduling;

import android.annotation.NonNull;
import android.app.job.JobScheduler;
import android.content.Context;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;

/**
 * Class that deals with rescheduling learning jobs in case they were unexpectedly cancelled.
 */
public class FederatedComputeLearningJobScheduleOrchestrator {

    private static final String TAG =
            FederatedComputeLearningJobScheduleOrchestrator.class.getSimpleName();

    private static volatile FederatedComputeLearningJobScheduleOrchestrator sInstance;
    @NonNull
    private final Context mContext;
    private final FederatedTrainingTaskDao mFederatedTrainingTaskDao;
    private final JobSchedulerHelper mJobSchedulerHelper;

    @VisibleForTesting
    FederatedComputeLearningJobScheduleOrchestrator(
            @NonNull Context context,
            FederatedTrainingTaskDao federatedTrainingTaskDao,
            JobSchedulerHelper jobSchedulerHelper) {
        this.mContext = context.getApplicationContext();
        this.mFederatedTrainingTaskDao = federatedTrainingTaskDao;
        this.mJobSchedulerHelper = jobSchedulerHelper;
    }

    /**
     * Returns an instance of the FederatedComputeLearningJobScheduleOrchestrator given a context.
     */
    public static FederatedComputeLearningJobScheduleOrchestrator getInstance(
            @NonNull Context context) {
        if (sInstance == null) {
            synchronized (FederatedComputeLearningJobScheduleOrchestrator.class) {
                if (sInstance == null) {
                    Clock clock = MonotonicClock.getInstance();
                    sInstance =
                            new FederatedComputeLearningJobScheduleOrchestrator(
                                    context.getApplicationContext(),
                                    FederatedTrainingTaskDao.getInstance(context),
                                    new JobSchedulerHelper(clock));
                }
            }
        }
        return sInstance;
    }

    /**
     * Checks if there are any FederatedTrainingTasks in Db that are not scheduled and schedules
     * them.
     */
    public void checkAndSchedule() {
        LogUtil.d(TAG, "checkAndSchedule started!");
        final JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e(TAG, "Failed to get job scheduler from system service.");
            return;
        }
        // get all tasks from DB
        List<FederatedTrainingTask> tasks =
                mFederatedTrainingTaskDao.getFederatedTrainingTask(null, null);
        if (tasks != null) {
            for (FederatedTrainingTask task : tasks) {
                LogUtil.d(TAG, "checkAndSchedule found task with jobId %d!", task.jobId());
                // check if task is scheduled already
                if (jobScheduler.getPendingJob(task.jobId()) == null) {
                    LogUtil.d(TAG, "task with jobId %d is not scheduled!", task.jobId());
                    //reschedule if task is not scheduled already
                    mJobSchedulerHelper.scheduleTask(mContext, task);
                }
            }
        }
    }
}
