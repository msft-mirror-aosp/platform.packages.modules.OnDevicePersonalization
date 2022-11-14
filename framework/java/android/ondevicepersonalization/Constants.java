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

package android.ondevicepersonalization;

/**
 * Constants used in the OnDevicePersonalization Module.
 *
 * @hide
 */
public class Constants {
    public static final int STATUS_INTERNAL_ERROR = 100;

    // Keys for Bundle objects passed between processes.
    public static final String
            EXTRA_LOOKUP_KEYS = "android.ondevicepersonalization.lookup_keys";
    public static final String
            EXTRA_RESULT = "android.ondevicepersonalization.result";

    // Data Access Service operations.
    public static final int DATA_ACCESS_OP_REMOTE_DATA_LOOKUP = 1;
    public static final int DATA_ACCESS_OP_REMOTE_DATA_SCAN = 2;

    private Constants() {}
}
