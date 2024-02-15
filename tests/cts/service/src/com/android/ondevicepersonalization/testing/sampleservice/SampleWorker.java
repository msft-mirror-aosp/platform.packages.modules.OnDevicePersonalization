/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.testing.sampleservice;

import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.RenderInput;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.content.ContentValues;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.util.Log;

import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class SampleWorker implements IsolatedWorker {
    private static final String TAG = "OdpTestingSampleService";

    private static final int ERROR_SAMPLE_SERVICE_FAILED = 1;

    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    @Override public void onExecute(
            ExecuteInput input,
            OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
        PersistableBundle appParams = Objects.requireNonNull(input.getAppParams());
        if (appParams == null
                || appParams.equals(PersistableBundle.EMPTY)
                || appParams.getString(SampleServiceApi.KEY_OPCODE) == null) {
            receiver.onResult(new ExecuteOutput.Builder().build());
            return;
        }

        String op = Objects.requireNonNull(appParams.getString(SampleServiceApi.KEY_OPCODE));
        if (op.equals(SampleServiceApi.OPCODE_THROW_EXCEPTION)) {
            throw createException(appParams);
        }

        mExecutor.submit(() -> handleOnExecute(appParams, receiver));
    }

    private void handleOnExecute(
            PersistableBundle appParams,
            OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
        Log.i(TAG, "handleOnExecute()");
        ExecuteOutput result = null;
        int errorCode = ERROR_SAMPLE_SERVICE_FAILED;

        try {
            String op = Objects.requireNonNull(appParams.getString(SampleServiceApi.KEY_OPCODE));

            if (op.equals(SampleServiceApi.OPCODE_RENDER_AND_LOG)) {
                result = handleRenderAndLog(appParams);
            } else if (op.equals(SampleServiceApi.OPCODE_FAIL_WITH_ERROR_CODE)) {
                errorCode = appParams.getInt(
                        SampleServiceApi.KEY_ERROR_CODE, ERROR_SAMPLE_SERVICE_FAILED);
            }
        } catch (Exception e) {
            Log.e(TAG, "Service error", e);
        }

        if (result != null) {
            receiver.onResult(result);
        } else {
            receiver.onError(new IsolatedServiceException(ERROR_SAMPLE_SERVICE_FAILED));
        }
    }

    private RuntimeException createException(PersistableBundle appParams) {
        try {
            String exceptionClass = appParams.getString(
                    SampleServiceApi.KEY_EXCEPTION_CLASS, "IllegalStateException");
            var clazz = Class.forName(exceptionClass);
            return (RuntimeException) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error creating exception", e);
            throw new IllegalStateException(e);
        }
    }

    private ExecuteOutput handleRenderAndLog(PersistableBundle appParams) {
        var builder = new ExecuteOutput.Builder();
        String renderingConfigIdList =
                appParams.getString(SampleServiceApi.KEY_RENDERING_CONFIG_IDS);
        if (renderingConfigIdList != null && !renderingConfigIdList.isBlank()) {
            List<String> renderingConfigIds = List.of(renderingConfigIdList.split(","));
            if (!renderingConfigIds.isEmpty()) {
                builder.setRenderingConfig(
                        new RenderingConfig.Builder().setKeys(renderingConfigIds).build());
            }
        }
        // TODO(b/273826477): Support multiple rows.
        PersistableBundle logDataBundle = appParams.getPersistableBundle(
                SampleServiceApi.KEY_LOG_DATA);
        if (logDataBundle != null && !logDataBundle.isEmpty()) {
            ContentValues logData = new ContentValues();
            for (String key : logDataBundle.keySet()) {
                putObject(logData, key, logDataBundle.get(key));
            }
            if (!logData.isEmpty()) {
                builder.setRequestLogRecord(
                        new RequestLogRecord.Builder().addRow(logData).build());
            }
        }

        return builder.build();
    }

    private void putObject(ContentValues cv, String key, Object value) {
        if (value instanceof String) {
            cv.put(key, (String) value);
        } else if (value instanceof Double) {
            cv.put(key, (Double) value);
        }
    }

    @Override public void onRender(
            RenderInput input,
            OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
        Log.i(TAG, "onRender()");
        var keys = input.getRenderingConfig().getKeys();
        if (keys.size() > 0) {
            String html = "<body>" + input.getRenderingConfig().getKeys().get(0) + "</body>";
            receiver.onResult(new RenderOutput.Builder().setContent(html).build());
        } else {
            receiver.onResult(null);
        }
    }
}

