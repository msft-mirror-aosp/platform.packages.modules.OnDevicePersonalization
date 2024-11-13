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

package android.adservices.ondevicepersonalization;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.util.ByteArrayUtil;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ModelManagerTest {
    ModelManager mModelManager =
            new ModelManager(
                    IDataAccessService.Stub.asInterface(new TestDataAccessService()),
                    IIsolatedModelService.Stub.asInterface(new TestIsolatedModelService()));

    private static final String INVALID_MODEL_KEY = "invalid_key";
    private static final String MODEL_KEY = "model_key";
    private static final String MISSING_OUTPUT_KEY = "missing-output-key";
    private boolean mRunInferenceCalled = false;
    private static final int EXECUTORCH_RESULT = 10;
    private RemoteDataImpl mRemoteData;

    @Before
    public void setup() {
        mRemoteData =
                new RemoteDataImpl(
                        IDataAccessService.Stub.asInterface(new TestDataAccessService()));
    }

    @Test
    public void runInference_success() throws Exception {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput inferenceContext =
                new InferenceInput.Builder(
                                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY).build(),
                                input,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        var callback = new ResultReceiver<InferenceOutput>();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        assertTrue(callback.isSuccess());
        float[] value = (float[]) callback.getResult().getDataOutputs().get(0);
        assertEquals(value[0], 5.0f, 0.01f);
    }

    @Test
    public void runInference_error() throws Exception {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput inferenceContext =
                new InferenceInput.Builder(
                                new InferenceInput.Params.Builder(mRemoteData, INVALID_MODEL_KEY)
                                        .build(),
                                input,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        var callback = new ResultReceiver<InferenceOutput>();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        assertTrue(callback.isError());
        OnDevicePersonalizationException exception =
                (OnDevicePersonalizationException) callback.getException();
        assertThat(exception.getErrorCode())
                .isEqualTo(OnDevicePersonalizationException.ERROR_INFERENCE_MODEL_NOT_FOUND);
    }

    @Test
    public void runInference_contextNull_throw() {
        var callback = new ResultReceiver<InferenceOutput>();
        assertThrows(
                NullPointerException.class,
                () -> mModelManager.run(null, MoreExecutors.directExecutor(), callback));
    }

    @Test
    public void runInference_resultMissingInferenceOutput() throws Exception {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] inputData = new Object[1];
        inputData[0] = new float[] {1.2f};
        InferenceInput inferenceContext =
                new InferenceInput.Builder(
                                new InferenceInput.Params.Builder(mRemoteData, MISSING_OUTPUT_KEY)
                                        .build(),
                                inputData,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        var callback = new ResultReceiver<InferenceOutput>();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        OnDevicePersonalizationException exception =
                (OnDevicePersonalizationException) callback.getException();
        assertThat(exception.getErrorCode())
                .isEqualTo(OnDevicePersonalizationException.ERROR_INFERENCE_FAILED);
    }

    @Test
    public void runExecutorchInference_success() throws Exception {
        // TODO(b/376902350): update input with EValue.
        byte[] input = new byte[] {1, 2, 3};
        InferenceInput inferenceContext =
                new InferenceInput.Builder()
                        .setData(input)
                        .setParams(
                                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY)
                                        .setModelType(InferenceInput.Params.MODEL_TYPE_EXECUTORCH)
                                        .build())
                        .build();

        var callback = new ResultReceiver<InferenceOutput>();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        assertTrue(callback.isSuccess());
        int value = (int) ByteArrayUtil.deserializeObject(callback.getResult().getData());
        assertThat(value).isEqualTo(EXECUTORCH_RESULT);
    }

    @Test
    public void runLiteRTInferenceUsingByteArray_success() throws Exception {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput inferenceContext =
                new InferenceInput.Builder()
                        .setData(ByteArrayUtil.serializeObject(input))
                        .setParams(
                                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY).build())
                        .setExpectedOutputStructure(
                                new InferenceOutput.Builder()
                                        .setData(ByteArrayUtil.serializeObject(outputData))
                                        .build())
                        .build();

        var callback = new ResultReceiver<InferenceOutput>();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        assertTrue(callback.isSuccess());
        Map<Integer, Object> result =
                (Map<Integer, Object>)
                        ByteArrayUtil.deserializeObject(callback.getResult().getData());
        float[] value = (float[]) result.get(0);
        assertEquals(value[0], 5.0f, 0.01f);
    }

    class TestIsolatedModelService extends IIsolatedModelService.Stub {
        @Override
        public void runInference(Bundle params, IIsolatedModelServiceCallback callback)
                throws RemoteException {
            mRunInferenceCalled = true;
            InferenceInputParcel inputParcel =
                    params.getParcelable(
                            Constants.EXTRA_INFERENCE_INPUT, InferenceInputParcel.class);
            if (inputParcel.getModelId().getKey().equals(INVALID_MODEL_KEY)) {
                callback.onError(OnDevicePersonalizationException.ERROR_INFERENCE_MODEL_NOT_FOUND);
                return;
            }
            if (inputParcel.getModelId().getKey().equals(MISSING_OUTPUT_KEY)) {
                callback.onError(OnDevicePersonalizationException.ERROR_INFERENCE_FAILED);
                return;
            }

            Bundle bundle = new Bundle();
            if (inputParcel.getModelType() == InferenceInput.Params.MODEL_TYPE_EXECUTORCH) {
                bundle.putParcelable(
                        Constants.EXTRA_RESULT,
                        new InferenceOutputParcel(
                                new InferenceOutput.Builder()
                                        .setData(ByteArrayUtil.serializeObject(EXECUTORCH_RESULT))
                                        .build()));
            } else {
                HashMap<Integer, Object> result = new HashMap<>();
                result.put(0, new float[] {5.0f});
                bundle.putParcelable(
                        Constants.EXTRA_RESULT,
                        new InferenceOutputParcel(
                                new InferenceOutput.Builder().setDataOutputs(result).build()));
            }

            callback.onSuccess(bundle);
        }
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {}

        @Override
        public void logApiCallStats(int apiName, long latencyMillis, int responseCode) {}
    }
}
