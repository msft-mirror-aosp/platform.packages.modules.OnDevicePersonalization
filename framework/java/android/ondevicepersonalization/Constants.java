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
 * Constants used internally in the OnDevicePersonalization Module and not
 * used in public APIs.
 *
 * @hide
 */
public class Constants {
    public static final int STATUS_INTERNAL_ERROR = 100;

    // Operations implemented by personalization services.
    public static final int OP_APP_REQUEST = 1;
    public static final int OP_DOWNLOAD_FINISHED = 2;
    public static final int OP_RENDER_CONTENT = 3;
    public static final int OP_COMPUTE_EVENT_METRICS = 4;

    // Keys for Bundle objects passed between processes.
    public static final String
            EXTRA_APP_NAME = "android.ondevicepersonalization.app_name";
    public static final String
            EXTRA_APP_PARAMS = "android.ondevicepersonalization.app_params";
    public static final String
            EXTRA_BID_IDS = "android.ondevicepersonalization.bid_ids";
    public static final String
            EXTRA_DATA_ACCESS_SERVICE_BINDER =
                "android.ondevicepersonalization.data_access_service_binder";
    public static final String
            EXTRA_EVENT_METRICS_INPUT =
                "android.ondevicepersonalization.event_metrics_input";
    public static final String
            EXTRA_LOOKUP_KEYS = "android.ondevicepersonalization.lookup_keys";
    public static final String
            EXTRA_PARCEL_FD = "android.ondevicepersonalization.parcel_fd";
    public static final String
            EXTRA_RESULT = "android.ondevicepersonalization.result";
    public static final String
            EXTRA_SLOT_INFO = "android.ondevicepersonalization.slot_info";

    // Data Access Service operations.
    public static final int DATA_ACCESS_OP_REMOTE_DATA_LOOKUP = 1;
    public static final int DATA_ACCESS_OP_REMOTE_DATA_SCAN = 2;

    private Constants() {}
}
