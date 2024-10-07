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

import android.adservices.ondevicepersonalization.ExecuteInIsolatedServiceRequest;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;

import com.android.adservices.shared.common.flags.ConfigFlag;
import com.android.adservices.shared.common.flags.FeatureFlag;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;

/**
 * OnDevicePersonalization Feature Flags interface. This Flags interface hold the default values
 * of flags. The default values in this class must match with the default values in PH since we
 * will migrate to Flag Codegen in the future. With that migration, the Flags.java file will be
 * generated from the GCL.
 */
public interface Flags extends ModuleSharedFlags {
    /**
     * Global OnDevicePersonalization Kill Switch. This overrides all other killswitches.
     * The default value is true which means OnDevicePersonalization is disabled.
     * This flag is used for ramp-up and emergency turning off the whole module.
     */
    boolean GLOBAL_KILL_SWITCH = true;

    /**
     * P/H flag to override the personalization status for end-to-end tests.
     * The default value is false, which means UserPrivacyStatus#personalizationStatus is not
     * override by PERSONALIZATION_STATUS_OVERRIDE_VALUE. If true, returns the personalization
     * status in PERSONALIZATION_STATUS_OVERRIDE_VALUE.
     */
    boolean ENABLE_PERSONALIZATION_STATUS_OVERRIDE = false;

    /**
     * Value of the personalization status, if ENABLE_PERSONALIZATION_STATUS_OVERRIDE is true.
     */
    boolean PERSONALIZATION_STATUS_OVERRIDE_VALUE = false;

    /**
     * Deadline for calls from ODP to isolated services.
     */
    int ISOLATED_SERVICE_DEADLINE_SECONDS = 30;

    /**
     * Execution deadline for app request flow.
     */
    int APP_REQUEST_FLOW_DEADLINE_SECONDS = 30;

    /**
     * Executiton deadline for render flow.
     */
    int RENDER_FLOW_DEADLINE_SECONDS = 30;

    /**
     * Executiton deadline for web view flow.
     */
    int WEB_VIEW_FLOW_DEADLINE_SECONDS = 30;

    /**
     * Executiton deadline for web trigger flow.
     */
    int WEB_TRIGGER_FLOW_DEADLINE_SECONDS = 30;

    /** Default value for the list of trusted partner app names. */
    String DEFAULT_TRUSTED_PARTNER_APPS_LIST = "";

    /** Default value for the shared isolated process feature. */
    boolean DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED = true;

    /** Default value for enabling client error logging. */
    boolean DEFAULT_CLIENT_ERROR_LOGGING_ENABLED = false;

    /** Default value for the base64 encoded Job Policy proto for ODP background jobs. */
    @ConfigFlag String DEFAULT_ODP_MODULE_JOB_POLICY = "";

    /** Default value for SPE to be enabled for the pilot background jobs. */
    @FeatureFlag boolean DEFAULT_SPE_PILOT_JOB_ENABLED = false;

    /** Default value for isolated service debugging flag. */
    boolean DEFAULT_ISOLATED_SERVICE_DEBUGGING_ENABLED = false;

    /** Default delay before starting a data reset. */
    int DEFAULT_RESET_DATA_DELAY_SECONDS = 24 * 60 * 60; // 24 hours

    /** Default deadline for data reset. */
    int DEFAULT_RESET_DATA_DEADLINE_SECONDS = 30 * 60 * 60; // 30 hours

    String DEFAULT_CALLER_APP_ALLOW_LIST =
            "android.ondevicepersonalization,"
                    + "android.ondevicepersonalization.test.scenario,"
                    + "com.android.federatedcompute.services,"
                    + "com.android.libraries.pcc.chronicle.test,"
                    + "com.android.ondevicepersonalization,"
                    + "com.android.ondevicepersonalization.cts.e2e,"
                    + "com.android.ondevicepersonalization.federatedcomputetests,"
                    + "com.android.ondevicepersonalization.libraries.plugin,"
                    + "com.android.ondevicepersonalization.manualtests,"
                    + "com.android.ondevicepersonalization.plugintests,"
                    + "com.android.ondevicepersonalization.services,"
                    + "com.android.ondevicepersonalization.servicetests,"
                    + "com.android.ondevicepersonalization.systemserviceapitests,"
                    + "com.android.ondevicepersonalization.systemserviceimpltests,"
                    + "com.android.ondevicepersonalization.testing.sampleservice,"
                    + "com.example.odpclient,"
                    + "com.example.odpsamplenetwork,"
                    + "com.example.odptargetingapp1,"
                    + "com.example.odptargetingapp2";

