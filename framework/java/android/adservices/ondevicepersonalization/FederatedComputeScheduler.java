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

package android.adservices.ondevicepersonalization;

import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.federatedcompute.common.TrainingOptions;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Handles scheduling Federated Learning and Federated Analytics jobs.
 *
 * @hide
 */
public class FederatedComputeScheduler {
    private static final String TAG = FederatedComputeScheduler.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private final IFederatedComputeService mFcService;

    /** @hide */
    public FederatedComputeScheduler(IFederatedComputeService binder) {
        mFcService = binder;
    }

    // TODO(b/300461799): add federated compute server document.
    /**
     * Schedule a federated computation job.
     *
     * @param params parameters related to job scheduling.
     * @param input the configuration of the federated computation. It should be consistent with
     *     the federated computation server setup.
     * @throws IllegalArgumentException caused by caller supplied invalid input argument.
     * @throws IllegalStateException caused by an internal failure of FederatedComputeScheduler.
     */
    @WorkerThread
    public void schedule(@NonNull Params params, @NonNull FederatedComputeInput input) {
        if (mFcService == null) {
            throw new IllegalStateException(
                    "FederatedComputeScheduler not available for this instance.");
        }
        android.federatedcompute.common.TrainingInterval trainingInterval =
                convertTrainingInterval(params.getTrainingInterval());
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName(input.getPopulationName())
                        .setTrainingInterval(trainingInterval)
                        .build();
        CountDownLatch latch = new CountDownLatch(1);
        final int[] err = {0};
        try {
            mFcService.schedule(
                    trainingOptions,
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(int i) {
                            err[0] = i;
                            latch.countDown();
                        }
                    });
            latch.await();
            if (err[0] != 0) {
                throw new IllegalStateException("Internal failure occurred while scheduling job");
            }
        } catch (RemoteException | InterruptedException e) {
            sLogger.e(TAG + ": Failed to schedule federated compute job", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cancel a federated computation job with input training params.
     *
     * @param populationName population name of the job that caller wants to cancel
     * @throws IllegalStateException caused by an internal failure of FederatedComputeScheduler.
     */
    @WorkerThread
    public void cancel(@NonNull String populationName) {
        if (mFcService == null) {
            throw new IllegalStateException(
                    "FederatedComputeScheduler not available for this instance.");
        }
        CountDownLatch latch = new CountDownLatch(1);
        final int[] err = {0};
        try {
            mFcService.cancel(
                    populationName,
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(int i) {
                            err[0] = i;
                            latch.countDown();
                        }
                    });
            latch.await();
            if (err[0] != 0) {
                throw new IllegalStateException("Internal failure occurred while cancelling job");
            }
        } catch (RemoteException | InterruptedException e) {
            sLogger.e(TAG + ": Failed to cancel federated compute job", e);
            throw new IllegalStateException(e);
        }
    }

    private android.federatedcompute.common.TrainingInterval convertTrainingInterval(
            TrainingInterval interval) {
        return new android.federatedcompute.common.TrainingInterval.Builder()
                .setMinimumIntervalMillis(interval.getMinimumInterval().toMillis())
                .setSchedulingMode(interval.getSchedulingMode())
                .build();
    }

    /** The parameters related to job scheduling. */
    public static class Params {
        /**
         * If training interval is scheduled for recurrent tasks, the earliest time this task could
         * start is after the minimum training interval expires. E.g. If the task is set to run
         * maximum once per day, the first run of this task will be one day after this task is
         * scheduled. When a one time job is scheduled, the earliest next runtime is calculated
         * based on federated compute default interval.
         */
        @NonNull private final TrainingInterval mTrainingInterval;

        public Params(@NonNull TrainingInterval trainingInterval) {
            mTrainingInterval = trainingInterval;
        }

        @NonNull
        public TrainingInterval getTrainingInterval() {
            return mTrainingInterval;
        }
    }
}
