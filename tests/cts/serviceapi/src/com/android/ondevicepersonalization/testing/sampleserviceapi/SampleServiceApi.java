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
    public static final String KEY_ERROR_CODE = "error_code";
    public static final String KEY_EXCEPTION_CLASS = "exception_class";
    public static final String KEY_TABLE_KEY = "table_key";
    public static final String KEY_TABLE_VALUE = "table_value";
    public static final String KEY_TABLE_VALUE_REPEAT_COUNT = "table_value_repeat_count";

    // Values of opcodes.
    public static final String OPCODE_RENDER_AND_LOG = "render_and_log";
    public static final String OPCODE_FAIL_WITH_ERROR_CODE = "fail_with_error_code";
    public static final String OPCODE_THROW_EXCEPTION = "throw_exception";
    public static final String OPCODE_WRITE_LOCAL_DATA = "write_local_data";
    public static final String OPCODE_READ_LOCAL_DATA = "read_local_data";

    private SampleServiceApi() {}
}
