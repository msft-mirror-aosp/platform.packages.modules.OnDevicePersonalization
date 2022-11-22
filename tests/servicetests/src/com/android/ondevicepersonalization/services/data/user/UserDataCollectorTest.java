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

import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class UserDataCollectorTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private UserDataCollector mCollector;

    @Before
    public void setup() {
        mCollector = UserDataCollector.getInstance(mContext);
    }

    @Test
    public void testGetUserData() throws InterruptedException {
        UserData userData = mCollector.getUserData();

        // Real time data
        assertTrue(userData.timeMillis > 0);
        assertTrue(userData.timeMillis <= mCollector.getTimeMillis());
        assertNotNull(userData.timeZone);
        assertEquals(userData.timeZone, mCollector.getTimeZone());
        assertEquals(userData.orientation, mCollector.getOrientation());

        assertTrue(userData.availableBytesMB > 0);
        assertEquals(userData.availableBytesMB, mCollector.getAvailableBytesMB());
        assertTrue(userData.batteryPct > 0);
        assertEquals(userData.batteryPct, mCollector.getBatteryPct());
        assertTrue(userData.batteryPct > 0);
        assertEquals(userData.country, mCollector.getCountry());
        assertEquals(userData.language, mCollector.getLanguage());
        assertEquals(userData.carrier, mCollector.getCarrier());
        assertTrue(userData.connectionType != UserData.ConnectionType.UNKNOWN);
        assertEquals(userData.connectionType, mCollector.getConnectionType());
        assertEquals(userData.networkMeteredStatus, mCollector.getNetworkMeteredStatus());
        assertTrue(userData.connectionSpeedKbps > 0);
        assertEquals(userData.connectionSpeedKbps, mCollector.getConnectionSpeedKbps());

        UserData ud = new UserData();
        ud.osVersions = new UserData.OSVersion();
        mCollector.getOSVersions(ud.osVersions);
        assertTrue(userData.osVersions.major > 0);
        assertEquals(userData.osVersions.major, ud.osVersions.major);
        assertEquals(userData.osVersions.minor, ud.osVersions.minor);
        assertEquals(userData.osVersions.micro, ud.osVersions.micro);

        ud.deviceMetrics = new UserData.DeviceMetrics();
        mCollector.getDeviceMetrics(ud.deviceMetrics);
        assertEquals(userData.deviceMetrics.make, ud.deviceMetrics.make);
        assertEquals(userData.deviceMetrics.model, ud.deviceMetrics.model);
        assertTrue(userData.deviceMetrics.screenHeight > 0);
        assertEquals(userData.deviceMetrics.screenHeight, ud.deviceMetrics.screenHeight);
        assertTrue(userData.deviceMetrics.screenWidth > 0);
        assertEquals(userData.deviceMetrics.screenWidth, ud.deviceMetrics.screenWidth);
        assertTrue(userData.deviceMetrics.xdpi > 0);
        assertEquals(userData.deviceMetrics.xdpi, ud.deviceMetrics.xdpi, 0.01);
        assertTrue(userData.deviceMetrics.ydpi > 0);
        assertEquals(userData.deviceMetrics.ydpi, ud.deviceMetrics.ydpi, 0.01);
        assertTrue(userData.deviceMetrics.pxRatio > 0);
        assertEquals(userData.deviceMetrics.pxRatio, ud.deviceMetrics.pxRatio, 0.01);

        ud.appsInfo = new ArrayList();
        mCollector.getInstalledApps(ud.appsInfo);
        assertTrue(userData.appsInfo.size() > 0);
        assertEquals(userData.appsInfo.size(), ud.appsInfo.size());
        for (int i = 0; i < userData.appsInfo.size(); ++i) {
            assertFalse(TextUtils.isEmpty(userData.appsInfo.get(i).packageName));
            assertEquals(userData.appsInfo.get(i).packageName, ud.appsInfo.get(i).packageName);
            assertEquals(userData.appsInfo.get(i).installed, ud.appsInfo.get(i).installed);
        }

        ud.appsUsageStats = new ArrayList();
        mCollector.getAppUsageStats(ud.appsUsageStats);
        assertEquals(userData.appsUsageStats.size(), ud.appsUsageStats.size());
        for (int i = 0; i < userData.appsUsageStats.size(); ++i) {
            UserData.AppUsageStats aus = userData.appsUsageStats.get(i);
            UserData.AppUsageStats ausRef = ud.appsUsageStats.get(i);
            assertFalse(TextUtils.isEmpty(aus.packageName));
            assertEquals(aus.packageName, ausRef.packageName);
            assertTrue(aus.startTimeMillis > 0);
            assertTrue(aus.startTimeMillis <= ausRef.startTimeMillis);
            assertTrue(aus.endTimeMillis > 0);
            assertTrue(aus.endTimeMillis <= ausRef.endTimeMillis);
            assertTrue(aus.totalTimeSec <= ausRef.totalTimeSec);
        }

        ud.locationInfo = new UserData.LocationInfo();
        mCollector.getCurrentLocation(ud.locationInfo);
        assertTrue(userData.locationInfo.timeMillis <= ud.locationInfo.timeMillis);
        assertEquals(userData.locationInfo.latitude, ud.locationInfo.latitude, 0.01);
        assertEquals(userData.locationInfo.longitude, ud.locationInfo.longitude, 0.01);
        assertEquals(userData.locationInfo.provider, ud.locationInfo.provider);
        assertEquals(userData.locationInfo.isPreciseLocation, ud.locationInfo.isPreciseLocation);
    }

    @Test
    public void testGetTimeZoneAfterModification() {
        TimeZone tzGmt4 = TimeZone.getTimeZone("GMT+04:00");
        TimeZone.setDefault(tzGmt4);
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.timeZone);
        assertEquals(userData.timeZone, tzGmt4);
    }

    @Test
    public void testGetCountry() {
        mCollector.setLocale(new Locale("en", "US"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.country);
        assertEquals(userData.country, Country.USA);
    }

    @Test
    public void testUnknownCountry() {
        mCollector.setLocale(new Locale("en"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.country);
        assertEquals(userData.country, Country.UNKNOWN);
    }

    @Test
    public void testGetLanguage() {
        mCollector.setLocale(new Locale("zh", "CN"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.language);
        assertEquals(userData.language, Language.ZH);
    }

    @Test
    public void testUnknownLanguage() {
        mCollector.setLocale(new Locale("nonexist_lang", "CA"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.language);
        assertEquals(userData.language, Language.UNKNOWN);
    }
}
