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

import android.content.Context;

import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.shared.spe.scheduling.PolicyJobScheduler;
import com.android.internal.annotations.GuardedBy;

/** The FederatedCompute's instance of {@link PolicyJobScheduler}. */
public class FederatedComputeJobScheduler extends PolicyJobScheduler<FederatedComputeJobService> {
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static volatile FederatedComputeJobScheduler sSingleton;

    private final Context mContext;

    private FederatedComputeJobScheduler(Context context) {
        super(
                FederatedComputeJobServiceFactory.getInstance(context),
                FederatedComputeJobService.class);
        mContext = context.getApplicationContext();
    }

    /** Gets the singleton instance of {@link FederatedComputeJobScheduler}. */
    public static FederatedComputeJobScheduler getInstance(Context context) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton = new FederatedComputeJobScheduler(context);
            }

            return sSingleton;
        }
    }

    /**
     * An overloading method to {@link PolicyJobScheduler#schedule(Context, JobSpec)} with passing
     * in FederatedCompute's app context.
     *
     * @param jobSpec a {@link JobSpec} that stores the specifications used to schedule a job.
     */
    public void schedule(Context context, JobSpec jobSpec) {
        super.schedule(mContext, jobSpec);
    }
}
