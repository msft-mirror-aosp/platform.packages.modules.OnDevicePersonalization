/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;
import android.os.RemoteException;
import android.util.Log;

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;

import com.google.common.annotations.VisibleForTesting;
import com.google.flatbuffers.FlatBufferBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Handles scheduling training tasks e.g. calling into JobScheduler, maintaining datastore. */
public class FederatedComputeJobManager {
    private static final String TAG = "FederatedComputeJobManager";
    // TODO(b/238193394): use PH flag for these settings.
    static final int DEFAULT_SCHEDULING_PERIOD_SECS = 60 * 5; // 5 minutes
    static final int MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION = 1 * 60; // 1 min
    static final int MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION =
            6 * 24 * 60 * 60; // 6 days (< default ttl 7d)
    private static final int MAX_SCHEDULING_PERIOD_SECS = 60 * 60 * 24 * 2; // 2 days
    static final int TRAINING_TIME_FOR_LIVE_SECONDS = 7 * 24 * 60 * 60; // one week
    static final int TRAINING_SERVICE_RESULT_CALLBACK_TIMEOUT_SEC =
            60 * 9 + 45; // 9 minutes 45 seconds, leaving ~15 seconds to clean up.

    @NonNull private final Context mContext;
    private final FederatedTrainingTaskDao mFederatedTrainingTaskDao;
    private final JobSchedulerHelper mJobSchedulerHelper;
    private static FederatedComputeJobManager sSingletonInstance;
    private Clock mClock;

    @VisibleForTesting
    FederatedComputeJobManager(
            @NonNull Context context,
            FederatedTrainingTaskDao federatedTrainingTaskDao,
            JobSchedulerHelper jobSchedulerHelper,
            @NonNull Clock clock) {
        this.mContext = context.getApplicationContext();
        this.mFederatedTrainingTaskDao = federatedTrainingTaskDao;
        this.mJobSchedulerHelper = jobSchedulerHelper;
        this.mClock = clock;
    }

