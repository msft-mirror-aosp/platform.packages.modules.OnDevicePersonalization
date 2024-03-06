/*
 * Copyright 2022 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;

import static org.junit.Assert.assertEquals;

import android.net.NetworkCapabilities;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

/**
 * Unit Tests of RemoteData API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UserDataTest {
    @Test
    public void testUserData() {
        UserData data = new UserData.Builder()
                .setTimezoneUtcOffsetMins(120)
                .setOrientation(ORIENTATION_PORTRAIT)
                .setAvailableStorageBytes(1024)
                .setBatteryPercentage(50)
                .setCarrier("carrier")
                .setNetworkCapabilities(
                    NetworkCapabilities.Builder.withoutDefaultCapabilities()
                                .setLinkDownstreamBandwidthKbps(100).build())
                .setDataNetworkType(NETWORK_TYPE_LTE)
                .addAppInfo("com.example.app", new AppInfo.Builder().setInstalled(true).build())
                .build();

        assertEquals(Duration.ofMinutes(120), data.getTimezoneUtcOffset());
        assertEquals(ORIENTATION_PORTRAIT, data.getOrientation());
        assertEquals(1024, data.getAvailableStorageBytes());
        assertEquals(50, data.getBatteryPercentage());
        assertEquals("carrier", data.getCarrier());
        assertEquals(100, data.getNetworkCapabilities().getLinkDownstreamBandwidthKbps());
        assertEquals(NETWORK_TYPE_LTE, data.getDataNetworkType());
        assertEquals(true, data.getAppInfos().get("com.example.app").isInstalled());
    }
}
