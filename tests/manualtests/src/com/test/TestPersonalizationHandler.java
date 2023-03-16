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
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.ondevicepersonalization.PersonalizationHandler;
import android.os.OutcomeReceiver;
import android.util.Log;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class TestPersonalizationHandler implements PersonalizationHandler {
    public final String TAG = "TestPersonalizationHandler";

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
                        // Get the keys to keep from the downloaded data
                        DownloadResult downloadResult =
                                new DownloadResult.Builder()
                                .setKeysToRetain(getFilteredKeys(input.getData()))
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

    private List<String> getFilteredKeys(Map<String, byte[]> data) {
        Set<String> filteredKeys = data.keySet();
        filteredKeys.remove("key3");
        return new ArrayList<>(filteredKeys);
    }
}
