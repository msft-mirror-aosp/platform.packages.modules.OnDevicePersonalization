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

package com.android.federatedcompute.services.scheduling;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.federatedcompute.services.common.FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID;
import static com.android.federatedcompute.services.common.Flags.ODP_AUTHORIZATION_TOKEN_DELETION_PERIOD_SECONDs;

import android.content.Context;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobScheduler;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/** A background job that deletes expired assets, such as authorization tokens and task history. */
public final class DeleteExpiredJob implements JobWorker {
    private static final String TAG = DeleteExpiredJobService.class.getSimpleName();

    private final Injector mInjector;

    /** Default constructor with initializing the injector. */
    public DeleteExpiredJob() {
        mInjector = new Injector();
    }

    /** Constructor that allows passing in an injector. */
    @VisibleForTesting
    public DeleteExpiredJob(Injector injector) {
        mInjector = injector;
    }

    static class Injector {
        ListeningExecutorService getExecutor() {
            return FederatedComputeExecutors.getBackgroundExecutor();
        }

        ODPAuthorizationTokenDao getODPAuthorizationTokenDao(Context context) {
            return ODPAuthorizationTokenDao.getInstance(context);
        }

        FederatedTrainingTaskDao getTrainingTaskDao(Context context) {
            return FederatedTrainingTaskDao.getInstance(context);
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }
    }

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        ListenableFuture<Integer> deleteExpiredAuthTokenFuture =
                Futures.submit(
                        () ->
                                mInjector
                                        .getODPAuthorizationTokenDao(context)
                                        .deleteExpiredAuthorizationTokens(),
                        mInjector.getExecutor());

        return FluentFuture.from(deleteExpiredAuthTokenFuture)
                .transform(
                        numberOfDeletedTokens -> {
                            LogUtil.d(
                                    TAG,
                                    "Deleted %d expired authorization tokens",
                                    numberOfDeletedTokens);

                            long deleteTime =
                                    mInjector.getClock().currentTimeMillis()
                                            - mInjector.getFlags().getTaskHistoryTtl();
                            return mInjector
                                    .getTrainingTaskDao(context)
                                    .deleteExpiredTaskHistory(deleteTime);
                        },
                        mInjector.getExecutor())
                .transform(
                        numberOfDeletedTaskHistory -> {
                            LogUtil.d(
                                    TAG,
                                    "Deleted %d expired task history",
                                    numberOfDeletedTaskHistory);
                            return ExecutionResult.SUCCESS;
                        },
                        mInjector.getExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d(TAG, "GlobalKillSwitch is enabled, finishing job.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }

        return JOB_ENABLED_STATUS_ENABLED;
    }

    /** Schedule the periodic {@link DeleteExpiredJob}. */
    public static void schedule(Context context, Flags flags) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpePilotJobEnabled()) {
            LogUtil.d(
                    TAG, "SPE is not enabled. Schedule the job with" + " DeleteExpiredJobService.");

            int resultCode =
                    DeleteExpiredJobService.scheduleJobIfNeeded(
                            context, flags, /* forceSchedule= */ false);
            FederatedComputeJobServiceFactory.getInstance(context)
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(DELETE_EXPIRED_JOB_ID, resultCode);

            return;
        }

        FederatedComputeJobScheduler.getInstance(context).schedule(context, createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(DELETE_EXPIRED_JOB_ID)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(
                                                ODP_AUTHORIZATION_TOKEN_DELETION_PERIOD_SECONDs
                                                        * 1000)
                                        .build())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireDeviceIdle(true)
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy).build();
    }
}
