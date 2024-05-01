/*
 * Copyright (C) 2024 The Android Open Source Project
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

public class UserDataContract {
    private UserDataContract() {}

    /** Keep the installed apps for last 30 days. */
    public static class AppInstall implements BaseColumns {
        /** The name of app install table. */
        public static final String TABLE_NAME = "app_install";

        /** The list of app information including app package name and last update time. */
        public static final String APP_LIST = "package_name";

        /**
         * Whether the app list is added noise or not. 1 means this app list is already added noise
         * and 0 means it's raw installed app list.
         */
        public static final String IS_NOISED = "is_noised";

        /** The time when create the record. */
        public static final String CREATION_TIME = "creation_time";

        public static final String CREATE_TABLE_STATEMENT =
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_NAME
                        + " ("
                        + _ID
                        + " INTEGER PRIMARY KEY, "
                        + APP_LIST
                        + " BLOB NOT NULL, "
                        + IS_NOISED
                        + " INTEGER NOT NULL, "
                        + CREATION_TIME
                        + " INTEGER NOT NULL)";
    }
}
