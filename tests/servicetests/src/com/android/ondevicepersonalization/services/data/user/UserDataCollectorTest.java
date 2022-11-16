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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    public void testGetUserData() {
        UserData userData = mCollector.getUserData();

        // Real time data
        assertTrue(userData.timeMillis > 0);
        assertTrue(userData.timeMillis <= mCollector.getTimeMillis());
        assertNotNull(userData.timeZone);
        assertEquals(userData.timeZone, mCollector.getTimeZone());
        assertEquals(userData.orientation, mCollector.getOrientation());

        assertEquals(userData.availableBytesMB, mCollector.getAvailableBytesMB());
        assertEquals(userData.batteryPct, mCollector.getBatteryPct());
        assertEquals(userData.country, mCollector.getCountry());
        assertEquals(userData.language, mCollector.getLanguage());
        assertEquals(userData.carrier, mCollector.getCarrier());
        assertEquals(userData.connectionType, mCollector.getConnectionType());
        assertEquals(userData.networkMeteredStatus, mCollector.getNetworkMeteredStatus());
        assertEquals(userData.connectionSpeedKbps, mCollector.getConnectionSpeedKbps());

        UserData ud = new UserData();
        ud.osVersions = new UserData.OSVersion();
        mCollector.getOSVersions(ud.osVersions);
        assertEquals(userData.osVersions.major, ud.osVersions.major);
        assertEquals(userData.osVersions.minor, ud.osVersions.minor);
        assertEquals(userData.osVersions.micro, ud.osVersions.micro);

        ud.deviceMetrics = new UserData.DeviceMetrics();
        mCollector.getDeviceMetrics(ud.deviceMetrics);
        assertEquals(userData.deviceMetrics.make, ud.deviceMetrics.make);
        assertEquals(userData.deviceMetrics.model, ud.deviceMetrics.model);
        assertEquals(userData.deviceMetrics.screenHeight, ud.deviceMetrics.screenHeight);
        assertEquals(userData.deviceMetrics.screenWidth, ud.deviceMetrics.screenWidth);
        assertEquals(userData.deviceMetrics.xdpi, ud.deviceMetrics.xdpi, 0.01);
        assertEquals(userData.deviceMetrics.ydpi, ud.deviceMetrics.ydpi, 0.01);
        assertEquals(userData.deviceMetrics.pxRatio, ud.deviceMetrics.pxRatio, 0.01);
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
