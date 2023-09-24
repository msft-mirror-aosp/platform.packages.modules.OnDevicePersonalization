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
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IResultHandlingService;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;
import com.android.federatedcompute.services.training.ResultCallbackHelper.CallbackResult;

import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public final class ResultCallbackHelperTest {
    private static final String PACKAGE_NAME = "app_package_name";
    private static final byte[] SELECTION_CRITERIA = new byte[] {10, 0, 1};
    private static final int SCHEDULING_REASON = SchedulingReason.SCHEDULING_REASON_NEW_TASK;
    private static final String POPULATION_NAME = "population_name";
    private static final String TASK_NAME = "task_name";
    private static final int JOB_ID = 123;
    private static final byte[] INTERVAL_OPTIONS = createDefaultTrainingIntervalOptions();
    private static final byte[] TRAINING_CONSTRAINTS = createDefaultTrainingConstraints();
    private final CountDownLatch mLatch = new CountDownLatch(1);

    private static final FederatedTrainingTask TRAINING_TASK =
            FederatedTrainingTask.builder()
                    .appPackageName(PACKAGE_NAME)
                    .jobId(123)
                    .populationName(POPULATION_NAME)
                    .intervalOptions(INTERVAL_OPTIONS)
                    .constraints(TRAINING_CONSTRAINTS)
                    .serverAddress("server_address")
                    .creationTime(123L)
                    .lastScheduledTime(123L)
                    .earliestNextRunTime(123L)
                    .schedulingReason(SCHEDULING_REASON)
                    .build();
    private static final ArrayList<ExampleConsumption> EXAMPLE_CONSUMPTIONS =
            getExampleConsumptions();
    private ResultCallbackHelper mHelper;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mHelper = Mockito.spy(new ResultCallbackHelper(context));
        doNothing().when(mHelper).unbindFromResultHandlingService();
    }

    @Test
    public void testHandleResult_success() throws Exception {
        doReturn(new TestResultHandlingService())
                .when(mHelper)
                .getResultHandlingService(eq(PACKAGE_NAME));

        CallbackResult result =
                mHelper.callHandleResult(TASK_NAME, TRAINING_TASK, EXAMPLE_CONSUMPTIONS, true)
                        .get();

        assertThat(result).isEqualTo(CallbackResult.SUCCESS);
    }

    @Test
    public void testHandleResult_remoteException() throws Exception {
        doReturn(new ResultHandlingServiceWithRemoteException())
                .when(mHelper)
                .getResultHandlingService(eq(PACKAGE_NAME));

        CallbackResult result =
                mHelper.callHandleResult(TASK_NAME, TRAINING_TASK, EXAMPLE_CONSUMPTIONS, true)
                        .get();

        assertThat(result).isEqualTo(CallbackResult.FAIL);
    }

    @Test
    public void testHandleResult_interruptedException() throws Exception {
        doReturn(new ResultHandlingServiceWithInterruptedException())
                .when(mHelper)
                .getResultHandlingService(eq(PACKAGE_NAME));

        CallbackResult result =
                mHelper.callHandleResult(TASK_NAME, TRAINING_TASK, EXAMPLE_CONSUMPTIONS, true)
                        .get();

        assertThat(result).isEqualTo(CallbackResult.FAIL);
    }

    @Test
    public void testHandleResult_failed() throws Exception {
        doReturn(new ResultHandlingServiceWithFail())
                .when(mHelper)
                .getResultHandlingService(eq(PACKAGE_NAME));

        CallbackResult result =
                mHelper.callHandleResult(TASK_NAME, TRAINING_TASK, EXAMPLE_CONSUMPTIONS, true)
                        .get();

        assertThat(result).isEqualTo(CallbackResult.FAIL);
    }

    private static final class ResultHandlingServiceWithRemoteException
            extends IResultHandlingService.Stub {
        @Override
        public void handleResult(Bundle input, IFederatedComputeCallback callback)
                throws RemoteException {
            throw new RemoteException("expected remote exception");
        }
    }

    private static class ResultHandlingServiceWithInterruptedException
            extends IResultHandlingService.Stub {

        @Override
        public void handleResult(Bundle input, IFederatedComputeCallback callback)
                throws RemoteException {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ResultHandlingServiceWithFail extends IResultHandlingService.Stub {
        @Override
        public void handleResult(Bundle input, IFederatedComputeCallback callback)
                throws RemoteException {
            callback.onFailure(STATUS_INTERNAL_ERROR);
        }
    }

    private static final class TestResultHandlingService extends IResultHandlingService.Stub {
        @Override
        public void handleResult(Bundle input, IFederatedComputeCallback callback)
                throws RemoteException {
            assertThat(input.getInt(ClientConstants.EXTRA_COMPUTATION_RESULT))
                    .isEqualTo(STATUS_SUCCESS);
            assertThat(
                            input.getParcelableArrayList(
                                    ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST,
                                    ExampleConsumption.class))
                    .containsExactlyElementsIn(EXAMPLE_CONSUMPTIONS);
            callback.onSuccess();
        }
    }

    private static ArrayList<ExampleConsumption> getExampleConsumptions() {
        ArrayList<ExampleConsumption> exampleList = new ArrayList<>();
        exampleList.add(
                new ExampleConsumption.Builder()
                        .setCollectionName("collection")
                        .setExampleCount(100)
                        .setSelectionCriteria(SELECTION_CRITERIA)
                        .build());
        return exampleList;
    }

    private static byte[] createDefaultTrainingIntervalOptions() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder, SchedulingMode.ONE_TIME, 0));
        return builder.sizedByteArray();
    }

    private static byte[] createDefaultTrainingConstraints() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(TrainingConstraints.createTrainingConstraints(builder, true, true, true));
        return builder.sizedByteArray();
    }
}
