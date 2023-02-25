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
import android.ondevicepersonalization.DownloadInput;
import android.ondevicepersonalization.DownloadResult;
import android.ondevicepersonalization.EventMetricsInput;
import android.ondevicepersonalization.EventMetricsResult;
import android.ondevicepersonalization.Metrics;
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.ondevicepersonalization.PersonalizationService;
import android.ondevicepersonalization.RenderContentInput;
import android.ondevicepersonalization.RenderContentResult;
import android.ondevicepersonalization.ScoredBid;
import android.ondevicepersonalization.SelectContentInput;
import android.ondevicepersonalization.SelectContentResult;
import android.ondevicepersonalization.SlotResult;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.util.JsonReader;
import android.util.Log;

import com.google.common.util.concurrent.MoreExecutors;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class TestPersonalizationService extends PersonalizationService {
    public final String TAG = "TestPersonalizationService";

    @Override
    public void onDownload(DownloadInput input, OnDevicePersonalizationContext odpContext,
            Consumer<DownloadResult> consumer) {
        Log.d(TAG, "Starting filterData.");
        List<String> lookupKeys = new ArrayList<>();
        lookupKeys.add("keyExtra");
        odpContext.getRemoteData().lookup(lookupKeys, MoreExecutors.directExecutor(),
                new OutcomeReceiver<Map<String, byte[]>, Exception>() {
                    @Override
                    public void onResult(@NonNull Map<String, byte[]> result) {
                        Log.d(TAG, "OutcomeReceiver onResult: " + result);
                        List<String> keysToRetain =
                                getFilteredKeys(input.getParcelFileDescriptor());
                        keysToRetain.add("keyExtra");
                        // Get the keys to keep from the downloaded data
                        DownloadResult downloadResult =
                                new DownloadResult.Builder()
                                .setKeysToRetain(keysToRetain)
                                .build();
                        consumer.accept(downloadResult);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "OutcomeReceiver onError.", e);
                        consumer.accept(null);
                    }
                });
    }

    @Override public void selectContent(
            @NonNull SelectContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<SelectContentResult> consumer
    ) {
        Log.d(TAG, "onAppRequest() started.");
        SelectContentResult result = new SelectContentResult.Builder()
                .addSlotResults(new SlotResult.Builder()
                        .setSlotId("slot_id")
                        .addWinningBids(
                            new ScoredBid.Builder()
                            .setBidId("bid1").setPrice(5.0).setScore(1.0).build())
                        .build())
                .build();
        consumer.accept(result);
    }

    @Override public void renderContent(
            @NonNull RenderContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<RenderContentResult> consumer
    ) {
        Log.d(TAG, "renderContent() started.");
        RenderContentResult result =
                new RenderContentResult.Builder()
                .setContent("<p>RenderResult: " + String.join(",", input.getBidIds()) + "<p>")
                .build();
        consumer.accept(result);
    }

    public void computeEventMetrics(
            @NonNull EventMetricsInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<EventMetricsResult> consumer
    ) {
        int intValue = 0;
        double floatValue = 0.0;
        if (input.getEventParams() != null) {
            intValue = input.getEventParams().getInt("a");
            floatValue = input.getEventParams().getDouble("b");
        }
        EventMetricsResult result =
                new EventMetricsResult.Builder()
                    .setMetrics(
                            new Metrics.Builder()
                                .setIntValues(intValue)
                                .setFloatValues(floatValue)
                                .build())
                    .build();
        Log.d(TAG, "computeEventMetrics() result: " + result.toString());
        consumer.accept(result);
    }

    private List<String> getFilteredKeys(ParcelFileDescriptor fd) {
        List<String> filteredKeys = new ArrayList<String>();
        // Add all keys from the file into the list
        try (InputStream in =
                     new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("contents")) {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            reader.beginObject();
                            while (reader.hasNext()) {
                                String elementName = reader.nextName();
                                if (elementName.equals("key")) {
                                    filteredKeys.add(reader.nextString());
                                } else {
                                    reader.skipValue();
                                }
                            }
                            reader.endObject();
                        }
                        reader.endArray();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse downloaded data from fd");
        }
        // Just keep the first 2 keys for the test.
        return filteredKeys.subList(0, 2);
    }
}
