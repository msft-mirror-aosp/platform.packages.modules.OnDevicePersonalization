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

import static com.android.federatedcompute.services.common.Flags.FEDERATED_COMPUTE_GLOBAL_KILL_SWITCH;
import static com.android.federatedcompute.services.common.PhFlags.KEY_FEDERATED_COMPUTE_KILL_SWITCH;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link com.android.ondevicepersonalization.service.PhFlags} */
@RunWith(AndroidJUnit4.class)
public class PhFlagsTest {
    /** Get necessary permissions to access Setting.Config API and set up context */
    @Before
    public void setUpContext() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
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
        final boolean phOverridingValue = true;
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        Flags phFlags = FlagsFactory.getFlags();
        assertThat(phFlags.getGlobalKillSwitch()).isEqualTo(phOverridingValue);
    }
}
