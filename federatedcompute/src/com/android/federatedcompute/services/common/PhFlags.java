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

package com.android.federatedcompute.services.common;

import static com.android.federatedcompute.services.common.FlagsConstants.DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH;
import static com.android.federatedcompute.services.common.FlagsConstants.ENABLE_ELIGIBILITY_TASK;
import static com.android.federatedcompute.services.common.FlagsConstants.EXAMPLE_ITERATOR_NEXT_TIMEOUT_SEC;
import static com.android.federatedcompute.services.common.FlagsConstants.EXAMPLE_STORE_SERVICE_CALLBACK_TIMEOUT_SEC;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_BACKGROUND_JOBS__ENABLE_SPE_ON_BACKGROUND_KEY_FETCH_JOB;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_BACKGROUND_JOBS__ENABLE_SPE_ON_FEDERATED_JOB;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_CHECKPOINT_FILE_SIZE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_ENABLE_CLIENT_ERROR_LOGGING;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_ENABLE_ENCRYPTION;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_JOB_SCHEDULING_LOGGING_ENABLED;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_MEMORY_SIZE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_MODULE_JOB_POLICY;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_RECURRENT_RESCHEDULE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_RESCHEDULE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_SPE_PILOT_JOB_ENABLED;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_TASK_LIMIT_PER_PACKAGE_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_TEMP_FILE_TTL_IN_MILLIS_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.FCP_TF_ERROR_RESCHEDULE_SECONDS_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL;
import static com.android.federatedcompute.services.common.FlagsConstants.HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.KEY_FEDERATED_COMPUTE_KILL_SWITCH;
import static com.android.federatedcompute.services.common.FlagsConstants.MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.NAMESPACE_ON_DEVICE_PERSONALIZATION;
import static com.android.federatedcompute.services.common.FlagsConstants.TASK_HISTORY_TTL_MILLIS;
import static com.android.federatedcompute.services.common.FlagsConstants.TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS;
import static com.android.federatedcompute.services.common.FlagsConstants.TRAINING_MIN_BATTERY_LEVEL;
import static com.android.federatedcompute.services.common.FlagsConstants.TRAINING_THERMAL_STATUS_TO_THROTTLE;
import static com.android.federatedcompute.services.common.FlagsConstants.TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME;
import static com.android.federatedcompute.services.common.FlagsConstants.KEY_IS_FEATURE_ENABLED_API_ENABLED;

import android.os.SystemProperties;
import android.provider.DeviceConfig;

import com.android.internal.annotations.VisibleForTesting;

/** A placeholder class for PhFlag. */
public final class PhFlags implements Flags {
    private static final PhFlags sSingleton = new PhFlags();
    // SystemProperty prefix. SystemProperty is for overriding OnDevicePersonalization Configs.
    private static final String SYSTEM_PROPERTY_PREFIX = "debug.ondevicepersonalization.";

    private PhFlags() {
    }

