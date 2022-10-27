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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

/** DAO for accessing to vendor data tables. */
public class UserDataDao {
    private static final String TAG = "UserDataDao";

    private static UserDataDao sUserDataDao;
    private final OnDevicePersonalizationDbHelper mDbHelper;

    private UserDataDao(OnDevicePersonalizationDbHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    /**
     * Returns an instance of the UserDataDao given a context.
     *
     * @param context    The context of the application.
     * @return Instance of UserDataDao for accessing the requested package's table.
     */
    public static UserDataDao getInstance(Context context) {
        synchronized (UserDataDao.class) {
            if (sUserDataDao == null) {
                sUserDataDao = new UserDataDao(
                    OnDevicePersonalizationDbHelper.getInstance(context));
            }
            return sUserDataDao;
        }
    }

    /**
     * Returns an instance of the UserDataDao given a context. This is used for testing only.
     */
    @VisibleForTesting
    public static UserDataDao getInstanceForTest(Context context) {
        return getInstance(context);
    }

    /**
     * Inserts location history row if it doesn't already exist.
     *
     * @return true if the insert succeeded, false otherwise.
     */
    public boolean insertLocationHistoryData(long timeSec, String latitude, String longitude,
                                             int source, boolean isPrecise) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            if (db == null) {
                return false;
            }
            ContentValues values = new ContentValues();
            values.put(UserDataTables.LocationHistory.TIME_SEC, timeSec);
            values.put(UserDataTables.LocationHistory.LATITUDE, latitude);
            values.put(UserDataTables.LocationHistory.LONGITUDE, longitude);
            values.put(UserDataTables.LocationHistory.SOURCE, source);
            values.put(UserDataTables.LocationHistory.IS_PRECISE, isPrecise);
            return db.insertWithOnConflict(UserDataTables.LocationHistory.TABLE_NAME, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE) != -1;
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to insert location history data", e);
            return false;
        }
    }
}
