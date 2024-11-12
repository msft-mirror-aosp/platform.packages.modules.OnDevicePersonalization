/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.provider.DeviceConfig;

import com.android.modules.utils.build.SdkLevel;

import java.util.HashMap;
import java.util.Map;

/** Flags Implementation that delegates to DeviceConfig. */
public final class PhFlags implements Flags {
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

    public static final String KEY_RENDER_FLOW_DEADLINE_SECONDS =
            "render_flow_deadline_seconds";

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
            "enable_aggregated_error_reporting";

    public static final String KEY_AGGREGATED_ERROR_REPORT_TTL_DAYS =
            "aggregated_error_report_ttl_days";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_PATH =
            "aggregated_error_reporting_path";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_THRESHOLD =
            "aggregated_error_reporting_threshold";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_INTERVAL_HOURS =
            "aggregated_error_reporting_interval_hours";
    public static final String KEY_ALLOW_UNENCRYPTED_AGGREGATED_ERROR_REPORTING =
            "aggregated_error_allow_unencrypted_aggregated_error_reporting";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_HTTP_TIMEOUT_SECONDS =
            "aggregated_error_reporting_http_timeout_seconds";

    public static final String KEY_AGGREGATED_ERROR_REPORTING_HTTP_RETRY_LIMIT =
            "aggregated_error_reporting_http_retry_limit";

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

    // OnDevicePersonalization Namespace String from DeviceConfig class
    public static final String NAMESPACE_ON_DEVICE_PERSONALIZATION = "on_device_personalization";

    private final Map<String, Object> mStableFlags = new HashMap<>();

