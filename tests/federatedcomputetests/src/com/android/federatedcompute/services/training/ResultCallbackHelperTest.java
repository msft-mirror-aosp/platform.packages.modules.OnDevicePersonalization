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

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;

import static com.google.common.truth.Truth.assertThat;

import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IResultHandlingService;
import android.federatedcompute.common.ExampleConsumption;
import android.federatedcompute.common.TrainingOptions;
import android.os.RemoteException;

import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;
import com.android.federatedcompute.services.training.ResultCallbackHelper.CallbackResult;

import com.google.common.collect.ImmutableList;
import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public final class ResultCallbackHelperTest {
    private static final byte[] SELECTION_CRITERIA = new byte[] {10, 0, 1};
    private static final String POPULATION_NAME = "population_name";
    private static final int JOB_ID = 123;
    private static final byte[] INTERVAL_OPTIONS = createDefaultTrainingIntervalOptions();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private static final ImmutableList<ExampleConsumption> EXAMPLE_CONSUMPTIONS =
            ImmutableList.of(
                    new ExampleConsumption.Builder()
                            .setCollectionName("collection")
                            .setExampleCount(100)
                            .setSelectionCriteria(SELECTION_CRITERIA)
                            .build());

    @Test
    public void testHandleResult_success() throws Exception {
        ResultCallbackHelper helper =
                new ResultCallbackHelper(EXAMPLE_CONSUMPTIONS, new TestResultHandlingService());

        CallbackResult result =
                helper.callHandleResult(JOB_ID, POPULATION_NAME, INTERVAL_OPTIONS, true);

        assertThat(result).isEqualTo(CallbackResult.SUCCESS);
    }

    @Test
    public void testHandleResult_remoteException() throws Exception {
        ResultCallbackHelper helper =
                new ResultCallbackHelper(
                        EXAMPLE_CONSUMPTIONS, new ResultHandlingServiceWithRemoteException());

        CallbackResult result =
                helper.callHandleResult(JOB_ID, POPULATION_NAME, INTERVAL_OPTIONS, true);

        assertThat(result).isEqualTo(CallbackResult.FAIL);
    }

    private static final class ResultHandlingServiceWithRemoteException
            extends IResultHandlingService.Stub {
        @Override
        public void handleResult(
                TrainingOptions trainingOptions,
                boolean success,
                List<ExampleConsumption> exampleConsumptionList,
                IFederatedComputeCallback callback)
                throws RemoteException {
            throw new RemoteException("expected remote exception");
        }
    }

    @Test
    public void testHandleResult_timeoutException() throws Exception {
        ResultCallbackHelper helper =
                new ResultCallbackHelper(
                        EXAMPLE_CONSUMPTIONS, new ResultHandlingServiceWithTimeoutException(), 20);

        CallbackResult result =
                helper.callHandleResult(JOB_ID, POPULATION_NAME, INTERVAL_OPTIONS, true);

        assertThat(result).isEqualTo(CallbackResult.FAIL);
    }

    class ResultHandlingServiceWithTimeoutException extends IResultHandlingService.Stub {

        @Override
        public void handleResult(
                TrainingOptions trainingOptions,
                boolean success,
                List<ExampleConsumption> exampleConsumptionList,
                IFederatedComputeCallback callback)
                throws RemoteException {
            try {
                mLatch.await(2000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }
    }

    @Test
    public void testHandleResult_interruptedException() throws Exception {
        ResultCallbackHelper helper =
                new ResultCallbackHelper(
                        EXAMPLE_CONSUMPTIONS, new ResultHandlingServiceWithInterruptedException());

        CallbackResult result =
                helper.callHandleResult(JOB_ID, POPULATION_NAME, INTERVAL_OPTIONS, true);

        assertThat(result).isEqualTo(CallbackResult.FAIL);
    }

    private static class ResultHandlingServiceWithInterruptedException
            extends IResultHandlingService.Stub {

        @Override
        public void handleResult(
                TrainingOptions trainingOptions,
                boolean success,
                List<ExampleConsumption> exampleConsumptionList,
                IFederatedComputeCallback callback)
                throws RemoteException {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void testHandleResult_failed() throws Exception {
        ResultCallbackHelper helper =
                new ResultCallbackHelper(EXAMPLE_CONSUMPTIONS, new ResultHandlingServiceWithFail());

        CallbackResult result =
                helper.callHandleResult(JOB_ID, POPULATION_NAME, INTERVAL_OPTIONS, true);

        assertThat(result).isEqualTo(CallbackResult.FAIL);
    }

    private static final class ResultHandlingServiceWithFail extends IResultHandlingService.Stub {
        @Override
        public void handleResult(
                TrainingOptions trainingOptions,
                boolean success,
                List<ExampleConsumption> exampleConsumptionList,
                IFederatedComputeCallback callback)
                throws RemoteException {
            callback.onFailure(STATUS_INTERNAL_ERROR);
        }
    }

    private static final class TestResultHandlingService extends IResultHandlingService.Stub {
        @Override
        public void handleResult(
                TrainingOptions trainingOptions,
                boolean success,
                List<ExampleConsumption> exampleConsumptionList,
                IFederatedComputeCallback callback)
                throws RemoteException {
            assertThat(success).isTrue();
            assertThat(exampleConsumptionList).containsExactlyElementsIn(EXAMPLE_CONSUMPTIONS);
            callback.onSuccess();
        }
    }

    private static byte[] createDefaultTrainingIntervalOptions() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder, SchedulingMode.ONE_TIME, 0));
        return builder.sizedByteArray();
    }
}
