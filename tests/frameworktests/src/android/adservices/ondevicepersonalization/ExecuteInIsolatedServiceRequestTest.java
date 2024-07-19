/*
 * Copyright 2024 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.os.PersistableBundle;

import org.junit.Test;

public class ExecuteInIsolatedServiceRequestTest {
    @Test
    public void buildRequestWithOption_success() {
        PersistableBundle params = new PersistableBundle();
        params.putString("key", "ok");
        ComponentName componentName =
                ComponentName.createRelative("com.example.service", ".Example");

        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder()
                        .setParams(params)
                        .setService(componentName)
                        .setOptions(
                                ExecuteInIsolatedServiceRequest.Options.buildBestValueOption(100))
                        .build();

        ExecuteInIsolatedServiceRequest.Options options = request.getOptions();
        assertThat(options.getMaxIntValue()).isEqualTo(100);
        assertThat(options.getOutputType())
                .isEqualTo(ExecuteInIsolatedServiceRequest.Options.OUTPUT_TYPE_BEST_VALUE);
        assertThat(request.getParams()).isEqualTo(params);
        assertThat(request.getService()).isEqualTo(componentName);
    }

    @Test
    public void buildRequestWithoutOption_success() {
        PersistableBundle params = new PersistableBundle();
        params.putString("key", "ok");
        ComponentName componentName =
                ComponentName.createRelative("com.example.service", ".Example");

        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder()
                        .setParams(params)
                        .setService(componentName)
                        .build();

        ExecuteInIsolatedServiceRequest.Options options = request.getOptions();
        assertThat(options).isNull();
        assertThat(request.getParams()).isEqualTo(params);
        assertThat(request.getService()).isEqualTo(componentName);
    }

    @Test
    public void buildRequest_paramsMissing() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ExecuteInIsolatedServiceRequest.Builder()
                                .setService(
                                        ComponentName.createRelative(
                                                "com.example.service", ".Example"))
                                .build());
    }

    @Test
    public void buildRequest_componentNameMissing() {
        PersistableBundle params = new PersistableBundle();
        params.putString("key", "ok");
        assertThrows(
                NullPointerException.class,
                () -> new ExecuteInIsolatedServiceRequest.Builder().setParams(params).build());
    }
}
