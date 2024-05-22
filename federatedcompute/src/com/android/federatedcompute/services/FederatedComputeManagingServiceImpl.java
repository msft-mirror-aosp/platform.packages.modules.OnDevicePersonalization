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

package com.android.federatedcompute.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.encryption.BackgroundKeyFetchJobService;
import com.android.federatedcompute.services.scheduling.DeleteExpiredJob;
import com.android.federatedcompute.services.scheduling.FederatedComputeLearningJobScheduleOrchestrator;
import com.android.federatedcompute.services.statsd.FederatedComputeStatsdLogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Implementation of FederatedCompute Service */
public class FederatedComputeManagingServiceImpl extends Service {
    private FederatedComputeManagingServiceDelegate mFcpServiceDelegate;

    private Flags mFlags;
    private Executor mExecutor;

    public FederatedComputeManagingServiceImpl() {
        mFlags = FlagsFactory.getFlags();
        mExecutor = FederatedComputeExecutors.getBackgroundExecutor();
    }

    @VisibleForTesting
    public FederatedComputeManagingServiceImpl(Executor executor) {
        mFlags = FlagsFactory.getFlags();
        mExecutor = executor;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (mFcpServiceDelegate == null) {
            mFcpServiceDelegate =
                    new FederatedComputeManagingServiceDelegate(
                            this, FederatedComputeStatsdLogger.getInstance());
            BackgroundKeyFetchJobService.scheduleJobIfNeeded(this, mFlags);
            DeleteExpiredJob.schedule(this, mFlags);
            var unused = Futures.submit(() ->
                    FederatedComputeLearningJobScheduleOrchestrator.getInstance(this)
                            .checkAndSchedule(), mExecutor);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return Objects.requireNonNull(mFcpServiceDelegate);
    }
}
