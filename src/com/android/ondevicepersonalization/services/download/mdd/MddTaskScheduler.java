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

package com.android.ondevicepersonalization.services.download.mdd;

import static android.app.job.JobScheduler.RESULT_SUCCESS;

import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_ANY;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.proto.JobPolicy.NetworkType;
import com.android.adservices.shared.spe.JobServiceConstants;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobServiceFactory;

import com.google.android.libraries.mobiledatadownload.TaskScheduler;

/**
 * MddTaskScheduler that uses JobScheduler to schedule MDD background tasks
 */
public class MddTaskScheduler implements TaskScheduler {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = MddTaskScheduler.class.getSimpleName();
    private static final String MDD_TASK_SHARED_PREFS = "mdd_worker_task_periods";
    private final Context mContext;
    static final String MDD_NETWORK_STATE_KEY = "MDD_NETWORK_STATE_KEY";
    static final String MDD_PERIOD_SECONDS_KEY = "MDD_PERIOD_SECONDS_KEY";
    static final String MDD_TASK_TAG_KEY = "MDD_TASK_TAG_KEY";

    public MddTaskScheduler(Context context) {
        this.mContext = context;
    }

    private static int getMddTaskJobId(String mddTag) {
        switch (mddTag) {
            case MAINTENANCE_PERIODIC_TASK:
                return MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
            case CHARGING_PERIODIC_TASK:
                return MDD_CHARGING_PERIODIC_TASK_JOB_ID;
            case CELLULAR_CHARGING_PERIODIC_TASK:
                return MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
            default:
                return MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
        }
    }

    // Maps from the MDD-supplied NetworkState to the JobInfo equivalent int code.
    static int getNetworkConstraints(NetworkState networkState) {
        switch (networkState) {
            case NETWORK_STATE_ANY:
                // Network not required.
                return JobInfo.NETWORK_TYPE_NONE;
            case NETWORK_STATE_CONNECTED:
                // Metered or unmetered network available.
                return JobInfo.NETWORK_TYPE_ANY;
            case NETWORK_STATE_UNMETERED:
            default:
                return JobInfo.NETWORK_TYPE_UNMETERED;
        }
    }

    @Override
    public void schedulePeriodicTask(
            String mddTaskTag, long periodSeconds, NetworkState networkState) {
        schedule(mContext, mddTaskTag, periodSeconds, networkState);
    }

    /** Schedules a unique instance of {@link MddJob}. */
    public static void schedule(Context context, PersistableBundle extras) {
        schedule(context,
                getMddTaskTag(extras),
                getMddPeriodSeconds(extras),
                getMddNetworkState(extras));
    }

    /** Schedules a unique instance of {@link MddJobService}. */
    @JobServiceConstants.JobSchedulingResultCode
    public static int scheduleWithLegacy(
            Context context, PersistableBundle extras, boolean forceSchedule) {
        return scheduleWithLegacy(context,
                getMddTaskTag(extras),
                getMddPeriodSeconds(extras),
                getMddNetworkState(extras),
                forceSchedule);
    }

    private static void schedule(Context context, String mddTaskTag, long periodSeconds,
            NetworkState networkState) {
        if (FlagsFactory.getFlags().getSpeOnMddJobEnabled()) {
            OdpJobScheduler.getInstance(context).schedule(
                    context,
                    createJobSpec(mddTaskTag, periodSeconds, networkState));
            return;
        }

        sLogger.d("SPE is not enabled. Schedule the job with MddJobService.");
        int resultCode = scheduleWithLegacy(
                context, mddTaskTag, periodSeconds, networkState, /* forceSchedule */ false);

        OdpJobServiceFactory.getInstance(context)
                .getJobSchedulingLogger()
                .recordOnSchedulingLegacy(getMddTaskJobId(mddTaskTag), resultCode);
    }