    String DEFAULT_ISOLATED_SERVICE_ALLOW_LIST =
            "android.ondevicepersonalization,"
                    + "android.ondevicepersonalization.test.scenario,"
                    + "com.android.federatedcompute.services,"
                    + "com.android.libraries.pcc.chronicle.test,"
                    + "com.android.ondevicepersonalization,"
                    + "com.android.ondevicepersonalization.cts.e2e,"
                    + "com.android.ondevicepersonalization.federatedcomputetests,"
                    + "com.android.ondevicepersonalization.libraries.plugin,"
                    + "com.android.ondevicepersonalization.manualtests,"
                    + "com.android.ondevicepersonalization.plugintests,"
                    + "com.android.ondevicepersonalization.services,"
                    + "com.android.ondevicepersonalization.servicetests,"
                    + "com.android.ondevicepersonalization.systemserviceapitests,"
                    + "com.android.ondevicepersonalization.systemserviceimpltests,"
                    + "com.android.ondevicepersonalization.testing.sampleservice,"
                    + "com.example.odpclient,"
                    + "com.example.odpsamplenetwork,"
                    + "com.example.odptargetingapp1,"
                    + "com.example.odptargetingapp2";

    String DEFAULT_OUTPUT_DATA_ALLOW_LIST = "";

    /**
     * Default value of valid duration of user control cache in milliseconds (24 hours).
     */
    long USER_CONTROL_CACHE_IN_MILLIS = 86400000;

    default boolean getGlobalKillSwitch() {
        return GLOBAL_KILL_SWITCH;
    }

    default boolean isPersonalizationStatusOverrideEnabled() {
        return ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
    }

    default boolean getPersonalizationStatusOverrideValue() {
        return PERSONALIZATION_STATUS_OVERRIDE_VALUE;
    }

    default int getIsolatedServiceDeadlineSeconds() {
        return ISOLATED_SERVICE_DEADLINE_SECONDS;
    }

    default int getAppRequestFlowDeadlineSeconds() {
        return APP_REQUEST_FLOW_DEADLINE_SECONDS;
    }

    default int getRenderFlowDeadlineSeconds() {
        return RENDER_FLOW_DEADLINE_SECONDS;
    }

    default int getWebViewFlowDeadlineSeconds() {
        return WEB_VIEW_FLOW_DEADLINE_SECONDS;
    }

    default int getWebTriggerFlowDeadlineSeconds() {
        return WEB_TRIGGER_FLOW_DEADLINE_SECONDS;
    }

    /** Execution deadline for example store flow. */
    int EXAMPLE_STORE_FLOW_DEADLINE_SECONDS = 30;

    default int getExampleStoreFlowDeadlineSeconds() {
        return EXAMPLE_STORE_FLOW_DEADLINE_SECONDS;
    }

    /** Execution deadline for download flow. */
    int DOWNLOAD_FLOW_DEADLINE_SECONDS = 30;

    default int getDownloadFlowDeadlineSeconds() {
        return DOWNLOAD_FLOW_DEADLINE_SECONDS;
    }

    default String getTrustedPartnerAppsList() {
        return DEFAULT_TRUSTED_PARTNER_APPS_LIST;
    }

    default boolean isSharedIsolatedProcessFeatureEnabled() {
        return DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
    }

    /**
     * The ART image loading optimization is disabled by default.
     */
    boolean IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED = false;

    default boolean isArtImageLoadingOptimizationEnabled() {
        return IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
    }

    default String getCallerAppAllowList() {
        return DEFAULT_CALLER_APP_ALLOW_LIST;
    }

    default String getIsolatedServiceAllowList() {
        return DEFAULT_ISOLATED_SERVICE_ALLOW_LIST;
    }

    default long getUserControlCacheInMillis() {
        return USER_CONTROL_CACHE_IN_MILLIS;
    }

    default String getOutputDataAllowList() {
        return DEFAULT_OUTPUT_DATA_ALLOW_LIST;
    }

    default boolean isIsolatedServiceDebuggingEnabled() {
        return DEFAULT_ISOLATED_SERVICE_DEBUGGING_ENABLED;
    }

    default String getOdpModuleJobPolicy() {
        return DEFAULT_ODP_MODULE_JOB_POLICY;
    }

    default boolean getSpePilotJobEnabled() {
        return DEFAULT_SPE_PILOT_JOB_ENABLED;
    }

    default boolean getEnableClientErrorLogging() {
        return DEFAULT_CLIENT_ERROR_LOGGING_ENABLED;
    }

    default int getResetDataDelaySeconds() {
        return DEFAULT_RESET_DATA_DELAY_SECONDS;
    }

    default int getResetDataDeadlineSeconds() {
        return DEFAULT_RESET_DATA_DEADLINE_SECONDS;
    }

    // Keep app install in last 30 days.
    long DEFAULT_APP_INSTALL_HISTORY_TTL_MILLIS = 30 * 24 * 60 * 60 * 1000L;

    default long getAppInstallHistoryTtlInMillis() {
        return DEFAULT_APP_INSTALL_HISTORY_TTL_MILLIS;
    }

    /**
     * The probability that we will return a random integer for {@link
     * OnDevicePersonalizationManager#executeInIsolatedService}.
     */
    float DEFAULT_EXECUTE_BEST_VALUE_NOISE = 0.1f;

