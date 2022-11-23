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
import static org.junit.Assert.assertNotEquals;
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

@RunWith(JUnit4.class)
public class UserDataDaoTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private UserDataDao mDao;

    @Before
    public void setup() {
        mDao = UserDataDao.getInstanceForTest(mContext);
    }

    @Test
    public void testInsert() {
        boolean insertResult = mDao.insertLocationHistoryData(
                1234567890, "111.11111", "-222.22222", 1, true);
        assertTrue(insertResult);
        assertTrue(mDao.insertAppUsageHistoryData("TikTok", 999100, 999200, 100));
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
    public void testReadLocationHistory() {
        mDao.insertLocationHistoryData(1234567890, "111.11111", "-222.22222", 1, true);
        Cursor cursor = mDao.readAllUserData(UserDataTables.LocationHistory.TABLE_NAME);

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                assertEquals(1234567890, cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.TIME_SEC)));
                assertEquals("111.11111", cursor.getString(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.LATITUDE)));
                assertEquals("-222.22222", cursor.getString(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.LONGITUDE)));
                assertEquals(1, cursor.getInt(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.SOURCE)));
                assertNotEquals(0, cursor.getInt(cursor.getColumnIndex(
                        UserDataTables.LocationHistory.IS_PRECISE)));
                cursor.moveToNext();
            }
        }
        cursor.close();
    }

    @Test
    public void testReadAppUsageStats() {
        mDao.insertAppUsageHistoryData("TikTok", 999100, 999200, 100);
        Cursor cursor = mDao.readAllUserData(UserDataTables.AppUsageHistory.TABLE_NAME);

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                assertEquals("TikTok", cursor.getString(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.PACKAGE_NAME)));
                assertEquals(999100, cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.STARTING_TIME_SEC)));
                assertEquals(999200, cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.ENDING_TIME_SEC)));
                assertEquals(100, cursor.getLong(cursor.getColumnIndex(
                        UserDataTables.AppUsageHistory.TOTAL_TIME_USED_SEC)));
                cursor.moveToNext();
            }
        }
        cursor.close();
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
