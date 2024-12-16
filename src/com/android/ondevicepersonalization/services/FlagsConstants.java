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

package com.android.ondevicepersonalization.services;

public final class FlagsConstants {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Killswitch keys
    public static final String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";

    public static final String KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE =
            "enable_personalization_status_override";

    public static final String KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE =
            "personalization_status_override_value";

    public static final String KEY_ISOLATED_SERVICE_DEADLINE_SECONDS =
            "isolated_service_deadline_seconds";

    public static final String KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS =
            "app_request_flow_deadline_seconds";

    public static final String KEY_RENDER_FLOW_DEADLINE_SECONDS = "render_flow_deadline_seconds";

    public static final String KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS =
            "web_view_flow_deadline_seconds";

    public static final String KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS =
            "web_trigger_flow_deadline_seconds";

    public static final String KEY_TRUSTED_PARTNER_APPS_LIST = "trusted_partner_apps_list";

    public static final String KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED =
            "shared_isolated_process_feature_enabled";

    public static final String KEY_CALLER_APP_ALLOW_LIST = "caller_app_allow_list";

    public static final String KEY_ISOLATED_SERVICE_ALLOW_LIST = "isolated_service_allow_list";

    public static final String KEY_OUTPUT_DATA_ALLOW_LIST = "output_data_allow_list";

    public static final String KEY_USER_CONTROL_CACHE_IN_MILLIS =
            "user_control_cache_duration_millis";

    public static final String KEY_ODP_ENABLE_CLIENT_ERROR_LOGGING =
            "odp_enable_client_error_logging";

    public static final String KEY_ODP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE =
            "odp_background_job_sampling_logging_rate";

    public static final String KEY_ODP_JOB_SCHEDULING_LOGGING_ENABLED =
            "odp_job_scheduling_logging_enabled";

    public static final String KEY_ODP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE =
            "odp_job_scheduling_logging_sampling_rate";

    public static final String KEY_ODP_MODULE_JOB_POLICY = "odp_module_job_policy";

    public static final String KEY_ODP_SPE_PILOT_JOB_ENABLED = "odp_spe_pilot_job_enabled";

    public static final String KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED =
            "is_art_image_loading_optimization_enabled";

    public static final String KEY_ISOLATED_SERVICE_DEBUGGING_ENABLED =
            "isolated_service_debugging_enabled";

    public static final String KEY_RESET_DATA_DELAY_SECONDS = "reset_data_delay_seconds";

    public static final String KEY_RESET_DATA_DEADLINE_SECONDS = "reset_data_deadline_seconds";

    public static final String APP_INSTALL_HISTORY_TTL = "app_install_history_ttl";
    public static final String EXECUTE_BEST_VALUE_NOISE = "noise_for_execute_best_value";

    public static final String KEY_ENABLE_AGGREGATED_ERROR_REPORTING =
            "Odp__enable_aggregated_error_reporting";

    public static final String KEY_AGGREGATED_ERROR_REPORT_TTL_DAYS =
            "Odp__aggregated_error_report_ttl_days";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_PATH =
            "Odp__aggregated_error_reporting_path";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_THRESHOLD =
            "Odp__aggregated_error_reporting_threshold";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_INTERVAL_HOURS =
            "Odp__aggregated_error_reporting_interval_hours";
    public static final String KEY_ALLOW_UNENCRYPTED_AGGREGATED_ERROR_REPORTING =
            "Odp__aggregated_error_allow_unencrypted_aggregated_error_reporting";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_HTTP_TIMEOUT_SECONDS =
            "Odp__aggregated_error_reporting_http_timeout_seconds";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_HTTP_RETRY_LIMIT =
            "Odp__aggregated_error_reporting_http_retry_limit";

    public static final String KEY_ENCRYPTION_KEY_URL = "Odp__encryption_key_download_url";

    public static final String KEY_ENCRYPTION_KEY_MAX_AGE_SECONDS =
            "Odp__encryption_key_max_age_seconds";
    public static final String MAX_INT_VALUES_LIMIT = "max_int_values_limit";

    public static final String KEY_ADSERVICES_IPC_CALL_TIMEOUT_IN_MILLIS =
            "adservices_ipc_call_timeout_in_millis";
    public static final String KEY_PLATFORM_DATA_FOR_TRAINING_ALLOWLIST =
            "platform_data_for_training_allowlist";
    public static final String KEY_PLATFORM_DATA_FOR_EXECUTE_ALLOWLIST =
            "platform_data_for_execute_allowlist";

    public static final String KEY_LOG_ISOLATED_SERVICE_ERROR_CODE_NON_AGGREGATED_ALLOWLIST =
            "log_isolated_service_error_code_non_aggregated_allowlist";

    public static final String KEY_PLUGIN_PROCESS_RUNNER_ENABLED =
            "Odp__enable_plugin_process_runner";

    public static final String KEY_IS_FEATURE_ENABLED_API_ENABLED =
            "Odp__enable_is_feature_enabled";

    public static final String KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS =
            "download_flow_deadline_seconds";

    public static final String KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS =
            "example_store_flow_deadline_seconds";
}
