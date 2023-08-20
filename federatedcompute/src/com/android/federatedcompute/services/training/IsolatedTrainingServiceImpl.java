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
import android.federatedcompute.aidl.IResultHandlingService;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder;
import com.android.federatedcompute.services.training.aidl.IIsolatedTrainingService;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

/** The implementation of {@link IsolatedTrainingService}. */
public class IsolatedTrainingServiceImpl extends IIsolatedTrainingService.Stub {
    private static final String TAG = "IsolatedTrainingServiceImpl";
    private final AtomicBoolean mInterruptFlag = new AtomicBoolean(false);

    @VisibleForTesting
    ListenableSupplier<Boolean> mInterruptState = new ListenableSupplier<>(mInterruptFlag::get);

    private ComputationRunner mComputationRunner;

    public IsolatedTrainingServiceImpl(Context context) {
        mComputationRunner = new ComputationRunner(context);
    }

    @VisibleForTesting
    IsolatedTrainingServiceImpl(ComputationRunner computationRunner) {
        this.mComputationRunner = computationRunner;
    }

    @Override
    public void runFlTraining(@Nonnull Bundle params, @Nonnull ITrainingResultCallback callback) {
        Objects.requireNonNull(params);
        Objects.requireNonNull(callback);
        IExampleStoreIterator exampleStoreIteratorBinder =
                IExampleStoreIterator.Stub.asInterface(
                        Objects.requireNonNull(
                                params.getBinder(Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER)));
        Objects.requireNonNull(exampleStoreIteratorBinder);
        IResultHandlingService resultHandlingServiceBinder =
                IResultHandlingService.Stub.asInterface(
                        Objects.requireNonNull(
                                params.getBinder(Constants.EXTRA_RESULT_HANDLING_SERVICE_BINDER)));
        Objects.requireNonNull(resultHandlingServiceBinder);
        ExampleConsumptionRecorder recorder = new ExampleConsumptionRecorder();
        byte[] exampleSelectorBytes =
                Objects.requireNonNull(params.getByteArray(Constants.EXTRA_EXAMPLE_SELECTOR));
        ExampleSelector exampleSelector;
        try {
            exampleSelector = ExampleSelector.parseFrom(exampleSelectorBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("ExampleSelector proto is invalid", e);
        }
        String populationName =
                Objects.requireNonNull(params.getString(Constants.EXTRA_POPULATION_NAME));
        ParcelFileDescriptor inputCheckpointFd =
                Objects.requireNonNull(
                        params.getParcelable(
                                Constants.EXTRA_INPUT_CHECKPOINT_FD, ParcelFileDescriptor.class));
        ParcelFileDescriptor outputCheckpointFd =
                Objects.requireNonNull(
                        params.getParcelable(
                                Constants.EXTRA_OUTPUT_CHECKPOINT_FD, ParcelFileDescriptor.class));
        int jobId = params.getInt(Constants.EXTRA_JOB_ID);
        byte[] clientPlanBytes =
                Objects.requireNonNull(params.getByteArray(Constants.EXTRA_CLIENT_ONLY_PLAN));
        ClientOnlyPlan clientPlan;
        try {
            clientPlan = ClientOnlyPlan.parseFrom(clientPlanBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("ClientOnlyPlan proto is invalid", e);
        }

        ListenableFuture<FLRunnerResult> resultFuture =
                Futures.submit(
                        () ->
                                mComputationRunner.runTaskWithNativeRunner(
                                        jobId,
                                        populationName,
                                        getFileDescriptorForTensorflow(inputCheckpointFd),
                                        getFileDescriptorForTensorflow(outputCheckpointFd),
                                        clientPlan,
                                        exampleSelector,
                                        recorder,
                                        exampleStoreIteratorBinder,
                                        resultHandlingServiceBinder,
                                        mInterruptState),
                        FederatedComputeExecutors.getBackgroundExecutor());

        Futures.addCallback(
                resultFuture,
                new FutureCallback<FLRunnerResult>() {
                    @Override
                    public void onSuccess(FLRunnerResult result) {
                        sendResult(result, callback);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Failed to runTaskWithNativeRunner", t);
                        FLRunnerResult result =
                                FLRunnerResult.newBuilder()
                                        .setContributionResult(ContributionResult.FAIL)
                                        .build();
                        sendResult(result, callback);
                    }
                },
                FederatedComputeExecutors.getLightweightExecutor());
    }

    // We implement a customized tensorflow filesystem which support file descriptor for read and
    // write. The file format is "fd:///${fd_number}".
    private String getFileDescriptorForTensorflow(ParcelFileDescriptor parcelFileDescriptor) {
        return "fd:///" + parcelFileDescriptor.getFd();
    }

    private void sendResult(FLRunnerResult result, ITrainingResultCallback callback) {
        Bundle bundle = new Bundle();
        bundle.putByteArray(Constants.EXTRA_FL_RUNNER_RESULT, result.toByteArray());
        try {
            callback.onResult(bundle);
        } catch (RemoteException e) {
            Log.w(TAG + ": Callback failed ", e);
        }
    }

    @Override
    public void cancelTraining(long runId) {
        mInterruptFlag.set(true);
        mInterruptState.runListeners();
    }
}