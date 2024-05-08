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

package com.android.federatedcompute.services.sharedlibrary.spe;

import android.app.job.JobParameters;

import com.android.adservices.shared.spe.framework.AbstractJobService;
import com.android.adservices.shared.spe.framework.JobServiceFactory;
import com.android.federatedcompute.internal.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

/** The FederatedCompute's implementation of {@link AbstractJobService}. */
public final class FederatedComputeJobService extends AbstractJobService {
    private static final String TAG = FederatedComputeJobService.class.getSimpleName();

    @Override
    protected JobServiceFactory getJobServiceFactory() {
        return FederatedComputeJobServiceFactory.getInstance(this);
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
            LogUtil.d(
                    TAG,
                    "SPE is disabled. Reschedule SPE job instance of jobId=%d with its legacy"
                            + " JobService scheduling method.",
                    jobId);
            FederatedComputeJobServiceFactory factory =
                    (FederatedComputeJobServiceFactory) getJobServiceFactory();
            factory.rescheduleJobWithLegacyMethod(jobId);
            return false;
        }

        return super.onStartJob(params);
    }

    // Determine whether we should cancel and reschedule current job with the legacy JobService
    // class. It could happen when SPE has a production issue.
    @VisibleForTesting
    boolean shouldRescheduleWithLegacyMethod(int jobId) {
        return false;
    }
}
