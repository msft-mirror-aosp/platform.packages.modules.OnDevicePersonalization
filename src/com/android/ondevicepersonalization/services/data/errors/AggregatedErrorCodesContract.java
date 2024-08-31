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

package com.android.ondevicepersonalization.services.data.errors;

import android.provider.BaseColumns;

/** Contract for the per vendor aggregated error code tables. Defines the table. */
final class AggregatedErrorCodesContract {
    private AggregatedErrorCodesContract() {}

    /**
     * Table containing aggregated error data associated with a particular vendor/adopter.
     *
     * <p>Each table is associated with a particular vendor.
     */
    public static class ErrorDataEntry implements BaseColumns {

        /** The {@code isolatedServiceErrorCode} returned from the {@code IsolatedWorker}. */
        public static final String EXCEPTION_ERROR_CODE = "exception_error_code";

        /** The date that error was thrown. */
        public static final String EXCEPTION_DATE = "exception_date";

        /** The total count of the errors thrown by the vendor code on the given date. */
        public static final String EXCEPTION_COUNT = "exception_count";

        /**
         * The version of the package of the {@code IsolatedService} when the error was reported.
         */
        public static final String SERVICE_PACKAGE_VERSION = "service_package_version";

        private ErrorDataEntry() {}

        /** Returns the statement for table creation for the given table name. */
        public static String getCreateTableIfNotExistsStatement(final String tableName) {
            return "CREATE TABLE IF NOT EXISTS "
                    + tableName
                    + " ("
                    + EXCEPTION_ERROR_CODE
                    + " INTEGER DEFAULT 0,"
                    + EXCEPTION_DATE
                    + " INTEGER DEFAULT 0,"
                    + EXCEPTION_COUNT
                    + " INTEGER DEFAULT 0,"
                    + SERVICE_PACKAGE_VERSION
                    + " INTEGER DEFAULT 0,"
                    + "PRIMARY KEY("
                    + EXCEPTION_ERROR_CODE
                    + ","
                    + EXCEPTION_DATE
                    + "))";
        }
    }
}
