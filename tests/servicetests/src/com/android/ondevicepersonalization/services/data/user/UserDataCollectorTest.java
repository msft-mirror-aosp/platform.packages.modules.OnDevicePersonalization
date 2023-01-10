/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.TextUtils;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class UserDataCollectorTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private UserDataCollector mCollector;
    private UserData mUserData;

    @Before
    public void setup() {
        mCollector = UserDataCollector.getInstanceForTest(mContext);
        mUserData = UserData.getInstance();
        mCollector.clearUserData(mUserData);
        mCollector.setLastTimeMillisAppUsageCollected(0);
        mCollector.setAllowedAppUsageEntries(new ArrayDeque<>());
    }

    @Test
    public void testInitializeUserData() throws InterruptedException {
        mCollector.initializeUserData(mUserData);

        // Test initial collection.
        assertTrue(mUserData.timeMillis > 0);
        assertTrue(mUserData.timeMillis <= mCollector.getTimeMillis());
        assertNotNull(mUserData.utcOffset);
        assertEquals(mUserData.utcOffset, mCollector.getUtcOffset());

        assertTrue(mUserData.availableBytesMB > 0);
        assertTrue(mUserData.batteryPct > 0);
        assertEquals(mUserData.country, mCollector.getCountry());
        assertEquals(mUserData.language, mCollector.getLanguage());
        assertEquals(mUserData.carrier, mCollector.getCarrier());
        assertTrue(mUserData.connectionType != UserData.ConnectionType.UNKNOWN);
        assertEquals(mUserData.connectionType, mCollector.getConnectionType());
        assertEquals(mUserData.networkMeteredStatus, mCollector.getNetworkMeteredStatus());
        assertTrue(mUserData.connectionSpeedKbps > 0);

        OSVersion osVersions = new OSVersion();
        mCollector.getOSVersions(osVersions);
        assertTrue(mUserData.osVersions.major > 0);
        assertEquals(mUserData.osVersions.major, osVersions.major);
        assertEquals(mUserData.osVersions.minor, osVersions.minor);
        assertEquals(mUserData.osVersions.micro, osVersions.micro);

        DeviceMetrics deviceMetrics = new DeviceMetrics();
        mCollector.getDeviceMetrics(deviceMetrics);
        assertEquals(mUserData.deviceMetrics.make, deviceMetrics.make);
        assertEquals(mUserData.deviceMetrics.model, deviceMetrics.model);
        assertTrue(mUserData.deviceMetrics.screenHeight > 0);
        assertEquals(mUserData.deviceMetrics.screenHeight, deviceMetrics.screenHeight);
        assertTrue(mUserData.deviceMetrics.screenWidth > 0);
        assertEquals(mUserData.deviceMetrics.screenWidth, deviceMetrics.screenWidth);
        assertTrue(mUserData.deviceMetrics.xdpi > 0);
        assertEquals(mUserData.deviceMetrics.xdpi, deviceMetrics.xdpi, 0.01);
        assertTrue(mUserData.deviceMetrics.ydpi > 0);
        assertEquals(mUserData.deviceMetrics.ydpi, deviceMetrics.ydpi, 0.01);
        assertTrue(mUserData.deviceMetrics.pxRatio > 0);
        assertEquals(mUserData.deviceMetrics.pxRatio, deviceMetrics.pxRatio, 0.01);

        List<AppInfo> appsInfo = new ArrayList();
        mCollector.getInstalledApps(appsInfo);
        assertTrue(mUserData.appsInfo.size() > 0);
        assertEquals(mUserData.appsInfo.size(), appsInfo.size());
        for (int i = 0; i < mUserData.appsInfo.size(); ++i) {
            assertFalse(TextUtils.isEmpty(mUserData.appsInfo.get(i).packageName));
            assertEquals(mUserData.appsInfo.get(i).packageName, appsInfo.get(i).packageName);
            assertEquals(mUserData.appsInfo.get(i).installed, appsInfo.get(i).installed);
        }
    }

    @Test
    public void testRealTimeUpdate() {
        // TODO: test orientation modification.
        mCollector.initializeUserData(mUserData);
        long oldTimeMillis = mUserData.timeMillis;
        TimeZone tzGmt4 = TimeZone.getTimeZone("GMT+04:00");
        TimeZone.setDefault(tzGmt4);
        mCollector.getRealTimeData(mUserData);
        assertTrue(oldTimeMillis <= mUserData.timeMillis);
        assertEquals(mUserData.utcOffset, 240);
    }

    @Test
    public void testGetCountry() {
        mCollector.setLocale(new Locale("en", "US"));
        mCollector.initializeUserData(mUserData);
        assertNotNull(mUserData.country);
        assertEquals(mUserData.country, Country.USA);
    }

    @Test
    public void testUnknownCountry() {
        mCollector.setLocale(new Locale("en"));
        mCollector.initializeUserData(mUserData);
        assertNotNull(mUserData.country);
        assertEquals(mUserData.country, Country.UNKNOWN);
    }

    @Test
    public void testGetLanguage() {
        mCollector.setLocale(new Locale("zh", "CN"));
        mCollector.initializeUserData(mUserData);
        assertNotNull(mUserData.language);
        assertEquals(mUserData.language, Language.ZH);
    }

    @Test
    public void testUnknownLanguage() {
        mCollector.setLocale(new Locale("nonexist_lang", "CA"));
        mCollector.initializeUserData(mUserData);
        assertNotNull(mUserData.language);
        assertEquals(mUserData.language, Language.UNKNOWN);
    }

    /**
     * TODO (b/261748573): Although very unlikely, this test could be flaky when the call
     * happens around midnight that the two invocations span different days.
     */
    @Test
    public void testAppUsageUpdate() {
        assertTrue(mCollector.getAppUsageStats(mUserData.appUsageHistory));
        HashMap<String, Long> oldAppUsageHistory = mUserData.appUsageHistory;
        assertFalse(mCollector.getAppUsageStats(mUserData.appUsageHistory));
        assertEquals(oldAppUsageHistory.size(), mUserData.appUsageHistory.size());
        for (String packageName: mUserData.appUsageHistory.keySet()) {
            assertTrue(oldAppUsageHistory.containsKey(packageName));
            assertEquals(oldAppUsageHistory.get(packageName),
                    mUserData.appUsageHistory.get(packageName));
        }
    }
}
