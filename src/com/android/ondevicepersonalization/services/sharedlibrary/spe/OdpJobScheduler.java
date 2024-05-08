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

import android.content.Context;

import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.shared.spe.scheduling.PolicyJobScheduler;
import com.android.internal.annotations.GuardedBy;

public final class OdpJobScheduler extends PolicyJobScheduler<OdpJobService> {
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static volatile OdpJobScheduler sSingleton;

    private final Context mContext;

    private OdpJobScheduler(Context context) {
        super(OdpJobServiceFactory.getInstance(context), OdpJobService.class);
        mContext = context.getApplicationContext();
    }

    /** Gets the singleton instance of {@link OdpJobScheduler}. */
    public static OdpJobScheduler getInstance(Context context) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton = new OdpJobScheduler(context);
            }

            return sSingleton;
        }
    }

    /**
     * An overloading method to {@link PolicyJobScheduler#schedule(Context, JobSpec)} with passing
     * in ODP's app context.
     *
     * @param jobSpec a {@link JobSpec} that stores the specifications used to schedule a job.
     */
    public void schedule(Context context, JobSpec jobSpec) {
        super.schedule(mContext, jobSpec);
    }
}
