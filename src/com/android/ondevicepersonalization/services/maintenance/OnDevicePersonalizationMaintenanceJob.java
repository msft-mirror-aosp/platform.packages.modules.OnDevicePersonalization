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

package com.android.ondevicepersonalization.services.maintenance;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID;

import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

/** The background job to handle the OnDevicePersonalization maintenance. */
public final class OnDevicePersonalizationMaintenanceJob implements JobWorker {
    @VisibleForTesting static final long PERIOD_MILLIS = 86400 * 1_000; // 24 hours

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = OnDevicePersonalizationMaintenanceJob.class.getSimpleName();

    // The maximum deletion timeframe is 63 days.
    // Set parameter to 60 days to account for job scheduler delays.
    private static final long MAXIMUM_DELETION_TIMEFRAME_MILLIS = 5_184_000_000L;

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
        return Futures.submit(
                () -> {
                    cleanupVendorData(context);

                    return ExecutionResult.SUCCESS;
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());
    }

    @Override
    public int getJobEnablementStatus() {
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(
                    TAG
                            + ": GlobalKillSwitch enabled, skip execution of"
                            + " OnDevicePersonalizationMaintenanceJob.");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }

        return JOB_ENABLED_STATUS_ENABLED;
    }

    /** Schedules a unique instance of {@link OnDevicePersonalizationMaintenanceJob}. */
    public static void schedule(Context context) {
        // If SPE is not enabled, force to schedule the job with the old JobService.
        if (!FlagsFactory.getFlags().getSpePilotJobEnabled()) {
            sLogger.d(
                    "SPE is not enabled. Schedule the job with"
                            + " OnDevicePersonalizationMaintenanceJobService.");

            int resultCode =
                    OnDevicePersonalizationMaintenanceJobService.schedule(
                            context, /* forceSchedule= */ false);
            OdpJobServiceFactory.getInstance(context)
                    .getJobSchedulingLogger()
                    .recordOnSchedulingLegacy(MAINTENANCE_TASK_JOB_ID, resultCode);

            return;
        }

        OdpJobScheduler.getInstance(context).schedule(context, createDefaultJobSpec());
    }

    @VisibleForTesting
    static JobSpec createDefaultJobSpec() {
        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(MAINTENANCE_TASK_JOB_ID)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireStorageNotLow(true)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(PERIOD_MILLIS)
                                        .build())
                        .setIsPersisted(true)
                        .build();

        BackoffPolicy backoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        return new JobSpec.Builder(jobPolicy).setBackoffPolicy(backoffPolicy).build();
    }

    @VisibleForTesting
    void deleteEventsAndQueries(Context context) {
        EventsDao eventsDao = EventsDao.getInstance(context);
        // Cleanup event and queries table.
        eventsDao.deleteEventsAndQueries(
                Clock.getInstance().currentTimeMillis() - MAXIMUM_DELETION_TIMEFRAME_MILLIS);
    }

    @VisibleForTesting
    void cleanupVendorData(Context context) throws Exception {
        List<ComponentName> services =
                AppManifestConfigHelper.getOdpServices(context, /* enrolledOnly= */ true);

        OnDevicePersonalizationVendorDataDao.deleteVendorTables(context, services);
        deleteEventsAndQueries(context);
    }
}
