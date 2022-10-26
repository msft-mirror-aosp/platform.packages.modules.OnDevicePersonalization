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

import android.provider.BaseColumns;

/** Container class for definitions and constants of user data tables. */
public final class UserDataTables implements BaseColumns {
    public static final String USER_TABLE_PREFIX = "user_";
    public static final String INDEX_PREFIX = "index_";

    /** Location history table. */
    public static class LocationHistory implements BaseColumns {
        /** The name of location history table. */
        public static final String TABLE_NAME = USER_TABLE_PREFIX + "location_history";

        /** The name of location history index. */
        public static final String INDEX_NAME = INDEX_PREFIX + TABLE_NAME;

        /** The time that the location is retrieved in seconds. */
        public static final String TIME_SEC = "time_sec";

        /** The latitude of the location in the format of "[+-]DDD.DDDDD". */
        public static final String LATITUDE = "latitude";

        /** The Longitude of the location in the format of "[+-]DDD.DDDDD". */
        public static final String LONGITUDE = "longitude";

        /** The source of location signal. */
        public static final String SOURCE = "source";

        /** Is the location accuracy precise or coarse. */
        public static final String IS_PRECISE = "is_precise";

        public static final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS "
                + TABLE_NAME + " ("
                + _ID + " INTEGER PRIMARY KEY, "
                + TIME_SEC + " INTEGER NOT NULL, "
                + LATITUDE + " TEXT NOT NULL, "
                + LONGITUDE + " TEXT NOT NULL, "
                + SOURCE + " INTEGER NOT NULL, "
                + IS_PRECISE + " INTEGER NOT NULL)";

        public static final String CREATE_INDEXES_STATEMENT = "CREATE INDEX IF NOT EXISTS "
                + INDEX_NAME + " ON "
                + TABLE_NAME + "( "
                + TIME_SEC + ")";
    }

    // Private constructor to prevent instantiation.
    private UserDataTables() {}
}
