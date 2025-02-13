/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.download.mdd;


import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;

import android.content.Context;

import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJob;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * MDD JobService. This will download MDD files in background tasks.
 */
public final class MddJob implements JobWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "MddJob";

    private final String mMddTaskTag;
    private final Injector mInjector;

    public MddJob(String mddTaskTag) {
        mInjector = new Injector();
        mMddTaskTag = mddTaskTag;
    }

    @VisibleForTesting
    public MddJob(String mddTaskTag, Injector injector) {
        mInjector = injector;
        mMddTaskTag = mddTaskTag;
    }

    static class Injector {
        ListeningExecutorService getBackgroundExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }
    }

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        ListenableFuture<Void> handleTaskFuture =
                PropagatedFutures.submitAsync(
                        () -> MobileDataDownloadFactory.getMdd(context).handleTask(mMddTaskTag),
                        mInjector.getBackgroundExecutor());

        return PropagatedFutures.transform(handleTaskFuture, unused -> {
            if (WIFI_CHARGING_PERIODIC_TASK.equals(mMddTaskTag)) {
                OnDevicePersonalizationDownloadProcessingJob.schedule(context);
            }
            return ExecutionResult.SUCCESS;
        }, mInjector.getBackgroundExecutor());
    }

    @Override
    public ListenableFuture<Void> getExecutionStopFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        // Attempt to process any data downloaded before the worker was stopped.
        return PropagatedFutures.submit(() -> {
            if (WIFI_CHARGING_PERIODIC_TASK.equals(mMddTaskTag)) {
                OnDevicePersonalizationDownloadProcessingJob.schedule(context);
            }
        },
        mInjector.getBackgroundExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (mInjector.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, skip execution of MddJob.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (!mInjector.getFlags().getSpeOnMddJobEnabled()) {
            sLogger.d(TAG + ": mdd is disabled; skipping and cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        if (UserPrivacyStatus.getInstance().isProtectedAudienceAndMeasurementBothDisabled()) {
            sLogger.d(TAG + ": consent revoked; skipping, cancelling job");
            return JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
        }
        return JOB_ENABLED_STATUS_ENABLED;
    }

    @Override
    public BackoffPolicy getBackoffPolicy() {
        return new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();
    }
}
