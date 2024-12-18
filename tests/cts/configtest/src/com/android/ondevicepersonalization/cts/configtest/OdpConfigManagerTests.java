/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.ondevicepersonalization.cts.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationConfigManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

/** CTS Test cases for OnDevicePersonalizationConfigManager APIs. */
@RunWith(AndroidJUnit4.class)
public class OdpConfigManagerTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());

        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "global_kill_switch "
                        + false);
    }

    @After
    public void cleanUp() throws Exception {
        ShellUtils.runShellCommand(
                "device_config delete on_device_personalization global_kill_switch");
    }

    @Test
    public void testSetPersonalizationStatus() throws Exception {
        OnDevicePersonalizationConfigManager manager =
                mContext.getSystemService(OnDevicePersonalizationConfigManager.class);
        assertNotNull(manager);
        ResultReceiver<Void> receiver = new ResultReceiver<>();
        manager.setPersonalizationEnabled(true, Executors.newSingleThreadExecutor(), receiver);
        assertTrue(receiver.isCalled());
    }
}
