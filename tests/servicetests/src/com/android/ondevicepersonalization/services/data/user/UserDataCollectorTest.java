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
        assertEquals(userData.screenHeight, mCollector.getScreenHeightInDp());
        assertEquals(userData.screenWidth, mCollector.getScreenWidthInDp());
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
        Locale.setDefault(new Locale("en", "US"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.country);
        assertEquals(userData.country, Country.USA);
    }

    @Test
    public void testUnknownCountry() {
        Locale.setDefault(new Locale("en"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.country);
        assertEquals(userData.country, Country.UNKNOWN);
    }

    @Test
    public void testGetLanguage() {
        Locale.setDefault(new Locale("zh", "CN"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.language);
        assertEquals(userData.language, Language.ZH);
    }

    @Test
    public void testUnknownLanguage() {
        Locale.setDefault(new Locale("nonexist_lang", "CA"));
        UserData userData = mCollector.getUserData();
        assertNotNull(userData.language);
        assertEquals(userData.language, Language.UNKNOWN);
    }
}
