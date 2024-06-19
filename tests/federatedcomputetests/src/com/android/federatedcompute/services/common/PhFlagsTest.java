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

import static com.android.adservices.shared.common.flags.ModuleSharedFlags.BACKGROUND_JOB_LOGGING_ENABLED;
import static com.android.adservices.shared.common.flags.ModuleSharedFlags.DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED;
import static com.android.adservices.shared.common.flags.ModuleSharedFlags.DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_ENABLE_ELIGIBILITY_TASK;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_FCP_MODULE_JOB_POLICY;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_FCP_TASK_LIMIT_PER_PACKAGE;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_SCHEDULING_PERIOD_SECS;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_SPE_PILOT_JOB_ENABLED;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_THERMAL_STATUS_TO_THROTTLE;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS;
import static com.android.federatedcompute.services.common.Flags.DEFAULT_TRAINING_MIN_BATTERY_LEVEL;
import static com.android.federatedcompute.services.common.Flags.ENABLE_CLIENT_ERROR_LOGGING;
import static com.android.federatedcompute.services.common.Flags.ENCRYPTION_ENABLED;
import static com.android.federatedcompute.services.common.Flags.FCP_DEFAULT_CHECKPOINT_FILE_SIZE_LIMIT;
import static com.android.federatedcompute.services.common.Flags.FCP_DEFAULT_MEMORY_SIZE_LIMIT;
import static com.android.federatedcompute.services.common.Flags.FCP_RECURRENT_RESCHEDULE_LIMIT;
import static com.android.federatedcompute.services.common.Flags.FCP_RESCHEDULE_LIMIT;
import static com.android.federatedcompute.services.common.Flags.FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH;
import static com.android.federatedcompute.services.common.Flags.HTTP_REQUEST_RETRY_LIMIT;
import static com.android.federatedcompute.services.common.Flags.MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION;
import static com.android.federatedcompute.services.common.Flags.MAX_SCHEDULING_PERIOD_SECS;
import static com.android.federatedcompute.services.common.Flags.MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION;
import static com.android.federatedcompute.services.common.Flags.TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT;
import static com.android.federatedcompute.services.common.Flags.TRANSIENT_ERROR_RETRY_DELAY_SECS;
import static com.android.federatedcompute.services.common.Flags.USE_BACKGROUND_ENCRYPTION_KEY_FETCH;
import static com.android.federatedcompute.services.common.PhFlags.DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH;
import static com.android.federatedcompute.services.common.PhFlags.ENABLE_ELIGIBILITY_TASK;
import static com.android.federatedcompute.services.common.PhFlags.FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE;
import static com.android.federatedcompute.services.common.PhFlags.FCP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE;
import static com.android.federatedcompute.services.common.PhFlags.FCP_CHECKPOINT_FILE_SIZE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.FCP_ENABLE_BACKGROUND_JOBS_LOGGING;
import static com.android.federatedcompute.services.common.PhFlags.FCP_ENABLE_CLIENT_ERROR_LOGGING;
import static com.android.federatedcompute.services.common.PhFlags.FCP_ENABLE_ENCRYPTION;
import static com.android.federatedcompute.services.common.PhFlags.FCP_JOB_SCHEDULING_LOGGING_ENABLED;
import static com.android.federatedcompute.services.common.PhFlags.FCP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE;
import static com.android.federatedcompute.services.common.PhFlags.FCP_MEMORY_SIZE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.FCP_MODULE_JOB_POLICY;
import static com.android.federatedcompute.services.common.PhFlags.FCP_RECURRENT_RESCHEDULE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.FCP_RESCHEDULE_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.FCP_SPE_PILOT_JOB_ENABLED;
import static com.android.federatedcompute.services.common.PhFlags.FCP_TASK_LIMIT_PER_PACKAGE_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL;
import static com.android.federatedcompute.services.common.PhFlags.HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.KEY_FEDERATED_COMPUTE_KILL_SWITCH;
import static com.android.federatedcompute.services.common.PhFlags.MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS;
import static com.android.federatedcompute.services.common.PhFlags.TRAINING_MIN_BATTERY_LEVEL;
import static com.android.federatedcompute.services.common.PhFlags.TRAINING_THERMAL_STATUS_TO_THROTTLE;
import static com.android.federatedcompute.services.common.PhFlags.TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PhFlags} */
@RunWith(JUnit4.class)
public class PhFlagsTest {
    /** Get necessary permissions to access Setting.Config API and set up context */
    @Before
    public void setUpContext() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    /** Roll flags back to their default values. */
    @AfterClass
    public static void tearDown() {
        // roll back to default value.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME,
                Integer.toString(HTTP_REQUEST_RETRY_LIMIT),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH,
                Boolean.toString(USE_BACKGROUND_ENCRYPTION_KEY_FETCH),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_ENCRYPTION,
                Boolean.toString(ENCRYPTION_ENABLED),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                Long.toString(MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                Long.toString(MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                Long.toString(DEFAULT_SCHEDULING_PERIOD_SECS),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                Long.toString(MAX_SCHEDULING_PERIOD_SECS),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME,
                Float.toString(TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_ELIGIBILITY_TASK,
                Boolean.toString(DEFAULT_ENABLE_ELIGIBILITY_TASK),
                /* makeDefault */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_MIN_BATTERY_LEVEL,
                Integer.toString(DEFAULT_TRAINING_MIN_BATTERY_LEVEL),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_THERMAL_STATUS_TO_THROTTLE,
                Integer.toString(DEFAULT_THERMAL_STATUS_TO_THROTTLE),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS,
                Long.toString(DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_RESCHEDULE_LIMIT_CONFIG_NAME,
                Integer.toString(FCP_RESCHEDULE_LIMIT),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_RECURRENT_RESCHEDULE_LIMIT_CONFIG_NAME,
                Integer.toString(FCP_RECURRENT_RESCHEDULE_LIMIT),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_CLIENT_ERROR_LOGGING,
                Boolean.toString(ENABLE_CLIENT_ERROR_LOGGING),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_BACKGROUND_JOBS_LOGGING,
                Boolean.toString(BACKGROUND_JOB_LOGGING_ENABLED),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE,
                Integer.toString(FCP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_JOB_SCHEDULING_LOGGING_ENABLED,
                Boolean.toString(DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE,
                Integer.toString(DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_MODULE_JOB_POLICY,
                DEFAULT_FCP_MODULE_JOB_POLICY,
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_SPE_PILOT_JOB_ENABLED,
                Boolean.toString(DEFAULT_SPE_PILOT_JOB_ENABLED),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_MEMORY_SIZE_LIMIT_CONFIG_NAME,
                Long.toString(FCP_DEFAULT_MEMORY_SIZE_LIMIT),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_TASK_LIMIT_PER_PACKAGE_CONFIG_NAME,
                Integer.toString(DEFAULT_FCP_TASK_LIMIT_PER_PACKAGE),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_CHECKPOINT_FILE_SIZE_LIMIT_CONFIG_NAME,
                Integer.toString(FCP_DEFAULT_CHECKPOINT_FILE_SIZE_LIMIT),
                /* makeDefault= */ false);
    }

    @Test
    public void testGetGlobalKillSwitch() {
        // Without any overriding, the value is the hard coded constant.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                Boolean.toString(FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().getGlobalKillSwitch())
                .isEqualTo(FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH);

        // Now overriding with the value from PH.
        final boolean phOverridingValue = !FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getGlobalKillSwitch()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testGetEncryptionKeyFetchUrl() {
        // Now overriding with the value from PH.
        String overrideUrl = "https://real-coordinator/v1alpha/publicKeys";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL,
                overrideUrl,
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEncryptionKeyFetchUrl()).isEqualTo(overrideUrl);
    }

    @Test
    public void testEnableBackgroundEncryptionKeyFetch() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH,
                Boolean.toString(USE_BACKGROUND_ENCRYPTION_KEY_FETCH),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getEnableBackgroundEncryptionKeyFetch())
                .isEqualTo(USE_BACKGROUND_ENCRYPTION_KEY_FETCH);

        // Now overriding the value from PH.
        boolean overrideEnableBackgroundKeyFetch = !USE_BACKGROUND_ENCRYPTION_KEY_FETCH;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH,
                Boolean.toString(overrideEnableBackgroundKeyFetch),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getEnableBackgroundEncryptionKeyFetch())
                .isEqualTo(overrideEnableBackgroundKeyFetch);
    }

    @Test
    public void testEnableEncryption() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_ENCRYPTION,
                Boolean.toString(ENCRYPTION_ENABLED),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().isEncryptionEnabled()).isEqualTo(ENCRYPTION_ENABLED);

        // Now overriding the value from PH
        boolean overrideEnableEncryption = !ENCRYPTION_ENABLED;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_ENCRYPTION,
                Boolean.toString(overrideEnableEncryption),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isEncryptionEnabled()).isEqualTo(overrideEnableEncryption);
    }

