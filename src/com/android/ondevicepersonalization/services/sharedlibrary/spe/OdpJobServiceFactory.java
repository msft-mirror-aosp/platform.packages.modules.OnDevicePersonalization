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

package com.android.ondevicepersonalization.services.sharedlibrary.spe;

import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.AGGREGATE_ERROR_DATA_REPORTING_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.JOB_ID_TO_NAME_MAP;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.RESET_DATA_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.CELLULAR_CHARGING_PERIODIC_TASK;
import static com.google.android.libraries.mobiledatadownload.TaskScheduler.CHARGING_PERIODIC_TASK;
import static com.google.android.libraries.mobiledatadownload.TaskScheduler.MAINTENANCE_PERIODIC_TASK;
import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;

import android.content.Context;
import android.os.PersistableBundle;

import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.framework.JobServiceFactory;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.util.ProtoParser;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.errors.AggregateErrorDataReportingJob;
import com.android.ondevicepersonalization.services.data.errors.AggregateErrorDataReportingService;
import com.android.ondevicepersonalization.services.data.user.UserDataCollectionJob;
import com.android.ondevicepersonalization.services.data.user.UserDataCollectionJobService;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJob;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJobService;
import com.android.ondevicepersonalization.services.download.mdd.MddJob;
import com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJob;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJobService;
import com.android.ondevicepersonalization.services.reset.ResetDataJob;
import com.android.ondevicepersonalization.services.reset.ResetDataJobService;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpStatsdJobServiceLogger;

import java.util.Map;
import java.util.concurrent.Executor;