    /** Returns an instance of FederatedComputeJobManager given a context. */
    @NonNull
    public static FederatedComputeJobManager getInstance(@NonNull Context mContext) {
        synchronized (FederatedComputeJobManager.class) {
            if (sSingletonInstance == null) {
                sSingletonInstance =
                        new FederatedComputeJobManager(
                                mContext,
                                FederatedTrainingTaskDao.getInstance(mContext),
                                new JobSchedulerHelper(Clock.SYSTEM_CLOCK),
                                Clock.SYSTEM_CLOCK);
            }
            return sSingletonInstance;
        }
    }
    /**
     * Called when a client indicates via the client API that a task with the given parameters
     * should be scheduled.
     */
    public synchronized void onTrainerStartCalled(
            TrainingOptions trainingOptions, IFederatedComputeCallback callback) {
        FederatedTrainingTask existingTask =
                mFederatedTrainingTaskDao.findAndRemoveTaskByPopulationName(
                        trainingOptions.getPopulationName());
        Set<FederatedTrainingTask> trainingTasksToCancel = new HashSet<>();
        // If another task with same jobId exists, we only need to delete it and don't need cancel
        // the task because we will overwrite it anyway.
        mFederatedTrainingTaskDao.findAndRemoveTaskByJobId(trainingOptions.getJobSchedulerJobId());
        long nowMs = mClock.currentTimeMillis();
        int jobId = trainingOptions.getJobSchedulerJobId();
        boolean shouldSchedule = false;
        FederatedTrainingTask newTask;
        byte[] newTrainingConstraint = buildTrainingConstraints();

        if (existingTask == null) {
            FederatedTrainingTask.Builder newTaskBuilder =
                    FederatedTrainingTask.builder()
                            .appPackageName(mContext.getPackageName())
                            .jobId(jobId)
                            .creationTime(nowMs)
                            .lastScheduledTime(nowMs)
                            .schedulingReason(SchedulingReason.SCHEDULING_REASON_NEW_TASK)
                            .constraints(newTrainingConstraint)
                            .populationName(trainingOptions.getPopulationName())
                            .earliestNextRunTime(
                                    getEarliestRuntimeForInitialSchedule(
                                            nowMs, 0, trainingOptions));
            if (trainingOptions.getTrainingInterval() != null) {
                newTaskBuilder.intervalOptions(
                        buildTrainingIntervalOptions(trainingOptions.getTrainingInterval()));
            }
            newTask = newTaskBuilder.build();
            shouldSchedule = true;
        } else {
            // If a task does exist already then update only those fields that should be
            // updated: job ID, population name, constraints, last scheduled time, BUT maintain
            // other important fields like the earliest next run time unless the population or job
            // ID has changed. This ensures that repeated calls to onTrainerStart do not keep
            // postponing the job's next runtime.
            FederatedTrainingTask.Builder newTaskBuilder =
                    existingTask.toBuilder()
                            .jobId(jobId)
                            .constraints(buildTrainingConstraints())
                            .lastScheduledTime(nowMs);
            if (detectKeyParametersChanged(trainingOptions, existingTask, trainingTasksToCancel)) {
                newTaskBuilder.intervalOptions(null).lastRunStartTime(null).lastRunEndTime(null);
                newTaskBuilder
                        .populationName(trainingOptions.getPopulationName())
                        .earliestNextRunTime(
                                getEarliestRuntimeForInitialSchedule(
                                        nowMs, nowMs, trainingOptions));
                if (trainingOptions.getTrainingInterval() != null) {
                    newTaskBuilder.intervalOptions(
                            buildTrainingIntervalOptions(trainingOptions.getTrainingInterval()));
                }
                shouldSchedule = true;
            } else {
                long existingTaskMinLatencyMillis = existingTask.earliestNextRunTime() - nowMs;
                int schedulingMode =
                        trainingOptions.getTrainingInterval() != null
                                ? convertSchedulingMode(
                                        trainingOptions.getTrainingInterval().getSchedulingMode())
                                : SchedulingMode.ONE_TIME;
                long sanitizedMinLatencyMillis =
                        sanitizeMinimumLatencyMillis(existingTaskMinLatencyMillis, schedulingMode);
                long maxExpectedRuntimeSecs =
                        TRAINING_SERVICE_RESULT_CALLBACK_TIMEOUT_SEC + /*buffer*/ 30;
                boolean currentlyRunningHeuristic =
                        existingTask.lastRunStartTime() < nowMs
                                && nowMs - existingTask.lastRunStartTime()
                                        < 1000 * maxExpectedRuntimeSecs
                                && existingTask.lastRunStartTime() > existingTask.lastRunEndTime();
                long earliestNextRunTime = nowMs + sanitizedMinLatencyMillis;
                shouldSchedule =
                        !currentlyRunningHeuristic
                                && (!mJobSchedulerHelper.isTaskScheduled(mContext, existingTask)
                                        || !Arrays.equals(
                                                existingTask.constraints(), newTrainingConstraint)
                                        || !existingTask
                                                .earliestNextRunTime()
                                                .equals(earliestNextRunTime));

                // If we have to reschedule, update the earliest next run time. Otherwise,
                // retain the original earliest next run time.
                newTaskBuilder.earliestNextRunTime(
                        shouldSchedule ? earliestNextRunTime : existingTask.earliestNextRunTime());
            }
            // If we have to reschedule, mark this task as "new"; otherwise, retain the original
            // reason for scheduling it.
            newTaskBuilder.schedulingReason(
                    shouldSchedule
                            ? SchedulingReason.SCHEDULING_REASON_NEW_TASK
                            : existingTask.schedulingReason());
            newTask = newTaskBuilder.build();
        }

        // Now reconcile the new task store and JobScheduler.
        //
        // First, if necessary, try to (re)schedule the task.
        if (shouldSchedule) {
            boolean scheduleResult = mJobSchedulerHelper.scheduleTask(mContext, newTask);
            if (!scheduleResult) {
                Log.w(
                        TAG,
                        "JobScheduler returned failure when starting training job "
                                + newTask.jobId());
                // If scheduling failed then leave the task store as-is, and bail.
                sendError(callback);
                return;
            }
        }

        // Add the new task into federated training task store. if failed, return the error.
        boolean storeResult =
                mFederatedTrainingTaskDao.updateOrInsertFederatedTrainingTask(newTask);
        if (!storeResult) {
            Log.w(
                    TAG,
                    "JobScheduler returned failure when storing training job!" + newTask.jobId());
            sendError(callback);
            return;
        }
        // Second, if the task previously had a different job ID or a if there was another
        // task with the same population name, then cancel the corresponding old tasks.
        for (FederatedTrainingTask trainingTaskToCancel : trainingTasksToCancel) {
            Log.i(TAG, " JobScheduler cancel the task " + newTask.jobId());
            mJobSchedulerHelper.cancelTask(mContext, trainingTaskToCancel);
        }
        sendSuccess(callback);
    }

