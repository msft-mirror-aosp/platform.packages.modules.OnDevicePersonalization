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

package com.android.ondevicepersonalization.services.federatedcompute;

import static android.app.job.JobScheduler.RESULT_FAILURE;

import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.FEDERATED_COMPUTE_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.ODP_POPULATION_NAME;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.FederatedComputeManager;
import android.federatedcompute.common.ScheduleFederatedComputeRequest;
import android.federatedcompute.common.TrainingOptions;
import android.os.OutcomeReceiver;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CountDownLatch;

/** JobService to handle the OnDevicePersonalization FederatedCompute scheduling */
public class OdpFederatedComputeJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OdpFederatedComputeJobService";
    private static final long PERIOD_SECONDS = 86400;
    private static final long ASYNC_SCHEDULE_TIMEOUT_MS = 6000;
    private ListenableFuture<Void> mFuture;

    /** Schedules a unique instance of OdpFederatedComputeJobService to be run. */
    public static int schedule(Context context) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(FEDERATED_COMPUTE_TASK_JOB_ID) != null) {
            sLogger.d(TAG + ": Job is already scheduled. Doing nothing,");
            return RESULT_FAILURE;
        }
        ComponentName serviceComponent =
                new ComponentName(context, OdpFederatedComputeJobService.class);
        JobInfo.Builder builder =
                new JobInfo.Builder(FEDERATED_COMPUTE_TASK_JOB_ID, serviceComponent);

        // Constraints.
        // TODO(278106108): Update scheduling conditions.
        builder.setRequiresDeviceIdle(true);
        builder.setRequiresBatteryNotLow(true);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        builder.setPeriodic(1000 * PERIOD_SECONDS); // JobScheduler uses Milliseconds.
        // persist this job across boots
        builder.setPersisted(true);

        return jobScheduler.schedule(builder.build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sLogger.d(TAG + ": onStartJob()");
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, finishing job.");
            jobFinished(params, /* wantsReschedule= */ false);
            return true;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        mFuture =
                Futures.submit(
                        new Runnable() {
                            @Override
                            public void run() {
                                scheduleFederatedCompute(latch);
                            }
                        },
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());

        Futures.addCallback(
                mFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // Prevent the framework from unbinding/freezing/garbage collecting the
                        // remote FCP process before receiving the async schedule callback
                        try {
                            boolean asyncTaskTimelyCompletion =
                                    latch.await(ASYNC_SCHEDULE_TIMEOUT_MS, MILLISECONDS);
                            if (asyncTaskTimelyCompletion) {
                                sLogger.d(TAG + ": Job completed successfully.");
                            } else {
                                sLogger.w(TAG + ": Job completed, but the remote schedule call "
                                        + "did not finish on time");
                            }
                        } catch (InterruptedException e) {
                            sLogger.w(TAG + ": Job completed, the callback thread is interrupted "
                                    + "while waiting for latch countdown");
                        }
                        // Tell the JobScheduler that the job has completed and does not needs to be
                        // rescheduled.
                        jobFinished(params, /* wantsReschedule= */ false);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(TAG + ": Failed to handle JobService: " + params.getJobId(), t);
                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        jobFinished(params, /* wantsReschedule= */ false);
                    }
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        sLogger.d(TAG + ": onStopJob()");
        if (mFuture != null) {
            mFuture.cancel(true);
        }
        // Reschedule the job since it ended before finishing
        return true;
    }

    @VisibleForTesting
    void scheduleFederatedCompute(final CountDownLatch latch) {
        if (federatedComputeNeedsScheduling()) {
            FederatedComputeManager FCManager =
                    this.getSystemService(FederatedComputeManager.class);
            if (FCManager == null) {
                sLogger.e(TAG + ": Failed to get FederatedCompute Service");
                latch.countDown();
                return;
            }
            TrainingOptions trainingOptions =
                    new TrainingOptions.Builder().setPopulationName(ODP_POPULATION_NAME).build();
            ScheduleFederatedComputeRequest request =
                    new ScheduleFederatedComputeRequest.Builder()
                            .setTrainingOptions(trainingOptions)
                            .build();
            FCManager.schedule(
                    request,
                    OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                    new OutcomeReceiver<Object, Exception>() {
                        @Override
                        public void onResult(Object result) {
                            sLogger.d(TAG + ": Successfully scheduled federatedCompute");
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception error) {
                            sLogger.e(TAG + ": Error while scheduling federatedCompute", error);
                            latch.countDown();
                            OutcomeReceiver.super.onError(error);
                        }
                    });
        } else {
            latch.countDown();
        }
    }

    private boolean federatedComputeNeedsScheduling() {
        // TODO(278106108): Add conditions for when to schedule.
        return true;
    }
}
