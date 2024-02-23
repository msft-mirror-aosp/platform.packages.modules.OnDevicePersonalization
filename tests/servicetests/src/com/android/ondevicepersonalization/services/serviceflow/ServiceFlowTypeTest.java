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

package com.android.ondevicepersonalization.services.serviceflow;

import static android.adservices.ondevicepersonalization.Constants.OP_EXECUTE;
import static android.adservices.ondevicepersonalization.Constants.OP_RENDER;
import static android.adservices.ondevicepersonalization.Constants.OP_WEB_TRIGGER;
import static android.adservices.ondevicepersonalization.Constants.OP_WEB_VIEW_EVENT;

import static com.google.common.truth.Truth.assertThat;

import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ServiceFlowTypeTest {

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    @Test
    public void cardinalityTest() {
        assertThat(ServiceFlowType.values().length).isEqualTo(4);
    }

    @Test
    public void taskNameTest() {
        assertThat(ServiceFlowType.APP_REQUEST_FLOW.getTaskName()).isEqualTo("AppRequest");
        assertThat(ServiceFlowType.RENDER_FLOW.getTaskName()).isEqualTo("Render");
        assertThat(ServiceFlowType.WEB_TRIGGER_FLOW.getTaskName()).isEqualTo("WebTrigger");
        assertThat(ServiceFlowType.WEB_VIEW_FLOW.getTaskName())
                .isEqualTo("ComputeEventMetrics");
    }

    @Test
    public void operationCodeTest() {
        assertThat(ServiceFlowType.APP_REQUEST_FLOW.getOperationCode()).isEqualTo(OP_EXECUTE);
        assertThat(ServiceFlowType.RENDER_FLOW.getOperationCode()).isEqualTo(OP_RENDER);
        assertThat(ServiceFlowType.WEB_TRIGGER_FLOW.getOperationCode()).isEqualTo(OP_WEB_TRIGGER);
        assertThat(ServiceFlowType.WEB_VIEW_FLOW.getOperationCode()).isEqualTo(OP_WEB_VIEW_EVENT);
    }

    @Test
    public void executionTimeoutTest() {
        assertThat(ServiceFlowType.APP_REQUEST_FLOW.getExecutionTimeout())
                .isEqualTo(FlagsFactory.getFlags().getAppRequestFlowDeadlineSeconds());
        assertThat(ServiceFlowType.RENDER_FLOW.getExecutionTimeout())
                .isEqualTo(FlagsFactory.getFlags().getRenderFlowDeadlineSeconds());
        assertThat(ServiceFlowType.WEB_TRIGGER_FLOW.getExecutionTimeout())
                .isEqualTo(FlagsFactory.getFlags().getWebTriggerFlowDeadlineSeconds());
    }
}