    private static byte[] buildTrainingConstraints() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(TrainingConstraints.createTrainingConstraints(builder, true, true, true));
        return builder.sizedByteArray();
    }

    private static byte[] buildTrainingIntervalOptions(
            @Nullable TrainingInterval trainingInterval) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        if (trainingInterval == null) {
            builder.finish(
                    TrainingIntervalOptions.createTrainingIntervalOptions(
                            builder, SchedulingMode.ONE_TIME, 0));
            return builder.sizedByteArray();
        }
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder,
                        convertSchedulingMode(trainingInterval.getSchedulingMode()),
                        trainingInterval.getMinimumIntervalMillis()));

        return builder.sizedByteArray();
    }

    private static int convertSchedulingMode(@TrainingInterval.SchedulingMode int schedulingMode) {
        if (schedulingMode == TrainingInterval.SCHEDULING_MODE_RECURRENT) {
            return SchedulingMode.RECURRENT;
        } else if (schedulingMode == TrainingInterval.SCHEDULING_MODE_ONE_TIME) {
            return SchedulingMode.ONE_TIME;
        } else {
            throw new IllegalStateException("Unknown value for scheduling mode");
        }
    }

    private boolean detectKeyParametersChanged(
            TrainingOptions newTaskOptions,
            FederatedTrainingTask existingTask,
            Set<FederatedTrainingTask> trainingTasksToCancel) {
        // Check if the task previously had a different JobScheduler job ID. If it did then
        // cancel that job for that old ID so it's not left hanging.
        boolean jobIdChanged = existingTask.jobId() != newTaskOptions.getJobSchedulerJobId();
        if (jobIdChanged) {
            Log.i(
                    TAG,
                    String.format(
                            "JobScheduler job id changed from %d to %d",
                            existingTask.jobId(), newTaskOptions.getJobSchedulerJobId()));
            trainingTasksToCancel.add(existingTask);
        }

        // Check if the task previously had a different population name.
        boolean populationChanged =
                !existingTask.populationName().equals(newTaskOptions.getPopulationName());
        if (populationChanged) {
            Log.i(
                    TAG,
                    String.format(
                            "JobScheduler population name changed from %s to %s",
                            existingTask.populationName(), newTaskOptions.getPopulationName()));
        }

        boolean trainingIntervalChanged = trainingIntervalChanged(newTaskOptions, existingTask);
        if (trainingIntervalChanged) {
            Log.i(
                    TAG,
                    String.format(
                            "JobScheduler training interval changed from %s to %s",
                            existingTask.getTrainingIntervalOptions(),
                            newTaskOptions.getTrainingInterval()));
        }
        return jobIdChanged || populationChanged || trainingIntervalChanged;
    }

    private static boolean trainingIntervalChanged(
            TrainingOptions newTaskOptions, FederatedTrainingTask existingTask) {
        byte[] incomingTrainingIntervalOptions =
                newTaskOptions.getTrainingInterval() == null
                        ? null
                        : buildTrainingIntervalOptions(newTaskOptions.getTrainingInterval());
        return !Arrays.equals(incomingTrainingIntervalOptions, existingTask.intervalOptions());
    }

    private long sanitizeMinimumLatencyMillis(long unsanitizedMillis, int schedulingMode) {
        long lowerBoundMillis;
        long upperBoundMillis;
        if (schedulingMode == SchedulingMode.RECURRENT) {
            // Recurrent task with user defined interval
            lowerBoundMillis =
                    SECONDS.toMillis(MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);
            upperBoundMillis =
                    SECONDS.toMillis(MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);
        } else {
            // One-time task or recurrent task without user defined interval
            lowerBoundMillis = 0L;
            upperBoundMillis = SECONDS.toMillis(MAX_SCHEDULING_PERIOD_SECS);
        }
        return max(lowerBoundMillis, min(upperBoundMillis, unsanitizedMillis));
    }

    private long getEarliestRuntimeForInitialSchedule(
            long nowMs, long lastRunTimeMs, TrainingOptions trainerOptions) {
        long defaultNextRunTimeMs = nowMs + SECONDS.toMillis(DEFAULT_SCHEDULING_PERIOD_SECS);
        int schedulingMode =
                trainerOptions.getTrainingInterval() != null
                        ? convertSchedulingMode(
                                trainerOptions.getTrainingInterval().getSchedulingMode())
                        : SchedulingMode.ONE_TIME;
        if (schedulingMode != SchedulingMode.RECURRENT) {
            // Non-recurrent task doesn't have user defined interval.
            return defaultNextRunTimeMs;
        }

        long userDefinedMinIntervalMillis =
                sanitizeMinimumLatencyMillis(
                        trainerOptions.getTrainingInterval() == null
                                ? 0
                                : trainerOptions.getTrainingInterval().getMinimumIntervalMillis(),
                        schedulingMode);
        // Take the smaller value of default next run time, and the next run time with user defined
        // interval.
        long minIntervalMsForRecurrentTask =
                min(nowMs + userDefinedMinIntervalMillis, defaultNextRunTimeMs);

        if (lastRunTimeMs == 0) {
            // The task has never run in the past
            return minIntervalMsForRecurrentTask;
        } else {
            // If the task has run in the past, we want to make sure the user defined minimum
            // interval has passed since last time it ran.
            return max(lastRunTimeMs + userDefinedMinIntervalMillis, minIntervalMsForRecurrentTask);
        }
    }

    private void sendError(@NonNull IFederatedComputeCallback callback) {
        try {
            callback.onFailure(STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            Log.e(TAG, "IFederatedComputeCallback error", e);
        }
    }

    private void sendSuccess(@NonNull IFederatedComputeCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            Log.e(TAG, "IFederatedComputeCallback error", e);
        }
    }
}
