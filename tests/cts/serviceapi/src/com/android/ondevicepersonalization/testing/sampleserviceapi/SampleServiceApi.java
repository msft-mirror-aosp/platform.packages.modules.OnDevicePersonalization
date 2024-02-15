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

package com.android.ondevicepersonalization.testing.sampleserviceapi;

/**
 * Sample Service API constants.
 */
public class SampleServiceApi {
    // Keys in AppParams for SampleService in test.
    public static final String KEY_OPCODE = "opcode";
    public static final String KEY_RENDERING_CONFIG_IDS = "rendering_config_ids";
    public static final String KEY_LOG_DATA = "log_data";

    // Values of opcodes.
    public static final String OPCODE_RENDER_AND_LOG = "render-and-log";

    private SampleServiceApi() {}
}