    /** Returns the singleton instance of the PhFlags. */
    static PhFlags getInstance() {
        return sSingleton;
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig),
        // then hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_FEDERATED_COMPUTE_KILL_SWITCH),
                DeviceConfig.getBoolean(
                        /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                        /* name= */ KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                        /* defaultValue= */ FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH));
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_PREFIX + key;
    }

    @Override
    public String getEncryptionKeyFetchUrl() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL,
                /* defaultValue= */ ENCRYPTION_KEY_FETCH_URL);
    }

    @Override
    public Boolean getEnableBackgroundEncryptionKeyFetch() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH,
                /* defaultValue= */ USE_BACKGROUND_ENCRYPTION_KEY_FETCH);
    }

    @Override
    public int getHttpRequestRetryLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME,
                /* defaultValue= */ HTTP_REQUEST_RETRY_LIMIT);
    }

    public Boolean isEncryptionEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_ENABLE_ENCRYPTION,
                /* defaultValue= */ ENCRYPTION_ENABLED);
    }

    @Override
    public long getMinSchedulingIntervalSecsForFederatedComputation() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                /* defaultValue= */ MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);
    }

    @Override
    public long getMaxSchedulingIntervalSecsForFederatedComputation() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                /* defaultValue= */ MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);
    }

    @Override
    public long getDefaultSchedulingPeriodSecs() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                /* defaultValue= */ DEFAULT_SCHEDULING_PERIOD_SECS);
    }

    @Override
    public long getMaxSchedulingPeriodSecs() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                /* defaultValue= */ MAX_SCHEDULING_PERIOD_SECS);
    }

    @Override
    public float getTransientErrorRetryDelayJitterPercent() {
        return DeviceConfig.getFloat(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME,
                /* defaultValue= */ TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT);
    }

    @Override
    public long getTransientErrorRetryDelaySecs() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME,
                /* defaultValue= */ TRANSIENT_ERROR_RETRY_DELAY_SECS);
    }

    @Override
    public int getTrainingMinBatteryLevel() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRAINING_MIN_BATTERY_LEVEL,
                /* defaultValue= */ DEFAULT_TRAINING_MIN_BATTERY_LEVEL);
    }

    @Override
    public int getThermalStatusToThrottle() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRAINING_THERMAL_STATUS_TO_THROTTLE,
                /* defaultValue= */ DEFAULT_THERMAL_STATUS_TO_THROTTLE);
    }

    @Override
    public boolean isEligibilityTaskEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ ENABLE_ELIGIBILITY_TASK,
                /* defaultValue= */ DEFAULT_ENABLE_ELIGIBILITY_TASK);
    }

    @Override
    public long getTrainingConditionCheckThrottlePeriodMillis() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS,
                /* defaultValue= */ DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS);
    }

    @Override
    public int getFcpRescheduleLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_RESCHEDULE_LIMIT_CONFIG_NAME,
                /* defaultValue= */ FCP_RESCHEDULE_LIMIT);
    }

    @Override
    public int getFcpRecurrentRescheduleLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_RECURRENT_RESCHEDULE_LIMIT_CONFIG_NAME,
                /* defaultValue= */ FCP_RECURRENT_RESCHEDULE_LIMIT);
    }

    @Override
    public long getTaskHistoryTtl() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ TASK_HISTORY_TTL_MILLIS,
                /* defaultValue= */ DEFAULT_TASK_HISTORY_TTL_MILLIS);
    }

    @Override
    public boolean getEnableClientErrorLogging() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_ENABLE_CLIENT_ERROR_LOGGING,
                /* defaultValue= */ ENABLE_CLIENT_ERROR_LOGGING);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method always return {@code true} because the underlying flag is fully launched on
     * {@code FederatedCompute} but the method cannot be removed (as it's defined on {@code
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
                /* name= */ FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE,
                /* defaultValue= */ FCP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE);
    }

    @Override
    public boolean getJobSchedulingLoggingEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_JOB_SCHEDULING_LOGGING_ENABLED,
                /* defaultValue= */ DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED);
    }

    @Override
    public int getJobSchedulingLoggingSamplingRate() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE,
                /* defaultValue= */ DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE);
    }

    @Override
    public String getFcpModuleJobPolicy() {
        return DeviceConfig.getString(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name */ FCP_MODULE_JOB_POLICY,
                /* defaultValue */ DEFAULT_FCP_MODULE_JOB_POLICY);
    }

    @Override
    public boolean getSpePilotJobEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_SPE_PILOT_JOB_ENABLED,
                /* defaultValue= */ DEFAULT_SPE_PILOT_JOB_ENABLED);
    }

    @Override
    public boolean getSpeOnBackgroundKeyFetchJobEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_BACKGROUND_JOBS__ENABLE_SPE_ON_BACKGROUND_KEY_FETCH_JOB,
                /* defaultValue= */
                DEFAULT_FCP_BACKGROUND_JOBS__ENABLE_SPE_ON_BACKGROUND_KEY_FETCH_JOB);
    }

    @Override
    public boolean getSpeOnFederatedJobEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_BACKGROUND_JOBS__ENABLE_SPE_ON_FEDERATED_JOB,
                /* defaultValue= */ DEFAULT_FCP_BACKGROUND_JOBS__ENABLE_SPE_ON_FEDERATED_JOB);
    }

    @Override
    public int getExampleStoreServiceCallbackTimeoutSec() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ EXAMPLE_STORE_SERVICE_CALLBACK_TIMEOUT_SEC,
                /* defaultValue= */ DEFAULT_EXAMPLE_STORE_SERVICE_CALLBACK_TIMEOUT_SEC);
    }

    @Override
    public long getFcpTfErrorRescheduleSeconds() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_TF_ERROR_RESCHEDULE_SECONDS_CONFIG_NAME,
                /* defaultValue= */ FCP_TF_ERROR_RESCHEDULE_SECONDS);
    }

    @Override
    public int getExampleIteratorNextTimeoutSec() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ EXAMPLE_ITERATOR_NEXT_TIMEOUT_SEC,
                /* defaultValue= */ DEFAULT_EXAMPLE_ITERATOR_NEXT_TIMEOUT_SEC);
    }

    @Override
    public long getFcpMemorySizeLimit() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_MEMORY_SIZE_LIMIT_CONFIG_NAME,
                /* defaultValue= */ FCP_DEFAULT_MEMORY_SIZE_LIMIT);
    }

    @Override
    public int getFcpTaskLimitPerPackage() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_TASK_LIMIT_PER_PACKAGE_CONFIG_NAME,
                /* defaultValue= */ DEFAULT_FCP_TASK_LIMIT_PER_PACKAGE);
    }

    @Override
    public int getFcpCheckpointFileSizeLimit() {
        return DeviceConfig.getInt(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_CHECKPOINT_FILE_SIZE_LIMIT_CONFIG_NAME,
                /* defaultValue= */ FCP_DEFAULT_CHECKPOINT_FILE_SIZE_LIMIT);
    }

    @Override
    public long getTempFileTtlMillis() {
        return DeviceConfig.getLong(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ FCP_TEMP_FILE_TTL_IN_MILLIS_NAME,
                /* defaultValue= */ DEFAULT_TEMP_FILE_TTL_MILLIS);
    }

    @Override
    public boolean isFeatureEnabledApiEnabled() {
        return DeviceConfig.getBoolean(
                /* namespace= */ NAMESPACE_ON_DEVICE_PERSONALIZATION,
                /* name= */ KEY_IS_FEATURE_ENABLED_API_ENABLED,
                /* defaultValue= */ DEFAULT_IS_FEATURE_ENABLED_API_ENABLED);
    }

}
