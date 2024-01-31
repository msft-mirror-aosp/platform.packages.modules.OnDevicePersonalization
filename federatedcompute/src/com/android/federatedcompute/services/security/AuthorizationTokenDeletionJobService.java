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

package com.android.federatedcompute.services.security;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

public class AuthorizationTokenDeletionJobService extends JobService {

    private static final String TAG = AuthorizationTokenDeletionJobService.class.getSimpleName();

    private static final int AUTH_TOKEN_DELETION_JOB_ID =
            FederatedComputeJobInfo.ODP_AUTHORIZATION_TOKEN_DELETION_JOB_ID;

    private final Injector mInjector;

    /** Empty Constructor for AuthorizationTokenDeletionJobService. */
    public AuthorizationTokenDeletionJobService() {
        mInjector = new Injector();
    }

    @VisibleForTesting
    public AuthorizationTokenDeletionJobService(Injector injector) {
        mInjector = injector;
    }

    static class Injector {
        ListeningExecutorService getExecutor() {
            return FederatedComputeExecutors.getBackgroundExecutor();
        }

        ODPAuthorizationTokenDao getODPAuthorizationTokenDao(Context context) {
            return ODPAuthorizationTokenDao.getInstance(context);
        }
    }

    /** Runs the background key fetch and persist keys job. The method is for testing only. */
    @VisibleForTesting
    public void run(JobParameters params) {
        var unused = Futures.submit(() -> onStartJob(params), mInjector.getExecutor());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d(TAG, "AuthorizationTokenDeletionJobService.onStartJob %d", params.getJobId());
        if (FlagsFactory.getFlags().getGlobalKillSwitch()
                || !FlagsFactory.getFlags().isAuthenticationEnabled()) {
            LogUtil.d(TAG, "GlobalKillSwitch enabled or authentication disabled, finishing job.");
            jobFinished(params, /* wantsReschedule= */ false);
            return true;
        }
        try {
            int rowsDeleted =
                    mInjector.getODPAuthorizationTokenDao(this).deleteExpiredAuthorizationTokens();
            LogUtil.d(TAG, "Deleted %d expired tokens.", rowsDeleted);

        } catch (Exception e) {
            LogUtil.d(TAG, "Exception encountered when deleting expired tokens: " + e.getMessage());
        } finally {
            jobFinished(params, /* wantsReschedule= */ false);
        }

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d(TAG, "AuthorizationTokenDeletionJobService.onStopJob %d", params.getJobId());
        return false;
    }

    /** Schedule the periodic authorization token deletion job if it is not scheduled. */
    public static boolean scheduleJobIfNeeded(Context context, Flags flags) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e(TAG, "Failed to get job scheduler from system service.");
            return false;
        }

        final JobInfo scheduledJob = jobScheduler.getPendingJob(AUTH_TOKEN_DELETION_JOB_ID);
        final JobInfo jobInfo =
                new JobInfo.Builder(
                                AUTH_TOKEN_DELETION_JOB_ID,
                                new ComponentName(
                                        context, AuthorizationTokenDeletionJobService.class))
                        .setPeriodic(
                                flags.getAuthorizationTokenDeletionPeriodSeconds()
                                        * 1000) // convert to milliseconds
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build();

        if (!jobInfo.equals(scheduledJob)) {
            jobScheduler.schedule(jobInfo);
            LogUtil.d(
                    TAG,
                    "Scheduled job AuthorizationTokenDeletionJobService id %d",
                    AUTH_TOKEN_DELETION_JOB_ID);
            return true;
        } else {
            LogUtil.d(
                    TAG,
                    "Already scheduled job AuthorizationTokenDeletionJobService id %d",
                    AUTH_TOKEN_DELETION_JOB_ID);
            return false;
        }
    }
}
