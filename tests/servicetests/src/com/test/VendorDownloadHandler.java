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

import android.ondevicepersonalization.DownloadHandler;
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class VendorDownloadHandler implements DownloadHandler {
    public final String TAG = "VendorDownloadHandler";

    @Override
    public void filterData(Bundle bundle, OnDevicePersonalizationContext odpContext,
            OutcomeReceiver<List<String>, Exception> odpOutcomeReceiver) {
        Log.d(TAG, "Starting filterData.");
        List<String> lookupKeys = new ArrayList<>();
        lookupKeys.add("keyExtra");
        odpContext.getRemoteData().lookup(lookupKeys, MoreExecutors.directExecutor(),
                new OutcomeReceiver<Map<String, byte[]>, Exception>() {
                    @Override
                    public void onResult(@NonNull Map<String, byte[]> result) {
                        Log.d(TAG, "OutcomeReceiver onResult: " + result.toString());
                        // TODO(b/239479120): Create this from downloaded data.
                        List<String> res = new ArrayList<>();
                        res.add("key1");
                        res.add("key2");
                        odpOutcomeReceiver.onResult(res);
                    }
                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "OutcomeReceiver onError.", e);
                        odpOutcomeReceiver.onError(e);
                    }
                });
    }
}
