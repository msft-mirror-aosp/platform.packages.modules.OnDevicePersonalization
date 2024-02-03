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

package com.android.ondevicepersonalization.services.inference;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.InferenceInput;
import android.adservices.ondevicepersonalization.InferenceInputParcel;
import android.adservices.ondevicepersonalization.InferenceOutput;
import android.adservices.ondevicepersonalization.InferenceOutputParcel;
import android.adservices.ondevicepersonalization.ModelId;
import android.adservices.ondevicepersonalization.RemoteDataImpl;
import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class IsolatedModelServiceImplTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = IsolatedModelServiceImplTest.class.getSimpleName();

    private static final String MODEL_KEY = "modelKey";
    private Bundle mCallbackResult;
    private InferenceInput.Options mOptions;
    private RemoteDataImpl mRemoteData;

    @Before
    public void setup() {
        mRemoteData =
                new RemoteDataImpl(
                        IDataAccessService.Stub.asInterface(new TestDataAccessService()));
        mOptions = InferenceInput.Options.createCpuOptions(mRemoteData, MODEL_KEY, 1);
    }

    // TODO(b/323557896): add more test cases after get a fully OSS model.
    @Test
    public void runModelInference_success() throws Exception {
        // Set up inference context.
        InferenceInput inferenceInput =
                new InferenceInput.Builder()
                        .setInputData(generateInferenceInput())
                        .setOptions(mOptions)
                        .setExpectedOutputStructure(generateInferenceOutput())
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);
        callback.mLatch.await();

        assertFalse(callback.mError);
        InferenceOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, InferenceOutputParcel.class);
        Map<Integer, Object> outputs = result.getData();
        float[] output1 = (float[]) outputs.get(0);
        float[] expected1 = {4.89f};
        assertThat(output1).usingTolerance(0.1f).containsExactly(expected1).inOrder();

        float[] output2 = (float[]) outputs.get(1);
        float[] expected2 = {6.09f};
        assertThat(output2).usingTolerance(0.1f).containsExactly(expected2).inOrder();
    }

    @Test
    public void runModelInference_invalidInputFormat() throws Exception {
        // Misconfigured inputs.
        float[] input0 = {1.23f};
        float[] input1 = {2.43f};
        Object[] invalidInput = {input0, input1, input0};

        InferenceInput inferenceInput =
                new InferenceInput.Builder()
                        .setInputData(invalidInput)
                        .setOptions(mOptions)
                        .setExpectedOutputStructure(generateInferenceOutput())
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);
        callback.mLatch.await();

        assertTrue(callback.mError);
        assertThat(callback.mErrorCode).isEqualTo(Constants.STATUS_INTERNAL_ERROR);
    }

    @Test
    public void missModelId_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new InferenceInput.Builder()
                                .setInputData(generateInferenceInput())
                                // Not set model id in InferenceOption.
                                .setOptions(
                                        InferenceInput.Options.createCpuOptions(
                                                mRemoteData, null, -1))
                                .setExpectedOutputStructure(generateInferenceOutput())
                                .build());
    }

    @Test
    public void missingInputData_buildThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new InferenceInput.Builder()
                                .setOptions(mOptions)
                                .setExpectedOutputStructure(generateInferenceOutput())
                                .build());
    }

    @Test
    public void runModelInference_missingModelOutput() throws Exception {
        InferenceInput inferenceInput =
                new InferenceInput.Builder()
                        .setInputData(generateInferenceInput())
                        .setOptions(mOptions)
                        // Not set output structure in InferenceOutput.
                        .setExpectedOutputStructure(new InferenceOutput.Builder().build())
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);

        callback.mLatch.await();
        assertTrue(callback.mError);
        assertThat(callback.mErrorCode).isEqualTo(Constants.STATUS_INTERNAL_ERROR);
    }

    @Test
    public void runModelInference_missingDataAccessServiceBinder() {
        // Set up inference context.
        InferenceInput inferenceInput =
                new InferenceInput.Builder()
                        .setInputData(generateInferenceInput())
                        .setOptions(mOptions)
                        .setExpectedOutputStructure(generateInferenceOutput())
                        .build();

        Bundle bundle = new Bundle();
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        assertThrows(
                NullPointerException.class,
                () -> modelService.runInference(bundle, new TestServiceCallback()));
    }

    private Object[] generateInferenceInput() {
        float[] input0 = {1.23f};
        float[] input1 = {2.43f};
        return new Object[] {input0, input1, input0, input1};
    }

    private InferenceOutput generateInferenceOutput() {
        float[] parsedOutput0 = new float[1];
        float[] parsedOutput1 = new float[1];
        HashMap<Integer, Object> modelOutput = new HashMap<>();
        modelOutput.put(0, parsedOutput0);
        modelOutput.put(1, parsedOutput1);
        return new InferenceOutput.Builder().setData(modelOutput).build();
    }

    class TestServiceCallback extends IIsolatedModelServiceCallback.Stub {
        public boolean mError = false;
        public int mErrorCode = 0;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSuccess(Bundle result) {
            mCallbackResult = result;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mError = true;
            mErrorCode = errorCode;
            mLatch.countDown();
        }
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {
            if (operation == Constants.DATA_ACCESS_OP_GET_MODEL) {
                try {
                    ModelId modelId = params.getParcelable(Constants.EXTRA_MODEL_ID, ModelId.class);
                    sLogger.d(
                            TAG
                                    + " TestDataAccessService onRequest model id %s"
                                    + modelId.getKey());
                    assertThat(modelId.getKey()).isEqualTo(MODEL_KEY);
                    assertThat(modelId.getTableId()).isEqualTo(ModelId.TABLE_ID_REMOTE_DATA);
                    Context context = ApplicationProvider.getApplicationContext();
                    Uri modelUri =
                            Uri.parse(
                                    "android.resource://com.android.ondevicepersonalization.servicetests/raw/multi_add");
                    File modelFile = File.createTempFile("model", ".bin");
                    InputStream in = context.getContentResolver().openInputStream(modelUri);
                    Files.copy(in, modelFile.toPath(), REPLACE_EXISTING);
                    in.close();
                    ParcelFileDescriptor inputModelFd =
                            ParcelFileDescriptor.open(
                                    modelFile, ParcelFileDescriptor.MODE_READ_ONLY);

                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.EXTRA_RESULT, inputModelFd);
                    callback.onSuccess(bundle);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
