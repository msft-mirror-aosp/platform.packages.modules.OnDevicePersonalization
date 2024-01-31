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

import static com.android.federatedcompute.services.common.Flags.AUTHENTICATION_ENABLED;
import static com.android.federatedcompute.services.common.Flags.ENCRYPTION_ENABLED;
import static com.android.federatedcompute.services.common.Flags.FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH;
import static com.android.federatedcompute.services.common.Flags.HTTP_REQUEST_RETRY_LIMIT;
import static com.android.federatedcompute.services.common.Flags.USE_BACKGROUND_ENCRYPTION_KEY_FETCH;
import static com.android.federatedcompute.services.common.PhFlags.ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH;
import static com.android.federatedcompute.services.common.PhFlags.FCP_ENABLE_AUTHENTICATION;
import static com.android.federatedcompute.services.common.PhFlags.FCP_ENABLE_ENCRYPTION;
import static com.android.federatedcompute.services.common.PhFlags.FEDERATED_COMPUTATION_ENCRYPTION_KEY_DOWNLOAD_URL;
import static com.android.federatedcompute.services.common.PhFlags.HTTP_REQUEST_RETRY_LIMIT_CONFIG_NAME;
import static com.android.federatedcompute.services.common.PhFlags.KEY_FEDERATED_COMPUTE_KILL_SWITCH;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.android.ondevicepersonalization.service.PhFlags} */
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
                FCP_ENABLE_AUTHENTICATION,
                Boolean.toString(AUTHENTICATION_ENABLED),
                /* makeDefault= */ false);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_ENCRYPTION,
                Boolean.toString(ENCRYPTION_ENABLED),
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
    public void testEnableAuthentication() {
        // Without Overriding
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_AUTHENTICATION,
                Boolean.toString(AUTHENTICATION_ENABLED),
                /* makeDefault */ false);
        assertThat(FlagsFactory.getFlags().isAuthenticationEnabled())
                .isEqualTo(AUTHENTICATION_ENABLED);

        // Now overriding the value from PH
        boolean overrideEnableAuth = !AUTHENTICATION_ENABLED;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                FCP_ENABLE_AUTHENTICATION,
                Boolean.toString(overrideEnableAuth),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.isAuthenticationEnabled()).isEqualTo(overrideEnableAuth);
    }
}
