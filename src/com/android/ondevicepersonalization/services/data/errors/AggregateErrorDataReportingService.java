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

package com.android.ondevicepersonalization.services.data.errors;

import static android.app.job.JobScheduler.RESULT_FAILURE;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_JOB_NOT_CONFIGURED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.encryption.OdpEncryptionKey;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.EncryptionUtils;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;

/**
 * The {@link JobService} to perform daily reporting of aggregated error codes.
 *
 * <p>The actual reporting task is offloaded to {@link AggregatedErrorReportingWorker}.
 */
public class AggregateErrorDataReportingService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AggregateErrorDataReportingService.class.getSimpleName();

    private FluentFuture<Void> mFuture;

    private final Injector mInjector;

    public AggregateErrorDataReportingService() {
        this(new Injector());
    }

    @VisibleForTesting
    AggregateErrorDataReportingService(Injector injector) {
        mInjector = injector;
    }

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        AggregatedErrorReportingWorker getErrorReportingWorker() {
            return AggregatedErrorReportingWorker.createWorker();
        }

        OdpEncryptionKeyManager getEncryptionKeyManager(Context context) {
            return EncryptionUtils.getEncryptionKeyManager(context);
        }
    }

    /** Schedules a unique instance of the {@link AggregateErrorDataReportingService} to be run. */
    public static int scheduleIfNeeded(Context context) {
        return scheduleIfNeeded(context, FlagsFactory.getFlags());
    }

    @VisibleForTesting
    static int scheduleIfNeeded(Context context, Flags flags) {
        if (!flags.getAggregatedErrorReportingEnabled()) {
            sLogger.d(TAG + ": Aggregate error reporting is disabled.");
            return RESULT_FAILURE;
        }

        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID) != null) {
            sLogger.d(TAG + ": Job is already scheduled. Doing nothing.");
            return RESULT_FAILURE;
        }

        ComponentName serviceComponent =
                new ComponentName(context, AggregateErrorDataReportingService.class);
        JobInfo.Builder builder =
                new JobInfo.Builder(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID, serviceComponent);

        // Constraints
        builder.setRequiresDeviceIdle(true);
        builder.setRequiresBatteryNotLow(true);
        builder.setRequiresStorageNotLow(true);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        builder.setPeriodic(
                1000L
                        * FlagsFactory.getFlags().getAggregatedErrorReportingIntervalInHours()
                        * 3600L); // JobScheduler uses Milliseconds.
        // persist this job across boots
        builder.setPersisted(true);

        return jobScheduler.schedule(builder.build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sLogger.d(TAG + ": onStartJob()");
        OdpJobServiceLogger.getInstance(this)
                .recordOnStartJob(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID);
        if (mInjector.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, finishing job.");
            return cancelAndFinishJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON);
        }

        if (!mInjector.getFlags().getAggregatedErrorReportingEnabled()) {
            sLogger.d(TAG + ": aggregate error reporting disabled, finishing job.");
            return cancelAndFinishJob(
                    params,
                    AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_JOB_NOT_CONFIGURED);
        }

        OdpEncryptionKeyManager keyManager = mInjector.getEncryptionKeyManager(/* context= */ this);
        // By default, the aggregated error data payload is encrypted.
        FluentFuture<List<OdpEncryptionKey>> encryptionKeyFuture =
                mInjector.getFlags().getAllowUnencryptedAggregatedErrorReportingPayload()
                        ? FluentFuture.from(Futures.immediateFuture(List.of()))
                        : keyManager.fetchAndPersistActiveKeys(
                                OdpEncryptionKey.KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true);

        AggregatedErrorReportingWorker worker = mInjector.getErrorReportingWorker();
        mFuture =
                encryptionKeyFuture.transformAsync(
                        encryptionKeys ->
                                FluentFuture.from(
                                        worker.reportAggregateErrors(
                                                /* context= */ this,
                                                OdpEncryptionKeyManager.getRandomKey(
                                                        encryptionKeys))),
                        mInjector.getExecutor());

        Futures.addCallback(
                mFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.d(TAG + ": Aggregate error reporting job completed successfully.");
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(AggregateErrorDataReportingService.this)
                                .recordJobFinished(
                                        AGGREGATE_ERROR_DATA_REPORTING_JOB_ID,
                                        /* isSuccessful= */ true,
                                        wantsReschedule);
                        jobFinished(params, wantsReschedule);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(TAG + ": Failed to handle JobService: " + params.getJobId(), t);
                        boolean wantsReschedule = false;
                        OdpJobServiceLogger.getInstance(AggregateErrorDataReportingService.this)
                                .recordJobFinished(
                                        AGGREGATE_ERROR_DATA_REPORTING_JOB_ID,
                                        /* isSuccessful= */ false,
                                        wantsReschedule);
                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        jobFinished(params, wantsReschedule);
                    }
                },
                mInjector.getExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mFuture != null) {
            mFuture.cancel(true);
            mFuture = null;
        }

        // Reschedule the job since it ended before finishing
        boolean wantsReschedule = true;
        OdpJobServiceLogger.getInstance(this)
                .recordOnStopJob(params, AGGREGATE_ERROR_DATA_REPORTING_JOB_ID, wantsReschedule);
        return wantsReschedule;
    }

    private boolean cancelAndFinishJob(final JobParameters params, int skipReason) {
        JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID);
        }
        OdpJobServiceLogger.getInstance(this)
                .recordJobSkipped(AGGREGATE_ERROR_DATA_REPORTING_JOB_ID, skipReason);
        jobFinished(params, /* wantsReschedule= */ false);
        return true;
    }
}
