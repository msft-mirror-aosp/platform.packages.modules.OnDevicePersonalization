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

import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IResultHandlingService;
import android.federatedcompute.common.ExampleConsumption;
import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;
import android.os.RemoteException;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A helper class for binding to client implemented ResultHandlingService and trigger handleResult.
 */
public class ResultCallbackHelper {
    private static final String TAG = ResultCallbackHelper.class.getSimpleName();
    private static final long RESULT_HANDLING_SERVICE_CALLBACK_TIMEOUT_SECS = 60 * 9 + 45;

    /** The outcome of the result handling. */
    public enum CallbackResult {
        // Result handling succeeded, and the task completed.
        SUCCESS,
        // Result handling failed.
        FAIL,
        // Result handling succeeded, but the task needs to resume.
        NEEDS_RESUME,
    }

    private final List<ExampleConsumption> mExampleConsumptions;
    private final IResultHandlingService mResultHandlingService;
    private final long mResultHandlingServiceCallbackTimeoutSecs;

    public ResultCallbackHelper(
            List<ExampleConsumption> exampleConsumptions,
            IResultHandlingService resultHandlingService) {
        this.mExampleConsumptions = exampleConsumptions;
        this.mResultHandlingService = resultHandlingService;
        this.mResultHandlingServiceCallbackTimeoutSecs =
                RESULT_HANDLING_SERVICE_CALLBACK_TIMEOUT_SECS;
    }

    @VisibleForTesting
    ResultCallbackHelper(
            List<ExampleConsumption> exampleConsumptions,
            IResultHandlingService resultHandlingService,
            long resultHandlingServiceCallbackTimeoutSecs) {
        this.mExampleConsumptions = exampleConsumptions;
        this.mResultHandlingService = resultHandlingService;
        this.mResultHandlingServiceCallbackTimeoutSecs = resultHandlingServiceCallbackTimeoutSecs;
    }

    public CallbackResult callHandleResult(
            int jobId, String populationName, byte[] intervalOptions, boolean success) {

        SettableFuture<Integer> errorCodeFuture = SettableFuture.create();
        IFederatedComputeCallback callback =
                new IFederatedComputeCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        errorCodeFuture.set(STATUS_SUCCESS);
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        errorCodeFuture.set(errorCode);
                    }
                };
        try {
            mResultHandlingService.handleResult(
                    buildTrainingOptions(jobId, populationName, intervalOptions),
                    success,
                    mExampleConsumptions,
                    callback);
            int statusCode =
                    errorCodeFuture.get(
                            mResultHandlingServiceCallbackTimeoutSecs, TimeUnit.SECONDS);
            return statusCode == STATUS_SUCCESS ? CallbackResult.SUCCESS : CallbackResult.FAIL;
        } catch (RemoteException e) {
            LogUtil.e(
                    TAG,
                    String.format(
                            "ResultHandlingService binding died. population name: %s",
                            populationName),
                    e);
            return CallbackResult.FAIL;
        } catch (InterruptedException interruptedException) {
            LogUtil.e(
                    TAG,
                    String.format(
                            "ResultHandlingService callback interrupted. population name: %s",
                            populationName),
                    interruptedException);
            return CallbackResult.FAIL;
        } catch (ExecutionException e) {
            LogUtil.e(
                    TAG,
                    String.format(
                            "ResultHandlingService callback failed. population name: %s",
                            populationName),
                    e);
            return CallbackResult.FAIL;
        } catch (TimeoutException e) {
            LogUtil.e(
                    TAG,
                    String.format(
                            "ResultHandlingService callback timed out %d population name: %s",
                            mResultHandlingServiceCallbackTimeoutSecs, populationName),
                    e);
        }
        return CallbackResult.FAIL;
    }

    private TrainingOptions buildTrainingOptions(
            int jobId, String populationName, byte[] intervalBytes) {
        TrainingIntervalOptions intervalOptions =
                TrainingIntervalOptions.getRootAsTrainingIntervalOptions(
                        ByteBuffer.wrap(intervalBytes));
        TrainingOptions.Builder trainingOptionsBuilder = new TrainingOptions.Builder();
        trainingOptionsBuilder.setPopulationName(populationName);
        if (intervalOptions != null) {
            TrainingInterval interval =
                    new TrainingInterval.Builder()
                            .setSchedulingMode(intervalOptions.schedulingMode())
                            .setMinimumIntervalMillis(intervalOptions.minIntervalMillis())
                            .build();
            trainingOptionsBuilder.setTrainingInterval(interval);
        }
        return trainingOptionsBuilder.build();
    }
}