/** The ODP's implementation of {@link JobServiceFactory}. */
public final class OdpJobServiceFactory implements JobServiceFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String PROTO_PROPERTY_FOR_LOGCAT = "ODPModuleJobPolicy";
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static volatile OdpJobServiceFactory sSingleton;

    private final ModuleJobPolicy mModuleJobPolicy;
    private final Flags mFlags;
    private final ClientErrorLogger mErrorLogger;
    private final Executor mExecutor;
    private final JobServiceLogger mJobServiceLogger;
    private final JobSchedulingLogger mJobSchedulingLogger;
    private final Map<Integer, String> mJobIdTojobNameMap;

    @VisibleForTesting
    public OdpJobServiceFactory(
            JobServiceLogger jobServiceLogger,
            JobSchedulingLogger jobSchedulingLogger,
            ModuleJobPolicy moduleJobPolicy,
            ClientErrorLogger errorLogger,
            Map<Integer, String> jobIdTojobNameMap,
            Executor executor,
            Flags flags) {
        mJobServiceLogger = jobServiceLogger;
        mJobSchedulingLogger = jobSchedulingLogger;
        mModuleJobPolicy = moduleJobPolicy;
        mErrorLogger = errorLogger;
        mJobIdTojobNameMap = jobIdTojobNameMap;
        mExecutor = executor;
        mFlags = flags;
    }

    /** Gets a singleton instance of {@link OdpJobServiceFactory}. */
    public static OdpJobServiceFactory getInstance(Context context) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                Flags flags = FlagsFactory.getFlags();

                ModuleJobPolicy policy =
                        ProtoParser.parseBase64EncodedStringToProto(
                                ModuleJobPolicy.parser(),
                                ClientErrorLogger.getInstance(),
                                PROTO_PROPERTY_FOR_LOGCAT,
                                flags.getOdpModuleJobPolicy());
                sSingleton =
                        new OdpJobServiceFactory(
                                OdpJobServiceLogger.getInstance(context),
                                new JobSchedulingLogger(
                                        new OdpStatsdJobServiceLogger(),
                                        OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                                        flags),
                                policy,
                                ClientErrorLogger.getInstance(),
                                JOB_ID_TO_NAME_MAP,
                                OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                                flags);
            }

            return sSingleton;
        }
    }

    @Override
    public ModuleJobPolicy getModuleJobPolicy() {
        return mModuleJobPolicy;
    }

    @Override
    public Flags getFlags() {
        return mFlags;
    }

    @Override
    public ClientErrorLogger getErrorLogger() {
        return mErrorLogger;
    }

    @Override
    public Executor getBackgroundExecutor() {
        return mExecutor;
    }

    @Override
    public JobWorker getJobWorkerInstance(int jobId) {
        try {
            switch (jobId) {
                case AGGREGATE_ERROR_DATA_REPORTING_JOB_ID:
                    return new AggregateErrorDataReportingJob();
                case DOWNLOAD_PROCESSING_TASK_JOB_ID:
                    return new OnDevicePersonalizationDownloadProcessingJob();
                case MAINTENANCE_TASK_JOB_ID:
                    return new OnDevicePersonalizationMaintenanceJob();
                case MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID:
                    return new MddJob(CELLULAR_CHARGING_PERIODIC_TASK);
                case MDD_CHARGING_PERIODIC_TASK_JOB_ID:
                    return new MddJob(CHARGING_PERIODIC_TASK);
                case MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID:
                    return new MddJob(MAINTENANCE_PERIODIC_TASK);
                case MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID:
                    return new MddJob(WIFI_CHARGING_PERIODIC_TASK);
                case RESET_DATA_JOB_ID:
                    return new ResetDataJob();
                case USER_DATA_COLLECTION_ID:
                    return new UserDataCollectionJob();
                default:
                    throw new RuntimeException(
                            "The job is not configured for the instance creation.");
            }
        } catch (Exception e) {
            sLogger.e(e, "Creation of ODP's Job Instance is failed for jobId = %d.", jobId);
        }

        return null;
    }

    @Override
    public Map<Integer, String> getJobIdToNameMap() {
        return mJobIdTojobNameMap;
    }

    @Override
    public JobServiceLogger getJobServiceLogger() {
        return mJobServiceLogger;
    }

    @Override
    public JobSchedulingLogger getJobSchedulingLogger() {
        return mJobSchedulingLogger;
    }

    /**
     * Reschedules the corresponding background job using the legacy(non-SPE) scheduling method.
     *
     * <p>Used by {@link OdpJobService} for a job scheduled by SPE (when migrating the job to using
     * SPE framework).
     *
     * @param jobId the unique job ID for the background job to reschedule.
     * @param extras holds the extras which were passed when constructing the job in case of any,
     *               this is optional for most jobs.
     */
    public void rescheduleJobWithLegacyMethod(
            Context context, int jobId, PersistableBundle extras) {
        // The legacy job generally only checks some constraints of the job, instead of the entire
        // JobInfo including service name as SPE. Therefore, it needs to force-schedule the job
        // because the constraint should remain the same for legacy job and SPE.
        boolean forceSchedule = true;

        try {
            switch (jobId) {
                case AGGREGATE_ERROR_DATA_REPORTING_JOB_ID:
                    AggregateErrorDataReportingService.scheduleIfNeeded(context, forceSchedule);
                    return;
                case DOWNLOAD_PROCESSING_TASK_JOB_ID:
                    OnDevicePersonalizationDownloadProcessingJobService
                            .schedule(context, forceSchedule);
                    return;
                case MAINTENANCE_TASK_JOB_ID:
                    OnDevicePersonalizationMaintenanceJobService.schedule(context, forceSchedule);
                    return;
                case MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID:
                case MDD_CHARGING_PERIODIC_TASK_JOB_ID:
                case MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID:
                case MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID:
                    MddTaskScheduler.scheduleWithLegacy(context, extras, forceSchedule);
                    return;
                case RESET_DATA_JOB_ID:
                    ResetDataJobService.schedule(forceSchedule);
                    return;
                case USER_DATA_COLLECTION_ID:
                    UserDataCollectionJobService.schedule(context, forceSchedule);
                    return;
                default:
                    throw new RuntimeException(
                            "The job isn't configured for jobWorker creation. Requested Job ID: "
                                    + jobId);
            }
        } catch (Exception e) {
            sLogger.e(
                    e,
                    "Rescheduling the job using the legacy JobService is failed for jobId = %d.",
                    jobId);
        }
    }
}
