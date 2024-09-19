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

import static android.adservices.ondevicepersonalization.Constants.OP_DOWNLOAD;
import static android.adservices.ondevicepersonalization.Constants.OP_EXECUTE;
import static android.adservices.ondevicepersonalization.Constants.OP_RENDER;
import static android.adservices.ondevicepersonalization.Constants.OP_TRAINING_EXAMPLE;
import static android.adservices.ondevicepersonalization.Constants.OP_WEB_TRIGGER;
import static android.adservices.ondevicepersonalization.Constants.OP_WEB_VIEW_EVENT;

/** Collection of on-device personalization service flows. */
public enum ServiceFlowType {

    APP_REQUEST_FLOW(
            "AppRequest", OP_EXECUTE, Priority.HIGH),

    RENDER_FLOW(
            "Render", OP_RENDER, Priority.HIGH),

    WEB_TRIGGER_FLOW(
            "WebTrigger", OP_WEB_TRIGGER, Priority.NORMAL),

    WEB_VIEW_FLOW(
            "WebView", OP_WEB_VIEW_EVENT, Priority.NORMAL),

    EXAMPLE_STORE_FLOW(
            "ExampleStore", OP_TRAINING_EXAMPLE, Priority.NORMAL),

    DOWNLOAD_FLOW(
            "DownloadJob", OP_DOWNLOAD, Priority.LOW);

    final String mTaskName;
    final int mOperationCode;
    final Priority mPriority;

    ServiceFlowType(String taskName, int operationCode, Priority priority) {
        mTaskName = taskName;
        mOperationCode = operationCode;
        mPriority = priority;
    }

    public String getTaskName() {
        return mTaskName;
    }

    public int getOperationCode() {
        return mOperationCode;
    }

    public Priority getPriority() {
        return mPriority;
    }

    public enum Priority {
        HIGH,
        NORMAL,
        LOW
    }
}
