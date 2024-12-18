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

package com.android.federatedcompute.services.training;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_COMPLETED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ELIGIBLE;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ERROR_EXAMPLE_ITERATOR;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_EXAMPLE_STORE_BIND_ERROR;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_EXAMPLE_STORE_BIND_START;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_EXAMPLE_STORE_BIND_SUCCESS;

import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;
import android.os.SystemClock;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.ExampleStats;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.TaskHistory;
import com.android.federatedcompute.services.examplestore.ExampleStoreServiceProvider;
import com.android.federatedcompute.services.training.util.EligibilityResult;
import com.android.internal.annotations.VisibleForTesting;

import com.google.internal.federated.plan.ExampleSelector;
import com.google.ondevicepersonalization.federatedcompute.proto.DataAvailabilityPolicy;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityPolicyEvalSpec;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityTaskInfo;
import com.google.ondevicepersonalization.federatedcompute.proto.MinimumSeparationPolicy;

/** Runs eligibility evaluation and decide if device is qualified for each task. */
public class EligibilityDecider {
    private static final String TAG = EligibilityDecider.class.getSimpleName();
    private final FederatedTrainingTaskDao mTaskDao;
    private final ExampleStoreServiceProvider mExampleStoreServiceProvider;

    @VisibleForTesting
    EligibilityDecider(
            FederatedTrainingTaskDao taskDao,
            ExampleStoreServiceProvider exampleStoreServiceProvider) {
        mExampleStoreServiceProvider = exampleStoreServiceProvider;
        mTaskDao = taskDao;
    }

    public EligibilityDecider(FederatedTrainingTaskDao taskDao) {
        this(taskDao, new ExampleStoreServiceProvider());
    }

    /**
     * Computes the eligibility of the client for the given tasks in the population eligibility
     * spec. Returns true if device is eligible to execute this task.
     */
    public EligibilityResult computeEligibility(
            FederatedTrainingTask task,
            String taskId,
            EligibilityTaskInfo eligibilityTaskInfo,
            Context context,
            TrainingEventLogger trainingEventLogger,
            ExampleSelector exampleSelector) {
        boolean eligible = true;
        ExampleStats exampleStats = new ExampleStats();
        trainingEventLogger.logEventKind(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED);
        EligibilityResult.Builder result = new EligibilityResult.Builder();
        for (EligibilityPolicyEvalSpec policyEvalSpec :
                eligibilityTaskInfo.getEligibilityPoliciesList()) {
            switch (policyEvalSpec.getPolicyTypeCase()) {
                case MIN_SEP_POLICY:
                    eligible =
                            computePerTaskMinSeparation(
                                    policyEvalSpec.getMinSepPolicy(),
                                    task.populationName(),
                                    taskId,
                                    task.jobId());
                    break;
                case DATA_AVAILABILITY_POLICY:
                    eligible =
                            computePerTaskDataAvailability(
                                    task,
                                    policyEvalSpec.getDataAvailabilityPolicy(),
                                    taskId,
                                    context,
                                    exampleStats,
                                    trainingEventLogger,
                                    result,
                                    exampleSelector);
                    break;
                default:
                    throw new IllegalStateException(
                            String.format("Unsupported policy %s", policyEvalSpec.getId()));
            }
            // Device has to meet all eligibility policies in order to execute task.
            if (!eligible) {
                result.setEligible(false);
                break;
            }
        }
        // Always record eligibility task complete event. To calculate not eligible tasks, it
        // is (EVAL_COMPUTATION_COMPLETED - EVAL_COMPUTATION_ELIGIBLE).
        trainingEventLogger.logEventWithExampleStats(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_COMPLETED,
                exampleStats);
        EligibilityResult eligibilityResult = result.setEligible(eligible).build();

        if (eligible) {
            trainingEventLogger.logEventKind(
                    FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ELIGIBLE);
            return eligibilityResult;
        }

        // If device is not eligible, we should unbind from ExampleStore if needed.
        if (eligibilityResult.getExampleStoreIterator() != null) {
            mExampleStoreServiceProvider.unbindFromExampleStoreService();
        }
        return new EligibilityResult.Builder().setEligible(false).build();
    }

    private boolean computePerTaskMinSeparation(
            MinimumSeparationPolicy minSepPolicy, String populationName, String taskId, int jobId) {
        TaskHistory taskHistory = mTaskDao.getLatestTaskHistory(jobId, populationName, taskId);
        // Treat null as the task never run before, then device is qualified.
        if (taskHistory == null) {
            LogUtil.d(
                    TAG,
                    "population name %s task id %s job id %d doesn't have TaskHistory record.",
                    populationName,
                    taskId,
                    jobId);
            return true;
        }
        return minSepPolicy.getMinimumSeparation()
                <= minSepPolicy.getCurrentIndex() - taskHistory.getContributionRound();
    }

    private boolean computePerTaskDataAvailability(
            FederatedTrainingTask task,
            DataAvailabilityPolicy dataAvailabilityPolicy,
            String taskId,
            Context context,
            ExampleStats exampleStats,
            TrainingEventLogger logger,
            EligibilityResult.Builder result,
            ExampleSelector exampleSelector) {
        try {
            long callStartTimeNanos = SystemClock.elapsedRealtimeNanos();
            logger.logEventKind(
                    FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_EXAMPLE_STORE_BIND_START);
            IExampleStoreService exampleStoreService =
                    mExampleStoreServiceProvider.getExampleStoreService(
                            task.appPackageName(), context);
            if (exampleStoreService == null) {
                logger.logEventKind(
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ERROR_EXAMPLE_ITERATOR);
                logger.logEventKind(
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_EXAMPLE_STORE_BIND_ERROR);
                LogUtil.e(
                        TAG,
                        "Failed to compute DataAvailabilityPolicy due to bind ExampleStore"
                                + " failure %s %s %s",
                        task.appPackageName(),
                        task.populationName(),
                        taskId);
                return false;
            }
            logger.logEventKind(
                    FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_EXAMPLE_STORE_BIND_SUCCESS);
            exampleStats.mBindToExampleStoreLatencyNanos.addAndGet(
                    SystemClock.elapsedRealtimeNanos() - callStartTimeNanos);
            callStartTimeNanos = SystemClock.elapsedRealtimeNanos();
            IExampleStoreIterator iterator =
                    mExampleStoreServiceProvider.getExampleIterator(
                            exampleStoreService,
                            task,
                            taskId,
                            dataAvailabilityPolicy.getMinExampleCount(),
                            exampleSelector,
                            logger);
            if (iterator == null) {
                LogUtil.d(
                        TAG,
                        "Failed to compute DataAvailabilityPolicy due to iterator is null "
                                + "%s %s %s",
                        task.appPackageName(),
                        task.populationName(),
                        taskId);
                logger.logEventKind(
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ERROR_EXAMPLE_ITERATOR);
                mExampleStoreServiceProvider.unbindFromExampleStoreService();
                return false;
            }
            result.setExampleStoreIterator(iterator);
            exampleStats.mStartQueryLatencyNanos.addAndGet(
                    SystemClock.elapsedRealtimeNanos() - callStartTimeNanos);
            return true;
        } catch (Exception e) {
            mExampleStoreServiceProvider.unbindFromExampleStoreService();
            LogUtil.e(TAG, e, "Failed to compute DataAvailabilityPolicy");
            return false;
        }
    }
}
