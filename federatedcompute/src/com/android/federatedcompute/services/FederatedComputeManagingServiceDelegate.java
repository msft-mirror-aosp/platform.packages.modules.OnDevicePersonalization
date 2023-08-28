/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IFederatedComputeService;
import android.federatedcompute.common.TrainingOptions;
import android.os.Binder;

import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/** Implementation of {@link IFederatedComputeService}. */
public class FederatedComputeManagingServiceDelegate extends IFederatedComputeService.Stub {
    private static final String TAG = "FcpServiceDelegate";
    @NonNull private final Context mContext;

    @VisibleForTesting
    static class Injector {
        FederatedComputeJobManager getJobManager(Context context) {
            return FederatedComputeJobManager.getInstance(context);
        }
    }

    @NonNull private final Injector mInjector;

    public FederatedComputeManagingServiceDelegate(@NonNull Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting
    public FederatedComputeManagingServiceDelegate(
            @NonNull Context context, @NonNull Injector injector) {
        mContext = Objects.requireNonNull(context);
        mInjector = Objects.requireNonNull(injector);
    }

    @Override
    public void scheduleFederatedCompute(
            String callingPackageName,
            TrainingOptions trainingOptions,
            IFederatedComputeCallback callback) {
        // Use FederatedCompute instead of caller permission to read experiment flags. It requires
        // READ_DEVICE_CONFIG permission.
        long origId = Binder.clearCallingIdentity();
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            throw new IllegalStateException("Service skipped as the global kill switch is on.");
        }
        Binder.restoreCallingIdentity(origId);

        Objects.requireNonNull(callingPackageName);
        Objects.requireNonNull(callback);

        FederatedComputeJobManager jobManager = mInjector.getJobManager(mContext);
        FederatedComputeExecutors.getBackgroundExecutor()
                .execute(
                        () -> {
                            jobManager.onTrainerStartCalled(
                                    callingPackageName, trainingOptions, callback);
                        });
    }
}
