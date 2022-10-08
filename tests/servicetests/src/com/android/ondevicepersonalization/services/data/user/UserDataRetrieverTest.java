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

import java.util.TimeZone;

@RunWith(JUnit4.class)
public class UserDataRetrieverTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private UserDataRetriever mRetriever;

    @Before
    public void setup() {
        mRetriever = UserDataRetriever.getInstance(mContext);
    }

    @Test
    public void testGetUserData() {
        long timeMillis = System.currentTimeMillis();
        UserData userData = mRetriever.getUserData();
        assertTrue(userData.timeMillis > 0);
        assertTrue(userData.timeMillis >= timeMillis);
        TimeZone tz = TimeZone.getDefault();
        assertNotNull(userData.timeZone);
        assertEquals(userData.timeZone, tz);
        int orientation = mContext.getResources().getConfiguration().orientation;
        assertEquals(userData.orientation, orientation);
    }

    @Test
    public void testGetTimeZoneAfterModification() {
        TimeZone tzGmt4 = TimeZone.getTimeZone("GMT+04:00");
        TimeZone.setDefault(tzGmt4);
        UserData userData = mRetriever.getUserData();
        assertNotNull(userData.timeZone);
        assertEquals(userData.timeZone, tzGmt4);
    }
}
