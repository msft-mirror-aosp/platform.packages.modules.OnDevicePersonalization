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

package com.android.federatedcompute.services.training;

import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreIterator;

import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;

/**
 * Centralized class for running a single computation session. It calls to native fcp client to
 * start federated ananlytic and federated training jobs.
 */
public class ComputationRunner {
    private final String mPackageName;

    public ComputationRunner(Context context) {
        this.mPackageName = context.getPackageName();
    }

    /** Run a single round of federated computation. */
    public FLRunnerResult runTaskWithNativeRunner(
            String populationName,
            String inputCheckpointFd,
            String outputCheckpointFd,
            ClientOnlyPlan clientOnlyPlan,
            ExampleSelector exampleSelector,
            ExampleConsumptionRecorder recorder,
            IExampleStoreIterator exampleStoreIterator,
            ListenableSupplier<Boolean> interruptState) {
        // TODO(b/241799297): add native fl runner to call fcp client.
        return FLRunnerResult.newBuilder()
                .setContributionResult(ContributionResult.SUCCESS)
                .build();
    }
}
