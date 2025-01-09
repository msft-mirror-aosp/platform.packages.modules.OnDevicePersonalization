/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.federatedcompute.services.common;

public class FlagsConstants {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Killswitch keys
    static final String KEY_FEDERATED_COMPUTE_KILL_SWITCH = "federated_compute_kill_switch";

    // OnDevicePersonalization Namespace String from DeviceConfig class
    static final String NAMESPACE_ON_DEVICE_PERSONALIZATION = "on_device_personalization";

    static final String FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL =
            "fcp_encryption_key_download_url";

    static final String ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH =
            "enable_background_encryption_key_fetch";

    static final String HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME = "http_request_retry_limit";

    static final String FCP_ENABLE_ENCRYPTION = "fcp_enable_encryption";

    static final String MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME =
            "min_scheduling_interval_secs_for_federated_computation";

    static final String MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME =
            "max_scheduling_interval_secs_for_federated_computation";

    static final String DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME =
            "default_scheduling_period_secs";

    static final String MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME = "max_scheduling_period_secs";

    static final String TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME =
            "transient_error_retry_delay_jitter_percent";

    static final String TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME =
            "transient_error_retry_delay_secs";
    static final String TRAINING_MIN_BATTERY_LEVEL = "training_min_battery_level";
    static final String TRAINING_THERMAL_STATUS_TO_THROTTLE = "training_thermal_to_throttle";
    static final String ENABLE_ELIGIBILITY_TASK = "enable_eligibility_task";
    static final String TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS =
            "training_condition_check_period_throttle_period_mills";
    static final String TASK_HISTORY_TTL_MILLIS = "task_history_ttl_millis";

    static final String FCP_RESCHEDULE_LIMIT_CONFIG_NAME = "reschedule_limit";
    static final String FCP_RECURRENT_RESCHEDULE_LIMIT_CONFIG_NAME = "recurrent_reschedule_limit";

    static final String FCP_MEMORY_SIZE_LIMIT_CONFIG_NAME = "memory_size_limit";
    static final String FCP_TASK_LIMIT_PER_PACKAGE_CONFIG_NAME = "task_limit_per_package";
    static final String FCP_CHECKPOINT_FILE_SIZE_LIMIT_CONFIG_NAME = "checkpoint_file_size_limit";
    static final String FCP_ENABLE_CLIENT_ERROR_LOGGING = "fcp_enable_client_error_logging";
    static final String FCP_ENABLE_BACKGROUND_JOBS_LOGGING = "fcp_enable_background_jobs_logging";
    static final String FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE =
            "fcp_background_job_logging_sampling_rate";
    static final String FCP_JOB_SCHEDULING_LOGGING_ENABLED = "fcp_job_scheduling_logging_enabled";

    static final String FCP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE =
            "fcp_job_scheduling_logging_sampling_rate";
    static final String FCP_MODULE_JOB_POLICY = "fcp_module_job_policy";
    static final String FCP_SPE_PILOT_JOB_ENABLED = "fcp_spe_pilot_job_enabled";
    static final String EXAMPLE_STORE_SERVICE_CALLBACK_TIMEOUT_SEC =
            "example_store_service_timeout_sec";
    static final String FCP_TF_ERROR_RESCHEDULE_SECONDS_CONFIG_NAME = "tf_error_reschedule_seconds";
    static final String EXAMPLE_ITERATOR_NEXT_TIMEOUT_SEC = "example_iterator_next_timeout_sec";
}
