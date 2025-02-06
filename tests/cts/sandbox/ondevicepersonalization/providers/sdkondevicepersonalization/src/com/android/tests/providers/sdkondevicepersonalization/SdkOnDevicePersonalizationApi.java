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

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.app.sdksandbox.interfaces.ISdkOnDevicePersonalizationApi;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Log;

import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import java.util.concurrent.Executors;

public class SdkOnDevicePersonalizationApi extends ISdkOnDevicePersonalizationApi.Stub {
    public static final String TAG = "SdkOnDevicePersonalizationApi";
    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";

    private final OnDevicePersonalizationManager mOdpManager;

    public SdkOnDevicePersonalizationApi(Context context) {
        mOdpManager = context.getSystemService(OnDevicePersonalizationManager.class);
    }

    @Override
    public boolean matchPackageName(String packageName) {
        var receiver = new ResultReceiver<OnDevicePersonalizationManager.ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE,
                SampleServiceApi.OPCODE_CHECK_PACKAGE_NAME);
        appParams.putString(
                SampleServiceApi.KEY_EXPECTED_PACKAGE_NAME,
                packageName);
        mOdpManager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        try {
            return receiver.isSuccess();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error while calling ResultReceiver#isSuccess", e);
            return false;
        }
    }
}
