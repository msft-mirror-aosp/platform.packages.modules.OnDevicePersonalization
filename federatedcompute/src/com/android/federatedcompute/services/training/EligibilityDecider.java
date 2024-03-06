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

import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.TaskHistory;
import com.android.federatedcompute.services.examplestore.ExampleStoreServiceProvider;
import com.android.federatedcompute.services.examplestore.FederatedExampleIterator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.ondevicepersonalization.federatedcompute.proto.DataAvailabilityPolicy;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityPolicyEvalSpec;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityTaskInfo;
import com.google.ondevicepersonalization.federatedcompute.proto.MinimumSeparationPolicy;

/** Runs eligibility evaluation and decide if device is qualified for each task. */
public class EligibilityDecider {
    private static final String TAG = EligibilityDecider.class.getSimpleName();
    private final FederatedTrainingTaskDao mTaskDao;
    private ExampleStoreServiceProvider mExampleStoreServiceProvider;

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
    public boolean computeEligibility(
            FederatedTrainingTask task,
            String taskId,
            EligibilityTaskInfo eligibilityTaskInfo,
            Context context) {
        boolean eligible = true;
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
                                    context);
                    break;
                default:
                    throw new IllegalStateException(
                            String.format("Unsupported policy %s", policyEvalSpec.getId()));
            }
            // Device has to meet all eligibility policies in order to execute task.
            if (!eligible) {
                break;
            }
        }
        return eligible;
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
        LogUtil.d(
                TAG,
                "Execute minimum separartion policy: min sep %d, current index %d, round %d",
                minSepPolicy.getMinimumSeparation(),
                minSepPolicy.getCurrentIndex(),
                taskHistory.getContributionRound());
        return minSepPolicy.getMinimumSeparation()
                <= minSepPolicy.getCurrentIndex() - taskHistory.getContributionRound();
    }

    private boolean computePerTaskDataAvailability(
            FederatedTrainingTask task,
            DataAvailabilityPolicy dataAvailabilityPolicy,
            String taskId,
            Context context) {
        try {
            IExampleStoreService exampleStoreService =
                    mExampleStoreServiceProvider.getExampleStoreService(
                            task.appPackageName(), context);
            if (exampleStoreService == null) {
                LogUtil.e(
                        TAG,
                        "Failed to compute DataAvailabilityPolicy due to bind ExampleStore"
                                + " failure %s %s %s",
                        task.appPackageName(),
                        task.populationName(),
                        taskId);
                return false;
            }
            IExampleStoreIterator iterator =
                    mExampleStoreServiceProvider.getExampleIterator(
                            exampleStoreService, task, taskId);
            if (iterator == null) {
                return false;
            }
            FederatedExampleIterator federatedExampleIterator =
                    new FederatedExampleIterator(iterator, null, null);
            int totalExamples = 0;
            while (federatedExampleIterator.hasNext()) {
                totalExamples++;
                federatedExampleIterator.next();
            }
            mExampleStoreServiceProvider.unbindFromExampleStoreService();
            LogUtil.d(
                    TAG,
                    "total examples %d data availability policy count %d ",
                    totalExamples,
                    dataAvailabilityPolicy.getMinExampleCount());
            return totalExamples >= dataAvailabilityPolicy.getMinExampleCount();
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to compute DataAvailabilityPolicy", e);
            return false;
        }
    }
}
