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

package com.android.ondevicepersonalization.services.data;

import android.provider.BaseColumns;

/** Contract for the seller_data table. Defines the table. */
class SellerDataContract {
    private SellerDataContract() {
    }

    /**
     * Table containing data belonging to sellers. Each row is owned by a single seller and
     * contains data which will be used during ad requests.
     */
    public static class SellerDataEntry implements BaseColumns {
        public static final String TABLE_NAME = "seller_data";

        /** Name of seller SDK that owns this row */
        public static final String OWNER = "owner";

        /** Certificate used to sign seller SDK */
        public static final String CERTIFICATE = "certificate";

        /** Name of the app that this row contains data for */
        public static final String APPPACKAGENAME = "apppackagename";

        /** Lookup key for the row - unique for each seller + app */
        public static final String KEY = "key";

        /** Row data - seller settings and config */
        public static final String DATA = "data";

        /** A seller-assigned fingerprint for the row contents */
        public static final String FP = "fp";

        public static final String CREATE_TABLE =
                "CREATE TABLE " + TABLE_NAME + " ("
                        + OWNER + " TEXT NOT NULL,"
                        + CERTIFICATE + " TEXT NOT NULL,"
                        + APPPACKAGENAME + " TEXT NOT NULL,"
                        + KEY + " TEXT NOT NULL,"
                        + DATA + " BLOB NOT NULL,"
                        + FP + " TEXT NOT NULL,"
                        + "PRIMARY KEY(" + OWNER + ","
                        + CERTIFICATE + "," + APPPACKAGENAME + "," + KEY + "))";
    }
}
