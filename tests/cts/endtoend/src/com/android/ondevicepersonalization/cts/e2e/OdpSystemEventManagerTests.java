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

import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParams;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationSystemEventManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

/** CTS Test cases for OnDevicePersonalizationConfigManager APIs. */
@RunWith(AndroidJUnit4.class)
public class OdpSystemEventManagerTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());
        Assume.assumeTrue(DeviceSupportHelper.isOdpModuleAvailable());
    }

    @Test
    public void testNotifyMeasurementEventPermissionDenied() throws Exception {
        OnDevicePersonalizationSystemEventManager manager =
                mContext.getSystemService(OnDevicePersonalizationSystemEventManager.class);
        assertNotNull(manager);
        ResultReceiver<Void> receiver = new ResultReceiver<>();
        MeasurementWebTriggerEventParams params =
                new MeasurementWebTriggerEventParams.Builder(
                        Uri.parse("http://example.com"),
                        "com.example.testapp",
                        ComponentName.createRelative("com.example.service", ".ServiceClass"))
                .setCertDigest("ABCD")
                .setEventData(new byte[] {1, 2, 3})
                .build();
        manager.notifyMeasurementEvent(params, Executors.newSingleThreadExecutor(), receiver);
        assertTrue(receiver.isError());
        assertNotNull(receiver.getException());
        assertTrue(receiver.getException().getClass().getSimpleName(),
                receiver.getException() instanceof SecurityException);
    }
}
