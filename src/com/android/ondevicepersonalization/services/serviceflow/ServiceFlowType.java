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

import static com.android.ondevicepersonalization.services.PhFlags.KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_RENDER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS;

import com.android.ondevicepersonalization.services.FlagsFactory;

/** Collection of on-device personalization service flows. */
public enum ServiceFlowType {

    APP_REQUEST_FLOW(
            "AppRequest", OP_EXECUTE,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS)),

    RENDER_FLOW(
            "Render", OP_RENDER,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_RENDER_FLOW_DEADLINE_SECONDS)),

    WEB_TRIGGER_FLOW(
            "WebTrigger", OP_WEB_TRIGGER,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS)),

    WEB_VIEW_FLOW(
            "ComputeEventMetrics", OP_WEB_VIEW_EVENT,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS));

    final String mTaskName;
    final int mOperationCode;
    final int mExecutionTimeout;

    ServiceFlowType(String taskName, int operationCode, int executionTimeout) {
        mTaskName = taskName;
        mOperationCode = operationCode;
        mExecutionTimeout = executionTimeout;
    }

    public String getTaskName() {
        return mTaskName;
    }

    public int getOperationCode() {
        return mOperationCode;
    }

    public int getExecutionTimeout() {
        return mExecutionTimeout;
    }
}
