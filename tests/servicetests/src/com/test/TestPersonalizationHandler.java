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

package com.test;

import android.annotation.NonNull;
import android.app.ondevicepersonalization.DownloadInput;
import android.app.ondevicepersonalization.DownloadOutput;
import android.app.ondevicepersonalization.EventLogRecord;
import android.app.ondevicepersonalization.ExecuteInput;
import android.app.ondevicepersonalization.ExecuteOutput;
import android.app.ondevicepersonalization.IsolatedComputationCallback;
import android.app.ondevicepersonalization.KeyValueStore;
import android.app.ondevicepersonalization.RenderInput;
import android.app.ondevicepersonalization.RenderOutput;
import android.app.ondevicepersonalization.RenderingConfig;
import android.app.ondevicepersonalization.RequestLogRecord;
import android.app.ondevicepersonalization.WebViewEventInput;
import android.app.ondevicepersonalization.WebViewEventOutput;
import android.content.ContentValues;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class TestPersonalizationHandler implements IsolatedComputationCallback {
    public final String TAG = "TestPersonalizationHandler";
    private final KeyValueStore mRemoteData;

    TestPersonalizationHandler(KeyValueStore remoteData) {
        mRemoteData = remoteData;
    }

    @Override
    public void onDownload(DownloadInput input, Consumer<DownloadOutput> consumer) {
        try {
            Log.d(TAG, "Starting filterData.");
            Log.d(TAG, "Data: " + input.getData());

            Log.d(TAG, "Existing keyExtra: "
                    + Arrays.toString(mRemoteData.get("keyExtra")));
            Log.d(TAG, "Existing keySet: " + mRemoteData.keySet());

            List<String> keysToRetain =
                    getFilteredKeys(input.getData());
            keysToRetain.add("keyExtra");
            // Get the keys to keep from the downloaded data
            DownloadOutput result =
                    new DownloadOutput.Builder()
                            .setRetainedKeys(keysToRetain)
                            .build();
            consumer.accept(result);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred in onDownload", e);
        }
    }

    @Override public void onExecute(
            @NonNull ExecuteInput input,
            @NonNull Consumer<ExecuteOutput> consumer
    ) {
        Log.d(TAG, "onExecute() started.");
        ContentValues logData = new ContentValues();
        logData.put("id", "bid1");
        logData.put("pr", 5.0);
        ExecuteOutput result = new ExecuteOutput.Builder()
                .setRequestLogRecord(new RequestLogRecord.Builder().addRow(logData).build())
                .addRenderingConfig(
                    new RenderingConfig.Builder().addKey("bid1").build()
                )
                .build();
        consumer.accept(result);
    }

    @Override public void onRender(
            @NonNull RenderInput input,
            @NonNull Consumer<RenderOutput> consumer
    ) {
        Log.d(TAG, "onRender() started.");
        RenderOutput result =
                new RenderOutput.Builder()
                .setContent("<p>RenderResult: "
                    + String.join(",", input.getRenderingConfig().getKeys()) + "<p>")
                .build();
        consumer.accept(result);
    }

    public void onWebViewEvent(
            @NonNull WebViewEventInput input,
            @NonNull Consumer<WebViewEventOutput> consumer
    ) {
        Log.d(TAG, "onEvent() started.");
        long longValue = 0;
        if (input.getParameters() != null) {
            longValue = input.getParameters().getLong("x");
        }
        ContentValues logData = new ContentValues();
        logData.put("x", longValue);
        WebViewEventOutput result =
                new WebViewEventOutput.Builder()
                    .setEventLogRecord(
                        new EventLogRecord.Builder()
                            .setType(1)
                            .setRowIndex(0)
                            .setData(logData)
                            .build()
                    )
                    .build();
        Log.d(TAG, "onEvent() result: " + result.toString());
        consumer.accept(result);
    }

    private List<String> getFilteredKeys(Map<String, byte[]> data) {
        Set<String> filteredKeys = data.keySet();
        filteredKeys.remove("key3");
        return new ArrayList<>(filteredKeys);
    }
}