    PhFlags() {}

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return PhFlagsLazyInstanceHolder.sSingleton;
    }

    private static class PhFlagsLazyInstanceHolder {
        private static final PhFlags sSingleton = new PhFlags();
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: PH (DeviceConfig), then hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_GLOBAL_KILL_SWITCH,
                /* defaultValue= */ GLOBAL_KILL_SWITCH);
    }

    @Override
    public boolean isPersonalizationStatusOverrideEnabled() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        // The priority of applying the flag values: PH (DeviceConfig), then user hard-coded value.
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                /* defaultValue= */ ENABLE_PERSONALIZATION_STATUS_OVERRIDE);
    }

    @Override
    public boolean getPersonalizationStatusOverrideValue() {
        if (getGlobalKillSwitch()) {
            return false;
        }
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                /* defaultValue= */ PERSONALIZATION_STATUS_OVERRIDE_VALUE);
    }

    @Override
    public int getIsolatedServiceDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ISOLATED_SERVICE_DEADLINE_SECONDS,
                /* defaultValue= */ ISOLATED_SERVICE_DEADLINE_SECONDS);
    }

    @Override
    public int getAppRequestFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ APP_REQUEST_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public int getRenderFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_RENDER_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ RENDER_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public int getWebViewFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ WEB_VIEW_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public int getWebTriggerFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ WEB_TRIGGER_FLOW_DEADLINE_SECONDS);
    }

    public static final String KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS =
            "example_store_flow_deadline_seconds";

    @Override
    public int getExampleStoreFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ EXAMPLE_STORE_FLOW_DEADLINE_SECONDS);
    }

    public static final String KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS =
            "download_flow_deadline_seconds";

    @Override
    public int getDownloadFlowDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS,
                /* defaultValue= */ DOWNLOAD_FLOW_DEADLINE_SECONDS);
    }

    @Override
    public String getTrustedPartnerAppsList() {
        return SdkLevel.isAtLeastU()
                ? DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_TRUSTED_PARTNER_APPS_LIST,
                /* defaultValue */ DEFAULT_TRUSTED_PARTNER_APPS_LIST)
                : "";
    }

    @Override
    public boolean isSharedIsolatedProcessFeatureEnabled() {
        return SdkLevel.isAtLeastU() && DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                /* defaultValue= */ DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED);
    }

    @Override
    public boolean isIsolatedServiceDebuggingEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ISOLATED_SERVICE_DEBUGGING_ENABLED,
                /* defaultValue= */ DEFAULT_ISOLATED_SERVICE_DEBUGGING_ENABLED);
    }

    @Override
    public String getCallerAppAllowList() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_CALLER_APP_ALLOW_LIST,
                /* defaultValue= */ DEFAULT_CALLER_APP_ALLOW_LIST);
    }

    @Override
    public String getIsolatedServiceAllowList() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ISOLATED_SERVICE_ALLOW_LIST,
                /* defaultValue= */ DEFAULT_ISOLATED_SERVICE_ALLOW_LIST);
    }

    @Override
    public String getOutputDataAllowList() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name */ KEY_OUTPUT_DATA_ALLOW_LIST,
                /* defaultValue */ DEFAULT_OUTPUT_DATA_ALLOW_LIST);
    }

    @Override
    public long getUserControlCacheInMillis() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_USER_CONTROL_CACHE_IN_MILLIS,
                /* defaultValue= */ USER_CONTROL_CACHE_IN_MILLIS);
    }

    @Override
    public boolean getEnableClientErrorLogging() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ODP_ENABLE_CLIENT_ERROR_LOGGING,
                /* defaultValue= */ DEFAULT_CLIENT_ERROR_LOGGING_ENABLED);
    }


    /**
     * {@inheritDoc}
     *
     * <p>This method always return {@code true} because the underlying flag is fully launched on
     * {@code OnDevicePersonalization} but the method cannot be removed (as it's defined on {@code
     * ModuleSharedFlags}).
     */
    @Override
    public boolean getBackgroundJobsLoggingEnabled() {
        return true;
    }

    @Override
    public int getBackgroundJobSamplingLoggingRate() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ODP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE,
                /* defaultValue= */ BACKGROUND_JOB_SAMPLING_LOGGING_RATE);
    }

    @Override
    public boolean getJobSchedulingLoggingEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ODP_JOB_SCHEDULING_LOGGING_ENABLED,
                /* defaultValue= */ DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED);
    }

    @Override
    public int getJobSchedulingLoggingSamplingRate() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ODP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE,
                /* defaultValue= */ DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE);
    }

    @Override
    public String getOdpModuleJobPolicy() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name */ KEY_ODP_MODULE_JOB_POLICY,
                /* defaultValue */ DEFAULT_ODP_MODULE_JOB_POLICY);
    }

    @Override
    public boolean getSpePilotJobEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ODP_SPE_PILOT_JOB_ENABLED,
                /* defaultValue= */ DEFAULT_SPE_PILOT_JOB_ENABLED);
    }

    @Override
    public boolean isArtImageLoadingOptimizationEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED,
                /* defaultValue= */ IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED);
    }

    @Override
    public int getResetDataDelaySeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_RESET_DATA_DELAY_SECONDS,
                /* defaultValue= */ DEFAULT_RESET_DATA_DELAY_SECONDS);
    }

    @Override
    public int getResetDataDeadlineSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_RESET_DATA_DEADLINE_SECONDS,
                /* defaultValue= */ DEFAULT_RESET_DATA_DEADLINE_SECONDS);
    }

    @Override
    public long getAppInstallHistoryTtlInMillis() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ APP_INSTALL_HISTORY_TTL,
                /* defaultValue= */ DEFAULT_APP_INSTALL_HISTORY_TTL_MILLIS);
    }

    @Override
    public float getNoiseForExecuteBestValue() {
        return DeviceConfig.getFloat(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ EXECUTE_BEST_VALUE_NOISE,
                /* defaultValue= */ DEFAULT_EXECUTE_BEST_VALUE_NOISE);
    }

    @Override
    public boolean getAggregatedErrorReportingEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ENABLE_AGGREGATED_ERROR_REPORTING,
                /* defaultValue= */ DEFAULT_AGGREGATED_ERROR_REPORTING_ENABLED);
    }

    @Override
    public int getAggregatedErrorReportingTtlInDays() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_AGGREGATED_ERROR_REPORT_TTL_DAYS,
                /* defaultValue= */ DEFAULT_AGGREGATED_ERROR_REPORT_TTL_DAYS);
    }

    @Override
    public String getAggregatedErrorReportingServerPath() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_AGGREGATED_ERROR_REPORTING_PATH,
                /* defaultValue= */ DEFAULT_AGGREGATED_ERROR_REPORTING_URL_PATH);
    }

    @Override
    public int getAggregatedErrorMinThreshold() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_AGGREGATED_ERROR_REPORTING_THRESHOLD,
                /* defaultValue= */ DEFAULT_AGGREGATED_ERROR_REPORTING_THRESHOLD);
    }

    @Override
    public int getAggregatedErrorReportingIntervalInHours() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_AGGREGATED_ERROR_REPORTING_INTERVAL_HOURS,
                /* defaultValue= */ DEFAULT_AGGREGATED_ERROR_REPORTING_INTERVAL_HOURS);
    }

    @Override
    public boolean getAllowUnencryptedAggregatedErrorReportingPayload() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ALLOW_UNENCRYPTED_AGGREGATED_ERROR_REPORTING,
                /* defaultValue= */ DEFAULT_ALLOW_UNENCRYPTED_AGGREGATED_ERROR_REPORTING_PAYLOAD);
    }

    @Override
    public int getAggregatedErrorReportingHttpTimeoutSeconds() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_AGGREGATED_ERROR_REPORTING_HTTP_TIMEOUT_SECONDS,
                /* defaultValue= */ DEFAULT_AGGREGATED_ERROR_REPORT_HTTP_TIMEOUT_SECONDS);
    }

    @Override
    public int getAggregatedErrorReportingHttpRetryLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_AGGREGATED_ERROR_REPORTING_HTTP_RETRY_LIMIT,
                /* defaultValue= */ DEFAULT_AGGREGATED_ERROR_REPORT_HTTP_RETRY_LIMIT);
    }

    @Override
    public String getEncryptionKeyFetchUrl() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ENCRYPTION_KEY_URL,
                /* defaultValue= */ DEFAULT_ENCRYPTION_KEY_URL);
    }

    @Override
    public long getEncryptionKeyMaxAgeSeconds() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ENCRYPTION_KEY_MAX_AGE_SECONDS,
                /* defaultValue= */ DEFAULT_ENCRYPTION_KEY_MAX_AGE_SECONDS);
    }

    @Override
    public int getMaxIntValuesLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ MAX_INT_VALUES_LIMIT,
                /* defaultValue= */ DEFAULT_MAX_INT_VALUES);
    }

    @Override
    public long getAdservicesIpcCallTimeoutInMillis() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_ADSERVICES_IPC_CALL_TIMEOUT_IN_MILLIS,
                /* defaultValue= */ DEFAULT_ADSERVICES_IPC_CALL_TIMEOUT_IN_MILLIS);
    }

    @Override
    public String getPlatformDataForTrainingAllowlist() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_PLATFORM_DATA_FOR_TRAINING_ALLOWLIST,
                /* defaultValue= */ DEFAULT_PLATFORM_DATA_FOR_TRAINING_ALLOWLIST);
    }

    @Override
    public String getDefaultPlatformDataForExecuteAllowlist() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_PLATFORM_DATA_FOR_EXECUTE_ALLOWLIST,
                /* defaultValue= */ DEFAULT_PLATFORM_DATA_FOR_EXECUTE_ALLOWLIST);
    }

    @Override
    public String getLogIsolatedServiceErrorCodeNonAggregatedAllowlist() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_LOG_ISOLATED_SERVICE_ERROR_CODE_NON_AGGREGATED_ALLOWLIST,
                /* defaultValue= */
                DEFAULT_LOG_ISOLATED_SERVICE_ERROR_CODE_NON_AGGREGATED_ALLOWLIST);
    }

    @Override
    public boolean isPluginProcessRunnerEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_PLUGIN_PROCESS_RUNNER_ENABLED,
                /* defaultValue= */ DEFAULT_PLUGIN_PROCESS_RUNNER_ENABLED);
    }

    @Override
    public boolean isFeatureEnabledApiEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_IS_FEATURE_ENABLED_API_ENABLED,
                /* defaultValue= */ DEFAULT_IS_FEATURE_ENABLED_API_ENABLED);
    }
}
