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

import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MAINTENANCE_TASK_JOB_ID;

import android.app.job.JobParameters;

import com.android.adservices.shared.spe.framework.AbstractJobService;
import com.android.adservices.shared.spe.framework.JobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;

/** The ODP's implementation of {@link AbstractJobService}. */
public final class OdpJobService extends AbstractJobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    @Override
    protected JobServiceFactory getJobServiceFactory() {
        return OdpJobServiceFactory.getInstance(this);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        int jobId = params.getJobId();

        // Switch to the legacy job scheduling if SPE is disabled. Since job ID remains the same,
        // the scheduled job will be cancelled and rescheduled with the legacy method.
        //
        // And after the job is rescheduled, it will execute once instantly so don't log execution
        // stats here.
        if (shouldRescheduleWithLegacyMethod(jobId)) {
            sLogger.d(
                    "SPE is disabled. Reschedule SPE job instance of jobId=%d with its legacy"
                            + " JobService scheduling method.",
                    jobId);

            OdpJobServiceFactory factory = (OdpJobServiceFactory) getJobServiceFactory();
            factory.rescheduleJobWithLegacyMethod(this, jobId);

            return false;
        }

        return super.onStartJob(params);
    }

    // Determine whether we should cancel and reschedule current job with the legacy JobService
    // class. It could happen when SPE has a production issue so SPE gets disabled.
    //
    // The first batch job to migrate is,
    // - OnDevicePersonalizationMaintenanceJobService, job ID = 1005.
    @VisibleForTesting
    boolean shouldRescheduleWithLegacyMethod(int jobId) {
        Flags flags = FlagsFactory.getFlags();

        if (jobId == MAINTENANCE_TASK_JOB_ID && !flags.getSpePilotJobEnabled()) {
            return true;
        }

        return false;
    }
}