    default float getNoiseForExecuteBestValue() {
        return DEFAULT_EXECUTE_BEST_VALUE_NOISE;
    }

    /** Default value for flag that enables aggregated error code reporting. */
    boolean DEFAULT_AGGREGATED_ERROR_REPORTING_ENABLED = false;

    default boolean getAggregatedErrorReportingEnabled() {
        return DEFAULT_AGGREGATED_ERROR_REPORTING_ENABLED;
    }

    int DEFAULT_AGGREGATED_ERROR_REPORT_TTL_DAYS = 30;

    /**
     * TTL for aggregate counts after which they will be deleted without waiting for a successful
     * upload attempt.
     */
    default int getAggregatedErrorReportingTtlInDays() {
        return DEFAULT_AGGREGATED_ERROR_REPORT_TTL_DAYS;
    }

    String DEFAULT_AGGREGATED_ERROR_REPORTING_URL_PATH =
            "/debugreporting/v1/exceptions:report-exceptions";

    /**
     * URL suffix that the reporting job will use to send adopters daily aggregated counts of {@link
     * android.adservices.ondevicepersonalization.IsolatedServiceException}s.
     */
    default String getAggregatedErrorReportingServerPath() {
        return DEFAULT_AGGREGATED_ERROR_REPORTING_URL_PATH;
    }

    int DEFAULT_AGGREGATED_ERROR_REPORTING_THRESHOLD = 0;

    /**
     * Minimum threshold for counts of {@link
     * android.adservices.ondevicepersonalization.IsolatedServiceException} below which counts from
     * device won't be reported.
     *
     * <p>This is applied per error code.
     */
    default int getAggregatedErrorMinThreshold() {
        return DEFAULT_AGGREGATED_ERROR_REPORTING_THRESHOLD;
    }

    int DEFAULT_AGGREGATED_ERROR_REPORTING_INTERVAL_HOURS = 24;

    /**
     * Interval for the periodic runs of the {@link
     * com.android.ondevicepersonalization.services.data.errors.AggregateErrorDataReportingService}
     * that reports counts of {@link android.adservices.ondevicepersonalization.IsolatedService}.
     */
    default int getAggregatedErrorReportingIntervalInHours() {
        return DEFAULT_AGGREGATED_ERROR_REPORTING_INTERVAL_HOURS;
    }

    boolean DEFAULT_AGGREGATED_ERROR_REPORTING_ENCRYPTION = false;

    default boolean getAggregatedErrorReportingEncryptionEnabled() {
        return DEFAULT_AGGREGATED_ERROR_REPORTING_ENCRYPTION;
    }

    int DEFAULT_AGGREGATED_ERROR_REPORT_HTTP_TIMEOUT_SECONDS = 30;

    /** Timeout for http reporting of aggregated error data. */
    default int getAggregatedErrorReportingHttpTimeoutSeconds() {
        return DEFAULT_AGGREGATED_ERROR_REPORT_HTTP_TIMEOUT_SECONDS;
    }

    int DEFAULT_AGGREGATED_ERROR_REPORT_HTTP_RETRY_LIMIT = 3;

    /** Timeout for http reporting of aggregated error data. */
    default int getAggregatedErrorReportingHttpRetryLimit() {
        return DEFAULT_AGGREGATED_ERROR_REPORT_HTTP_RETRY_LIMIT;
    }

    /**
     * Default value for maximum int value caller can set in {@link
     * ExecuteInIsolatedServiceRequest.OutputSpec#buildBestValueSpec}.
     */
    int DEFAULT_MAX_INT_VALUES = 100;

    default int getMaxIntValuesLimit() {
        return DEFAULT_MAX_INT_VALUES;
    }

    /**
     * Default max wait time until timeout for AdServices IPC call
     */
    long DEFAULT_ADSERVICES_IPC_CALL_TIMEOUT_IN_MILLIS = 5000L;

    default long getAdservicesIpcCallTimeoutInMillis() {
        return DEFAULT_ADSERVICES_IPC_CALL_TIMEOUT_IN_MILLIS;
    }

    String DEFAULT_PLATFORM_DATA_FOR_TRAINING_ALLOWLIST = "";

    default String getPlatformDataForTrainingAllowlist() {
        return DEFAULT_PLATFORM_DATA_FOR_TRAINING_ALLOWLIST;
    }

    String DEFAULT_PLATFORM_DATA_FOR_EXECUTE_ALLOWLIST = "";

    default String getDefaultPlatformDataForExecuteAllowlist() {
        return DEFAULT_PLATFORM_DATA_FOR_EXECUTE_ALLOWLIST;
    }

    String DEFAULT_LOG_ISOLATED_SERVICE_ERROR_CODE_NON_AGGREGATED_ALLOWLIST = "";

    default String getLogIsolatedServiceErrorCodeNonAggregatedAllowlist() {
        return DEFAULT_LOG_ISOLATED_SERVICE_ERROR_CODE_NON_AGGREGATED_ALLOWLIST;
    }
}
