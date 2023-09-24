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

import android.annotation.NonNull;
import android.federatedcompute.aidl.IFederatedComputeService;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.Objects;

/**
 * Handles scheduling federated learning and federated analytic jobs.
 *
 * @hide
 */
public class FederatedComputeScheduler {
    private static final String TAG = FederatedComputeScheduler.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    @NonNull private final IFederatedComputeService mFcService;

    /** @hide */
    public FederatedComputeScheduler(@NonNull IFederatedComputeService binder) {
        mFcService = Objects.requireNonNull(binder);
    }

    /**
     * Schedule a federated computation job.
     *
     * @param params parameters related to job scheduling.
     * @param input the configuration related o federated computation. It should be consistent with
     *     federated computation server setup. TODO(b/300461799): add federated compute server
     *     document.
     * @throws IllegalArgumentException caused by caller supplied invalid input argument.
     * @throws IllegalStateException caused by an internal failure of FederatedComputeScheduler.
     */
    public void schedule(@NonNull Params params, @NonNull FederatedComputeInput input) {
        // TODO(b/300696702): add implementation to call FCP service.
    }

    /**
     * Cancel a federated computation job with input training params.
     *
     * @param populationName population name of the job that caller wants to cancel
     * @throws IllegalStateException caused by an internal failure of FederatedComputeScheduler.
     */
    public void cancel(@NonNull String populationName) {
        // TODO(b/300696702): add implementation to call FCP service.
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
