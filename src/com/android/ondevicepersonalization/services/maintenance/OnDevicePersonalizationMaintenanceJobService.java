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

package com.android.ondevicepersonalization.services.maintenance;

import static android.app.job.JobScheduler.RESULT_FAILURE;
import static android.content.pm.PackageManager.GET_META_DATA;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * JobService to handle the OnDevicePersonalization maintenance
 */
public class OnDevicePersonalizationMaintenanceJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationMaintenanceJobService";

    // Every 24hrs.
    private static final long PERIOD_SECONDS = 86400;

    // The maximum deletion timeframe is 63 days.
    // Set parameter to 60 days to account for job scheduler delays.
    private static final long MAXIMUM_DELETION_TIMEFRAME_MILLIS = 5184000000L;
    private ListenableFuture<Void> mFuture;

    /**
     * Schedules a unique instance of OnDevicePersonalizationMaintenanceJobService to be run.
     */
    public static int schedule(Context context) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID) != null) {
            sLogger.d(TAG + ": Job is already scheduled. Doing nothing,");
            return RESULT_FAILURE;
        }
        ComponentName serviceComponent = new ComponentName(context,
                OnDevicePersonalizationMaintenanceJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(
                OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID, serviceComponent);

        // Constraints.
        builder.setRequiresDeviceIdle(true);
        builder.setRequiresBatteryNotLow(true);
        builder.setRequiresStorageNotLow(true);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        builder.setPeriodic(1000 * PERIOD_SECONDS); // JobScheduler uses Milliseconds.
        // persist this job across boots
        builder.setPersisted(true);

        return jobScheduler.schedule(builder.build());
    }

    @VisibleForTesting
    static void cleanupVendorData(Context context) throws Exception {
        EventsDao eventsDao = EventsDao.getInstance(context);

        // Set of packageName and cert
        Set<Map.Entry<String, String>> vendors = new HashSet<>(
                OnDevicePersonalizationVendorDataDao.getVendors(context));

        // Remove all valid packages from the set
        for (PackageInfo packageInfo : context.getPackageManager().getInstalledPackages(
                PackageManager.PackageInfoFlags.of(GET_META_DATA))) {
            String packageName = packageInfo.packageName;
            if (AppManifestConfigHelper.manifestContainsOdpSettings(
                    context, packageName)) {
                vendors.remove(new AbstractMap.SimpleImmutableEntry<>(packageName,
                        PackageUtils.getCertDigest(context, packageName)));
            }
        }

        sLogger.d(TAG + ": Deleting: " + vendors);
        // Delete the remaining tables for packages not found onboarded
        for (Map.Entry<String, String> entry : vendors) {
            String packageName = entry.getKey();
            String certDigest = entry.getValue();
            OnDevicePersonalizationVendorDataDao.deleteVendorData(context, packageName, certDigest);
            eventsDao.deleteEventState(entry.getKey());
            // TODO(296893799): Cancel any tasks scheduled by the uninstalled package with FCP.
        }

        // Cleanup event and queries table.
        eventsDao.deleteEventsAndQueries(
                System.currentTimeMillis() - MAXIMUM_DELETION_TIMEFRAME_MILLIS);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sLogger.d(TAG + ": onStartJob()");
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch enabled, finishing job.");
            jobFinished(params, /* wantsReschedule = */ false);
            return true;
        }
        Context context = this;
        mFuture = Futures.submit(new Runnable() {
            @Override
            public void run() {
                sLogger.d(TAG + ": Running maintenance job");
                try {
                    cleanupVendorData(context);
                } catch (Exception e) {
                    sLogger.e(TAG + ": Failed to cleanup vendorData", e);
                }
            }
        }, OnDevicePersonalizationExecutors.getBackgroundExecutor());

        Futures.addCallback(
                mFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.d(TAG + ": Maintenance job completed.");
                        // Tell the JobScheduler that the job has completed and does not needs to be
                        // rescheduled.
                        jobFinished(params, /* wantsReschedule = */ false);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(TAG + ": Failed to handle JobService: " + params.getJobId(), t);
                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        jobFinished(params, /* wantsReschedule = */ false);
                    }
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mFuture != null) {
            mFuture.cancel(true);
        }
        // Reschedule the job since it ended before finishing
        return true;
    }
}
