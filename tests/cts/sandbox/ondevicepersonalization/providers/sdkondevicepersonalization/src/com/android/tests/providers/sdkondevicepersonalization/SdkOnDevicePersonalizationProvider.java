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

package com.android.tests.providers.sdkondevicepersonalization;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

public class SdkOnDevicePersonalizationProvider extends SandboxedSdkProvider {

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        try {
            return new SandboxedSdk(new SdkOnDevicePersonalizationApi(getContext()));
        } catch (Exception e) {
            throw new LoadSdkException(e, new Bundle());
        }
    }

    @Override
    public View getView(
            @NonNull Context windowContext, @NonNull Bundle params, int width, int height) {
        throw new UnsupportedOperationException("View not defined");
    }
}