    @Test
    public void testGetHttpRequestRetryLimit() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME,
                Integer.toString(HTTP_REQUEST_RETRY_LIMIT),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getHttpRequestRetryLimit())
                .isEqualTo(HTTP_REQUEST_RETRY_LIMIT);

        // Now overriding the value from PH.
        int overrideHttpRequestRetryLimit = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME,
                Integer.toString(overrideHttpRequestRetryLimit),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getHttpRequestRetryLimit()).isEqualTo(overrideHttpRequestRetryLimit);
    }

    @Test
    public void testEnableEligibilityTask() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_ELIGIBILITY_TASK,
                Boolean.toString(DEFAULT_ENABLE_ELIGIBILITY_TASK),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().isEligibilityTaskEnabled())
                .isEqualTo(DEFAULT_ENABLE_ELIGIBILITY_TASK);

        boolean overrideEnable = !DEFAULT_ENABLE_ELIGIBILITY_TASK;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_ELIGIBILITY_TASK,
                Boolean.toString(overrideEnable),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isEligibilityTaskEnabled()).isEqualTo(overrideEnable);
    }

    @Test
    public void testGetTrainingMinBatteryLevel() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_MIN_BATTERY_LEVEL,
                Integer.toString(DEFAULT_TRAINING_MIN_BATTERY_LEVEL),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getTrainingMinBatteryLevel())
                .isEqualTo(DEFAULT_TRAINING_MIN_BATTERY_LEVEL);

        // Now overriding the value from PH.
        int overrideValue = 50;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_MIN_BATTERY_LEVEL,
                Integer.toString(overrideValue),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTrainingMinBatteryLevel()).isEqualTo(overrideValue);
    }

    @Test
    public void testGetThermalStatusToThrottle() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_THERMAL_STATUS_TO_THROTTLE,
                Integer.toString(DEFAULT_THERMAL_STATUS_TO_THROTTLE),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getThermalStatusToThrottle())
                .isEqualTo(DEFAULT_THERMAL_STATUS_TO_THROTTLE);

        // Now overriding the value from PH.
        int overrideValue = 5;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_THERMAL_STATUS_TO_THROTTLE,
                Integer.toString(overrideValue),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getThermalStatusToThrottle()).isEqualTo(overrideValue);
    }

    @Test
    public void testGetTrainingConditionCheckThrottlePeriodMillis() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS,
                Long.toString(DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getTrainingConditionCheckThrottlePeriodMillis())
                .isEqualTo(DEFAULT_TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS);

        // Now overriding the value from PH.
        long overrideValue = 2000;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRAINING_CONDITION_CHECK_THROTTLE_PERIOD_MILLIS,
                Long.toString(overrideValue),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTrainingConditionCheckThrottlePeriodMillis())
                .isEqualTo(overrideValue);
    }

    @Test
    public void testGetMinSchedulingIntervalSecsForFederatedComputation() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                Long.toString(MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getMinSchedulingIntervalSecsForFederatedComputation())
                .isEqualTo(MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);

        // Now overriding the value from PH.
        long overrideMinSchedulingInterval =
                MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION + 60;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MIN_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                Long.toString(overrideMinSchedulingInterval),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMinSchedulingIntervalSecsForFederatedComputation())
                .isEqualTo(overrideMinSchedulingInterval);
    }

    @Test
    public void testGetMaxSchedulingIntervalSecsForFederatedComputation() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                Long.toString(MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getMaxSchedulingIntervalSecsForFederatedComputation())
                .isEqualTo(MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION);

        // Now overriding the value from PH.
        long overrideMaxSchedulingInterval =
                MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION + 60;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MAX_SCHEDULING_INTERVAL_SECS_FOR_FEDERATED_COMPUTATION_CONFIG_NAME,
                Long.toString(overrideMaxSchedulingInterval),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMaxSchedulingIntervalSecsForFederatedComputation())
                .isEqualTo(overrideMaxSchedulingInterval);
    }

    @Test
    public void testGetDefaultSchedulingPeriodSecs() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                Long.toString(DEFAULT_SCHEDULING_PERIOD_SECS),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getDefaultSchedulingPeriodSecs())
                .isEqualTo(DEFAULT_SCHEDULING_PERIOD_SECS);

        // Now overriding the value from PH.
        long overrideDefaultSchedulingPeriodSecs = DEFAULT_SCHEDULING_PERIOD_SECS + 60;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                DEFAULT_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                Long.toString(overrideDefaultSchedulingPeriodSecs),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getDefaultSchedulingPeriodSecs())
                .isEqualTo(overrideDefaultSchedulingPeriodSecs);
    }

    @Test
    public void testGetMaxSchedulingPeriodSecs() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                Long.toString(MAX_SCHEDULING_PERIOD_SECS),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getMaxSchedulingPeriodSecs())
                .isEqualTo(MAX_SCHEDULING_PERIOD_SECS);

        // Now overriding the value from PH.
        long overrideMaxSchedulingPeriodSecs = MAX_SCHEDULING_PERIOD_SECS + 60;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                MAX_SCHEDULING_PERIOD_SECS_CONFIG_NAME,
                Long.toString(overrideMaxSchedulingPeriodSecs),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getMaxSchedulingPeriodSecs()).isEqualTo(overrideMaxSchedulingPeriodSecs);
    }

    @Test
    public void testGetTransientErrorRetryDelayJitterPercent() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME,
                Float.toString(TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getTransientErrorRetryDelayJitterPercent())
                .isEqualTo(TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT);

        // Now overriding the value from PH.
        float overrideTransientErrorRetryDelayJitterPercent =
                TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT + 0.1f;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRANSIENT_ERROR_RETRY_DELAY_JITTER_PERCENT_CONFIG_NAME,
                Float.toString(overrideTransientErrorRetryDelayJitterPercent),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTransientErrorRetryDelayJitterPercent())
                .isEqualTo(overrideTransientErrorRetryDelayJitterPercent);
    }

    @Test
    public void testGetTransientErrorRetryDelaySecs() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME,
                Long.toString(TRANSIENT_ERROR_RETRY_DELAY_SECS),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getTransientErrorRetryDelaySecs())
                .isEqualTo(TRANSIENT_ERROR_RETRY_DELAY_SECS);

        // Now overriding the value from PH.
        long overrideTransientErrorRetryDelaySecs = TRANSIENT_ERROR_RETRY_DELAY_SECS + 15;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                TRANSIENT_ERROR_RETRY_DELAY_SECS_CONFIG_NAME,
                Long.toString(overrideTransientErrorRetryDelaySecs),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getTransientErrorRetryDelaySecs())
                .isEqualTo(overrideTransientErrorRetryDelaySecs);
    }

    @Test
    public void testGetFcpRescheduleLimit() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_RESCHEDULE_LIMIT_CONFIG_NAME,
                Integer.toString(FCP_RESCHEDULE_LIMIT),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getFcpRescheduleLimit()).isEqualTo(FCP_RESCHEDULE_LIMIT);

        // Now overriding the value from PH.
        int overrideFcpRescheduleLimit = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_RESCHEDULE_LIMIT_CONFIG_NAME,
                Integer.toString(overrideFcpRescheduleLimit),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFcpRescheduleLimit()).isEqualTo(overrideFcpRescheduleLimit);
    }

    @Test
    public void testGetFcpRecurrentRescheduleLimit() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_RECURRENT_RESCHEDULE_LIMIT_CONFIG_NAME,
                Integer.toString(FCP_RECURRENT_RESCHEDULE_LIMIT),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getFcpRecurrentRescheduleLimit())
                .isEqualTo(FCP_RECURRENT_RESCHEDULE_LIMIT);

        // Now overriding the value from PH.
        int overrideFcpRescheduleLimit = 4;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_RECURRENT_RESCHEDULE_LIMIT_CONFIG_NAME,
                Integer.toString(overrideFcpRescheduleLimit),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFcpRecurrentRescheduleLimit()).isEqualTo(overrideFcpRescheduleLimit);
    }

    @Test
    public void testGetEnableClientErrorLogging() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_CLIENT_ERROR_LOGGING,
                Boolean.toString(ENABLE_CLIENT_ERROR_LOGGING),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getEnableClientErrorLogging())
                .isEqualTo(ENABLE_CLIENT_ERROR_LOGGING);

        // Now overriding the value from PH.
        boolean overrideEnable = !ENABLE_CLIENT_ERROR_LOGGING;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_CLIENT_ERROR_LOGGING,
                Boolean.toString(overrideEnable),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getEnableClientErrorLogging()).isEqualTo(overrideEnable);
    }

    @Test
    public void testGetBackgroundJobsLoggingEnabled() {
        // read a stable flag value and verify it's equal to the default value.
        boolean stableValue = FlagsFactory.getFlags().getBackgroundJobsLoggingEnabled();
        assertThat(stableValue).isEqualTo(BACKGROUND_JOB_LOGGING_ENABLED);

        // Now overriding the value from PH.
        boolean overrideEnabled = !stableValue;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_BACKGROUND_JOBS_LOGGING,
                Boolean.toString(overrideEnabled),
                /* makeDefault= */ false);

        // the flag value remains stable
        assertThat(FlagsFactory.getFlags().getBackgroundJobsLoggingEnabled())
                .isEqualTo(stableValue);
    }

    @Test
    public void testGetBackgroundJobSamplingLoggingRate() {
        int defaultValue = FCP_BACKGROUND_JOB_SAMPLING_LOGGING_RATE;
        assertThat(FlagsFactory.getFlags().getBackgroundJobSamplingLoggingRate())
                .isEqualTo(defaultValue);

        // Now overriding the value from PH.
        int overrideRate = defaultValue + 1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_BACKGROUND_JOB_LOGGING_SAMPLING_RATE,
                Integer.toString(overrideRate),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getBackgroundJobSamplingLoggingRate())
                .isEqualTo(overrideRate);
    }

    @Test
    public void testGetFcpMemorySizeLimit() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_MEMORY_SIZE_LIMIT_CONFIG_NAME,
                Long.toString(FCP_DEFAULT_MEMORY_SIZE_LIMIT),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getFcpMemorySizeLimit())
                .isEqualTo(FCP_DEFAULT_MEMORY_SIZE_LIMIT);

        // Now overriding the value from PH.
        long overrideFcpMemLimit = 1000;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_MEMORY_SIZE_LIMIT_CONFIG_NAME,
                Long.toString(overrideFcpMemLimit),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFcpMemorySizeLimit()).isEqualTo(overrideFcpMemLimit);
    }

    @Test
    public void testGetFcpTaskCountPerPackage() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_TASK_LIMIT_PER_PACKAGE_CONFIG_NAME,
                Integer.toString(DEFAULT_FCP_TASK_LIMIT_PER_PACKAGE),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getFcpTaskLimitPerPackage())
                .isEqualTo(DEFAULT_FCP_TASK_LIMIT_PER_PACKAGE);

        // Now overriding the value from PH.
        int overrideFcpTaskLimit = 1000;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_TASK_LIMIT_PER_PACKAGE_CONFIG_NAME,
                Integer.toString(overrideFcpTaskLimit),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFcpTaskLimitPerPackage()).isEqualTo(overrideFcpTaskLimit);
    }

    @Test
    public void testGetFcpCheckinFileSizeLimit() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_CHECKPOINT_FILE_SIZE_LIMIT_CONFIG_NAME,
                Integer.toString(FCP_DEFAULT_CHECKPOINT_FILE_SIZE_LIMIT),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getFcpCheckpointFileSizeLimit())
                .isEqualTo(FCP_DEFAULT_CHECKPOINT_FILE_SIZE_LIMIT);

        // Now overriding the value from PH.
        int overrideFcpCheckinFileSizeLimit = 1000;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_CHECKPOINT_FILE_SIZE_LIMIT_CONFIG_NAME,
                Integer.toString(overrideFcpCheckinFileSizeLimit),
                /* makeDefault= */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getFcpCheckpointFileSizeLimit())
                .isEqualTo(overrideFcpCheckinFileSizeLimit);
    }

    @Test
    public void testGetJobSchedulingLoggingEnabled() {
        // read a stable flag value and verify it's equal to the default value.
        boolean stableValue = FlagsFactory.getFlags().getJobSchedulingLoggingEnabled();
        assertThat(stableValue).isEqualTo(DEFAULT_JOB_SCHEDULING_LOGGING_ENABLED);

        // override the value in device config.
        boolean overrideEnabled = !stableValue;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_JOB_SCHEDULING_LOGGING_ENABLED,
                Boolean.toString(overrideEnabled),
                /* makeDefault= */ false);

        // the flag value remains stable
        assertThat(FlagsFactory.getFlags().getJobSchedulingLoggingEnabled())
                .isEqualTo(overrideEnabled);
    }

    @Test
    public void testGetJobSchedulingLoggingSamplingRate() {
        int defaultValue = DEFAULT_JOB_SCHEDULING_LOGGING_SAMPLING_RATE;
        assertThat(FlagsFactory.getFlags().getJobSchedulingLoggingSamplingRate())
                .isEqualTo(defaultValue);

        // Override the value in device config.
        int overrideRate = defaultValue + 1;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_JOB_SCHEDULING_LOGGING_SAMPLING_RATE,
                Integer.toString(overrideRate),
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getJobSchedulingLoggingSamplingRate())
                .isEqualTo(overrideRate);
    }

    @Test
    public void testGetFcpModuleJobPolicy() {
        assertThat(FlagsFactory.getFlags().getFcpModuleJobPolicy())
                .isEqualTo(DEFAULT_FCP_MODULE_JOB_POLICY);

        String overrideValue = "Something";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_MODULE_JOB_POLICY,
                overrideValue,
                /* makeDefault= */ false);
        assertThat(FlagsFactory.getFlags().getFcpModuleJobPolicy()).isEqualTo(overrideValue);
    }

    @Test
    public void testGetSpePilotJobEnabled() {
        // read a stable flag value and verify it's equal to the default value.
        boolean stableValue = FlagsFactory.getFlags().getSpePilotJobEnabled();
        assertThat(stableValue).isEqualTo(DEFAULT_SPE_PILOT_JOB_ENABLED);

        // override the value in device config.
        boolean overrideEnabled = !stableValue;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_SPE_PILOT_JOB_ENABLED,
                Boolean.toString(overrideEnabled),
                /* makeDefault= */ false);

        // the flag value remains stable
        assertThat(FlagsFactory.getFlags().getSpePilotJobEnabled()).isEqualTo(overrideEnabled);
    }
}
