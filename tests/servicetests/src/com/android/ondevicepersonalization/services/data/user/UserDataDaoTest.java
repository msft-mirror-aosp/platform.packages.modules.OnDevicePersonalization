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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Calendar;

@RunWith(JUnit4.class)
public class UserDataDaoTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private UserDataDao mDao;
    private static final int TEST_APP_USAGE_LEGAL_DAYS = 28;
    private static final int TEST_APP_USAGE_ILLEGAL_DAYS = 32;
    private static final int TEST_APP_USAGE_TOTAL_TIMES = 60;
    private static final long MILLISECONDS_PER_DAY = 86400000;
    private static final String TEST_PACKAGE_NAME = "foobar";
    private static final long TEST_TOTAL_TIME_PER_DAY = 100;

    @Before
    public void setup() {
        mDao = UserDataDao.getInstanceForTest(mContext);
    }

    @Test
    public void testInsert() {
        boolean insertResult = mDao.insertLocationHistoryData(
                1234567890, "111.11111", "-222.22222", 1, true);
        assertTrue(insertResult);
        assertTrue(mDao.insertAppUsageStatsData("TikTok", 999100, 999200, 100));
    }

    @Test
    public void testInsertNullLatitude() {
        boolean insertResult = mDao.insertLocationHistoryData(
                1234567890, null, "-222.22222", 1, true);
        assertFalse(insertResult);
    }

    @Test
    public void testInsertNullLongitude() {
        boolean insertResult = mDao.insertLocationHistoryData(
                1234567890, "111.11111", null, 1, true);
        assertFalse(insertResult);
    }

    @Test
    public void testReadAppUsageStats() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        long endTimeMillis = cal.getTimeInMillis();
        for (int i = 0; i < TEST_APP_USAGE_TOTAL_TIMES; ++i) {
            final long startTimeMillis = endTimeMillis - MILLISECONDS_PER_DAY;
            mDao.insertAppUsageStatsData(TEST_PACKAGE_NAME,
                    startTimeMillis, endTimeMillis, TEST_TOTAL_TIME_PER_DAY);
            endTimeMillis = startTimeMillis;
        }
        Cursor cursor = mDao.readAppUsageInLastXDays(TEST_APP_USAGE_LEGAL_DAYS);
        assertNotNull(cursor);
        assertEquals(TEST_APP_USAGE_LEGAL_DAYS, cursor.getCount());
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                assertEquals(TEST_PACKAGE_NAME, cursor.getString(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.PACKAGE_NAME)));
                assertEquals(TEST_TOTAL_TIME_PER_DAY, cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.TOTAL_TIME_USED_SEC)));
                cursor.moveToNext();
            }
        }

        cursor = mDao.readAppUsageInLastXDays(TEST_APP_USAGE_ILLEGAL_DAYS);
        assertNull(cursor);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
