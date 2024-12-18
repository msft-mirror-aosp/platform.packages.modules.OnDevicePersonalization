/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.cts.e2e;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertThrows;

import android.adservices.ondevicepersonalization.InferenceInput;
import android.adservices.ondevicepersonalization.InferenceOutput;
import android.adservices.ondevicepersonalization.RemoteDataImpl;
import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class InferenceInputTest {
    private static final String MODEL_KEY = "model_key";
    private RemoteDataImpl mRemoteData;

    @Before
    public void setup() {
        mRemoteData =
                new RemoteDataImpl(
                        IDataAccessService.Stub.asInterface(new TestDataAccessService()));
    }

    @Test
    public void buildInput_success() {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput.Params params =
                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY).build();

        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                params,
                                input,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .setBatchSize(1)
                        .build();

        float[] inputData = (float[]) inferenceInput.getInputData()[0];
        assertEquals(inputData[0], 1.2f);
        assertThat(inferenceInput.getBatchSize()).isEqualTo(1);
        assertThat(inferenceInput.getExpectedOutputStructure().getDataOutputs()).hasSize(1);
        assertThat(inferenceInput.getParams()).isEqualTo(params);
    }

    @Test
    public void buildInputWithSetters() {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput.Params params =
                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY).build();

        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                params,
                                input,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .setParams(params)
                        .setInputData(input)
                        .setBatchSize(1)
                        .setExpectedOutputStructure(
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        float[] inputData = (float[]) inferenceInput.getInputData()[0];
        assertEquals(inputData[0], 1.2f);
        assertThat(inferenceInput.getBatchSize()).isEqualTo(1);
        assertThat(inferenceInput.getExpectedOutputStructure().getDataOutputs()).hasSize(1);
        assertThat(inferenceInput.getParams()).isEqualTo(params);
    }

    @Test
    public void buildInput_batchNotSet_success() {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput.Params params =
                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY).build();

        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                params,
                                input,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        assertThat(inferenceInput.getBatchSize()).isEqualTo(1);
    }

    @Test
    public void buildParams_success() {
        InferenceInput.Params params =
                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY)
                .build();

        assertThat(params.getRecommendedNumThreads()).isEqualTo(1);
        assertThat(params.getDelegateType()).isEqualTo(InferenceInput.Params.DELEGATE_CPU);
        assertThat(params.getModelType())
                .isEqualTo(InferenceInput.Params.MODEL_TYPE_TENSORFLOW_LITE);
        assertThat(params.getKeyValueStore()).isEqualTo(mRemoteData);
        assertThat(params.getModelKey()).isEqualTo(MODEL_KEY);
    }

    @Test
    public void buildParamsWithSetters() {
        InferenceInput.Params params = new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY)
                .setKeyValueStore(mRemoteData)
                .setModelKey(MODEL_KEY)
                .setDelegateType(InferenceInput.Params.DELEGATE_CPU)
                .setModelType(InferenceInput.Params.MODEL_TYPE_TENSORFLOW_LITE)
                .build();

        assertThat(params.getRecommendedNumThreads()).isEqualTo(1);
        assertThat(params.getDelegateType()).isEqualTo(InferenceInput.Params.DELEGATE_CPU);
        assertThat(params.getModelType())
                .isEqualTo(InferenceInput.Params.MODEL_TYPE_TENSORFLOW_LITE);
        assertThat(params.getKeyValueStore()).isEqualTo(mRemoteData);
        assertThat(params.getModelKey()).isEqualTo(MODEL_KEY);
    }

    @Test
    public void buildParams_negativeThread_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY)
                                .setRecommendedNumThreads(-2)
                                .build());
    }

    @Test
    public void buildParams_nullModelKey_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new InferenceInput.Params.Builder(mRemoteData, null).build());
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {}
        @Override
        public void logApiCallStats(int apiName, long latencyMillis, int responseCode) {}
    }
}
