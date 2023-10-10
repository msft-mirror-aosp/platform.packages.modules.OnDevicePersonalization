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

package com.android.federatedcompute.services.training;

import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBackgroundExecutor;

import android.app.job.JobParameters;
import android.app.job.JobService;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FlagsFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;

/** Main service for the scheduled federated computation jobs. */
public class FederatedJobService extends JobService {
    private static final String TAG = FederatedJobService.class.getSimpleName();

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d(TAG, "FederatedJobService.onStartJob");
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d(TAG, "GlobalKillSwitch enabled, finishing job.");
            jobFinished(params, /* wantsReschedule= */ false);
            return true;
        }
        FederatedComputeWorker worker = FederatedComputeWorker.getInstance(this);
        ListenableFuture<FLRunnerResult> runCompleteFuture =
                worker.startTrainingRun(params.getJobId());

        Futures.addCallback(
                runCompleteFuture,
                new FutureCallback<FLRunnerResult>() {
                    @Override
                    public void onSuccess(FLRunnerResult flRunnerResult) {
                        LogUtil.d(TAG, "Federated computation job %d is done!", params.getJobId());
                        if (flRunnerResult != null) {
                            worker.finish(flRunnerResult);
                        }
                        jobFinished(params, /* wantsReschedule= */ false);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(
                                TAG, t, "Failed to handle computation job: %d", params.getJobId());
                        worker.finish(null, ContributionResult.FAIL, false);
                        jobFinished(params, /* wantsReschedule= */ false);
                    }
                },
                getBackgroundExecutor());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d(TAG, "FederatedJobService.onStopJob %d", params.getJobId());
        FederatedComputeWorker.getInstance(this).finish(null, ContributionResult.FAIL, true);
        return false;
    }
}
