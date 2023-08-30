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

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

/**
 * Dao used to manage access to Events and Queries tables
 */
public class EventsDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "EventsDao";

    private static EventsDao sSingleton;

    private final OnDevicePersonalizationDbHelper mDbHelper;

    public EventsDao(@NonNull OnDevicePersonalizationDbHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    /** Returns an instance of the EventsDao given a context. */
    public static EventsDao getInstance(@NonNull Context context) {
        synchronized (EventsDao.class) {
            if (sSingleton == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstance(context);
                sSingleton = new EventsDao(dbHelper);
            }
            return sSingleton;
        }
    }

    /**
     * Returns an instance of the EventsDao given a context. This is used
     * for testing only.
     */
    @VisibleForTesting
    public static EventsDao getInstanceForTest(@NonNull Context context) {
        synchronized (EventsDao.class) {
            if (sSingleton == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstanceForTest(context);
                sSingleton = new EventsDao(dbHelper);
            }
            return sSingleton;
        }
    }

    /**
     * Inserts the Event into the Events table.
     *
     * @return The row id of the newly inserted row if successful, -1 otherwise
     */
    public long insertEvent(@NonNull Event event) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(EventsContract.EventsEntry.QUERY_ID, event.getQueryId());
            values.put(EventsContract.EventsEntry.ROW_INDEX, event.getRowIndex());
            values.put(EventsContract.EventsEntry.TIME_MILLIS, event.getTimeMillis());
            values.put(EventsContract.EventsEntry.SERVICE_PACKAGE_NAME,
                    event.getServicePackageName());
            values.put(EventsContract.EventsEntry.TYPE, event.getType());
            values.put(EventsContract.EventsEntry.EVENT_DATA, event.getEventData());
            return db.insert(EventsContract.EventsEntry.TABLE_NAME, null,
                    values);
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to insert event", e);
        }
        return -1;
    }

    /**
     * Inserts the Query into the Queries table.
     *
     * @return The row id of the newly inserted row if successful, -1 otherwise
     */
    public long insertQuery(@NonNull Query query) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(QueriesContract.QueriesEntry.TIME_MILLIS, query.getTimeMillis());
            values.put(QueriesContract.QueriesEntry.SERVICE_PACKAGE_NAME,
                    query.getServicePackageName());
            values.put(QueriesContract.QueriesEntry.QUERY_DATA, query.getQueryData());
            return db.insert(QueriesContract.QueriesEntry.TABLE_NAME, null,
                    values);
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to insert query", e);
        }
        return -1;
    }

    /**
     * Updates the eventState, adds it if it doesn't already exist.
     *
     * @return true if the update/insert succeeded, false otherwise
     */
    public boolean updateOrInsertEventState(EventState eventState) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(EventStateContract.EventStateEntry.EVENT_ID, eventState.getEventId());
            values.put(EventStateContract.EventStateEntry.QUERY_ID, eventState.getQueryId());
            values.put(EventStateContract.EventStateEntry.SERVICE_PACKAGE_NAME,
                    eventState.getServicePackageName());
            values.put(EventStateContract.EventStateEntry.TASK_IDENTIFIER,
                    eventState.getTaskIdentifier());
            return db.insertWithOnConflict(EventStateContract.EventStateEntry.TABLE_NAME,
                    null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to update or insert eventState", e);
        }
        return false;
    }

    /**
     * Gets the eventState for the given package and task
     *
     * @return eventState if found, null otherwise
     */
    public EventState getEventState(String taskIdentifier, String packageName) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = EventStateContract.EventStateEntry.TASK_IDENTIFIER + " = ? AND "
                + EventStateContract.EventStateEntry.SERVICE_PACKAGE_NAME + " = ?";
        String[] selectionArgs = {taskIdentifier, packageName};
        String[] projection = {EventStateContract.EventStateEntry.EVENT_ID,
                EventStateContract.EventStateEntry.QUERY_ID};
        try (Cursor cursor = db.query(
                EventStateContract.EventStateEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                /* groupBy= */ null,
                /* having= */ null,
                /* orderBy= */ null
        )) {
            if (cursor.moveToFirst()) {
                long eventId = cursor.getLong(cursor.getColumnIndexOrThrow(
                        EventStateContract.EventStateEntry.EVENT_ID));
                long queryId = cursor.getLong(cursor.getColumnIndexOrThrow(
                        EventStateContract.EventStateEntry.QUERY_ID));

                return new EventState.Builder()
                        .setEventId(eventId)
                        .setQueryId(queryId)
                        .setServicePackageName(packageName)
                        .setTaskIdentifier(taskIdentifier)
                        .build();
            }
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to read eventState", e);
        }
        return null;
    }
}
