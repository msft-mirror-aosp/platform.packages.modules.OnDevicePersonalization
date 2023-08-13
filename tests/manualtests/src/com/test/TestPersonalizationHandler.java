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

import android.adservices.ondevicepersonalization.DownloadInput;
import android.adservices.ondevicepersonalization.DownloadOutput;
import android.adservices.ondevicepersonalization.IsolatedComputationCallback;
import android.adservices.ondevicepersonalization.KeyValueStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class TestPersonalizationHandler implements IsolatedComputationCallback {
    public final String TAG = "TestIsolatedComputationCallback";
    private final KeyValueStore mRemoteData;

    TestPersonalizationHandler(KeyValueStore remoteData) {
        mRemoteData = remoteData;
    }

    @Override
    public void onDownload(DownloadInput input, Consumer<DownloadOutput> consumer) {
        try {
            Log.d(TAG, "Starting filterData.");
            Log.d(TAG, "Existing keyExtra: "
                    + Arrays.toString(mRemoteData.get("keyExtra")));
            Log.d(TAG, "Existing keySet: " + mRemoteData.keySet());

            DownloadOutput result =
                    new DownloadOutput.Builder()
                            .setRetainedKeys(getFilteredKeys(input.getData()))
                            .build();
            consumer.accept(result);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred in onDownload", e);
        }
    }

    private List<String> getFilteredKeys(Map<String, byte[]> data) {
        Set<String> filteredKeys = data.keySet();
        filteredKeys.remove("key3");
        return new ArrayList<>(filteredKeys);
    }
}
