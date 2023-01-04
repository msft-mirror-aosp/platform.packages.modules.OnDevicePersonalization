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

package com.android.ondevicepersonalization.services.data.events;

import android.provider.BaseColumns;

/** Contract for the events table. Defines the table. */
public class EventsContract {
    private EventsContract() {
    }

    /**
     * Table containing events. Each row in the table
     * represents a single event.
     */
    public static class EventsEntry implements BaseColumns {
        public static final String TABLE_NAME = "events";

        /** Time of the query in microseconds. */
        public static final String TIME_USEC = "timeUsec";

        /** The id of the thread serving the query. */
        public static final String THREAD_ID = "threadId";

        /** Id of the slot owner for this event */
        public static final String SLOT_ID = "slotId";

        /** Id of the bidder for this event */
        public static final String BID_ID = "bidId";

        /** Name of the service package or this event */
        public static final String SERVICE_PACKAGE_NAME = "servicePackageName";

        /** The position of the event in the slot */
        public static final String SLOT_POSITION = "slotPosition";

        /** {@link EventType} defining the type of event */
        public static final String TYPE = "type";

        /** Blob representing the event. */
        public static final String EVENT = "event";

        public static final String CREATE_TABLE_STATEMENT =
                "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + TIME_USEC + " INTEGER NOT NULL,"
                    + THREAD_ID + " INTEGER NOT NULL,"
                    + SLOT_ID + " TEXT NOT NULL,"
                    + BID_ID + " TEXT NOT NULL,"
                    + SERVICE_PACKAGE_NAME + " TEXT NOT NULL,"
                    + SLOT_POSITION + " INTEGER NOT NULL,"
                    + TYPE + " INTEGER NOT NULL,"
                    + EVENT + " BLOB NOT NULL,"
                    + "FOREIGN KEY(" + TIME_USEC + "," + THREAD_ID + ") REFERENCES "
                        + QueriesContract.QueriesEntry.TABLE_NAME + "("
                        + QueriesContract.QueriesEntry.TIME_USEC + ","
                        + QueriesContract.QueriesEntry.THREAD_ID + "),"
                    + "PRIMARY KEY(" + TIME_USEC + ","
                        + THREAD_ID + ","
                        + SLOT_ID + ","
                        + BID_ID + ","
                        + SERVICE_PACKAGE_NAME + ","
                        + SLOT_POSITION + ","
                        + TYPE + "))";

        private EventsEntry() {}
    }
}