    /** Schedules a unique instance of {@link MddJobService}. */
    @VisibleForTesting
    @JobServiceConstants.JobSchedulingResultCode
    static int scheduleWithLegacy(Context context, String mddTaskTag, long periodSeconds,
            NetworkState networkState, boolean forceSchedule) {
        SharedPreferences prefs =
                context.getSharedPreferences(MDD_TASK_SHARED_PREFS, Context.MODE_PRIVATE);

        // When the period change, we will need to update the existing works.
        boolean updateCurrent = false;
        if (getCurrentPeriodValue(prefs, mddTaskTag) != periodSeconds) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(mddTaskTag, periodSeconds);
            editor.apply();
            updateCurrent = true;
        }

        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler.getPendingJob(getMddTaskJobId(mddTaskTag)) == null) {
            sLogger.d(TAG + ": MddJob %s is not scheduled, scheduling now", mddTaskTag);
            return schedulePeriodicTaskWithUpdate(
                    context , jobScheduler, mddTaskTag, periodSeconds, networkState);
        } else if (updateCurrent) {
            sLogger.d(TAG + ": scheduling MddJob %s with frequency update", mddTaskTag);
            return schedulePeriodicTaskWithUpdate(
                    context, jobScheduler, mddTaskTag, periodSeconds, networkState);
        } else if (forceSchedule) {
            sLogger.d(TAG + ": force scheduling MddJob %s", mddTaskTag);
            return schedulePeriodicTaskWithUpdate(
                    context, jobScheduler, mddTaskTag, periodSeconds, networkState);
        }
        sLogger.d(TAG + ": MddJob %s already scheduled and frequency unchanged,"
                + " not scheduling", mddTaskTag);
        return SCHEDULING_RESULT_CODE_SKIPPED;
    }

    @JobServiceConstants.JobSchedulingResultCode
    private static int schedulePeriodicTaskWithUpdate(Context context, JobScheduler jobScheduler,
            String mddTaskTag, long periodSeconds, NetworkState networkState) {
        // We use extras to pass MDD config values. They will be used in the mdd jobs.
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, mddTaskTag);
        extras.putLong(MDD_PERIOD_SECONDS_KEY, periodSeconds);
        extras.putString(MDD_NETWORK_STATE_KEY, networkState.name());

        final JobInfo job =
                new JobInfo.Builder(
                        getMddTaskJobId(mddTaskTag),
                        new ComponentName(context, MddJobService.class))
                        .setRequiresDeviceIdle(true)
                        .setRequiresCharging(false)
                        .setRequiresBatteryNotLow(true)
                        .setPeriodic(1000 * periodSeconds) // JobScheduler uses Milliseconds.
                        // persist this job across boots
                        .setPersisted(true)
                        .setRequiredNetworkType(getNetworkConstraints(networkState))
                        .setExtras(extras)
                        .build();
        int schedulingResult = jobScheduler.schedule(job);
        return RESULT_SUCCESS == schedulingResult ? SCHEDULING_RESULT_CODE_SUCCESSFUL
                : SCHEDULING_RESULT_CODE_FAILED;
    }

    @VisibleForTesting
    static JobSpec createJobSpec(String mddTaskTag, long periodSeconds, NetworkState networkState) {
        // We use extras to pass MDD config values. They will be used in the mdd jobs.
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, mddTaskTag);
        extras.putLong(MDD_PERIOD_SECONDS_KEY, periodSeconds);
        extras.putString(MDD_NETWORK_STATE_KEY, networkState.name());

        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(getMddTaskJobId(mddTaskTag))
                        .setRequireDeviceIdle(true)
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(1000 * periodSeconds)
                                        .build())
                        .setNetworkType(getNetworkType(networkState))
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy)
                .setExtras(extras).build();
    }

    private static NetworkType getNetworkType(NetworkState networkState) {
        switch (networkState) {
            case NETWORK_STATE_ANY:
            case NETWORK_STATE_CONNECTED:
                return NETWORK_TYPE_ANY;
            case NETWORK_STATE_UNMETERED:
            default:
                return NETWORK_TYPE_UNMETERED;
        }
    }

    static String getMddTaskTag(final PersistableBundle extras) {
        requireNonNullExtras(extras);
        String mddTaskTag = extras.getString(MDD_TASK_TAG_KEY);
        if (null == mddTaskTag) {
            throw new IllegalArgumentException("Mdd task tag not found");
        }
        return mddTaskTag;
    }

    private static long getMddPeriodSeconds(final PersistableBundle extras) {
        requireNonNullExtras(extras);
        return extras.getLong(MDD_PERIOD_SECONDS_KEY);
    }

    private static NetworkState getMddNetworkState(final PersistableBundle extras) {
        requireNonNullExtras(extras);
        String networkState = extras.getString(MDD_NETWORK_STATE_KEY);
        if (networkState == null) {
            throw new IllegalArgumentException("MDD extra network state not found");
        }
        return NetworkState.valueOf(networkState);
    }

    private static void requireNonNullExtras(PersistableBundle extras) {
        if (null == extras) {
            throw new IllegalArgumentException("MDD extras not found");
        }
    }

    private static long getCurrentPeriodValue(SharedPreferences prefs, String mddTaskTag) {
        try {
            return prefs.getLong(mddTaskTag, 0);
        } catch (ClassCastException e) {
            sLogger.w(e, TAG + ": ClassCastException retrieving long value from prefs for tag: %s",
                    mddTaskTag);
            return 0;
        }
    }
}
