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

import android.content.ComponentName;
import android.os.PersistableBundle;

import org.junit.Test;

public class ExecuteInIsolatedServiceRequestTest {
    private static final ComponentName COMPONENT_NAME =
            ComponentName.createRelative("com.example.service", ".Example");

    @Test
    public void buildRequestWithOption_success() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("key", "ok");

        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(COMPONENT_NAME)
                        .setAppParams(bundle)
                        .setOutputSpec(
                                ExecuteInIsolatedServiceRequest.OutputSpec.buildBestValueSpec(100))
                        .build();

        ExecuteInIsolatedServiceRequest.OutputSpec options = request.getOutputSpec();
        assertThat(options.getMaxIntValue()).isEqualTo(100);
        assertThat(options.getOutputType())
                .isEqualTo(ExecuteInIsolatedServiceRequest.OutputSpec.OUTPUT_TYPE_BEST_VALUE);
        assertThat(request.getAppParams()).isEqualTo(bundle);
        assertThat(request.getService()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void buildRequestWithoutOption_success() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("key", "ok");

        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(COMPONENT_NAME)
                        .setAppParams(bundle)
                        .build();

        ExecuteInIsolatedServiceRequest.OutputSpec options = request.getOutputSpec();
        assertThat(options).isEqualTo(ExecuteInIsolatedServiceRequest.OutputSpec.DEFAULT);
        assertThat(request.getAppParams()).isEqualTo(bundle);
        assertThat(request.getService()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void buildRequest_noParams_success() {
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(COMPONENT_NAME).build();

        assertThat(request.getService()).isEqualTo(COMPONENT_NAME);
    }
}
