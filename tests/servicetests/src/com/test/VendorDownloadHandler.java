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
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class VendorDownloadHandler implements DownloadHandler {
    public final String TAG = "VendorDownloadHandler";

    @Override
    public List<String> filterData(Bundle params) {
        Log.d(TAG, "Starting filterData.");
        return new ArrayList<>();
    }
}
