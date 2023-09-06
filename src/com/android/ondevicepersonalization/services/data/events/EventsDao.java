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

import java.util.ArrayList;
import java.util.List;

/**
 * Dao used to manage access to Events and Queries tables
 */
public class EventsDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "EventsDao";

    private static EventsDao sSingleton;

    private final OnDevicePersonalizationDbHelper mDbHelper;

    private EventsDao(@NonNull OnDevicePersonalizationDbHelper dbHelper) {
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

    /**
     * Queries the events and queries table to return all new rows from given ids for the given
     * package
     *
     * @param servicePackageName Name of the package to read rows for
     * @param fromEventId        EventId to find all new rows from
     * @param fromQueryId        QueryId to find all new rows from
     * @return List of JoinedEvents.
     */
    public List<JoinedEvent> readAllNewRowsForPackage(String servicePackageName,
            long fromEventId, long fromQueryId) {
        // Query on the joined query & event table
        String joinedSelection = EventsContract.EventsEntry.EVENT_ID + " > ?"
                + " AND " + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.SERVICE_PACKAGE_NAME + " = ?";
        String[] joinedSelectionArgs = {String.valueOf(fromEventId), servicePackageName};
        List<JoinedEvent> joinedEventList = readJoinedTableRows(joinedSelection,
                joinedSelectionArgs);

        // Query on the queries table
        String queriesSelection = QueriesContract.QueriesEntry.QUERY_ID + " > ?"
                + " AND " + QueriesContract.QueriesEntry.SERVICE_PACKAGE_NAME + " = ?";
        String[] queriesSelectionArgs = {String.valueOf(fromQueryId), servicePackageName};
        List<Query> queryList = readQueryRows(queriesSelection, queriesSelectionArgs);
        for (Query query : queryList) {
            joinedEventList.add(new JoinedEvent.Builder()
                    .setQueryId(query.getQueryId())
                    .setQueryData(query.getQueryData())
                    .setQueryTimeMillis(query.getTimeMillis())
                    .setServicePackageName(query.getServicePackageName())
                    .build());
        }
        return joinedEventList;
    }

    /**
     * Queries the events and queries table to return all new rows from given ids for all packages
     *
     * @param fromEventId EventId to find all new rows from
     * @param fromQueryId QueryId to find all new rows from
     * @return List of JoinedEvents.
     */
    public List<JoinedEvent> readAllNewRows(long fromEventId, long fromQueryId) {
        // Query on the joined query & event table
        String joinedSelection = EventsContract.EventsEntry.EVENT_ID + " > ?";
        String[] joinedSelectionArgs = {String.valueOf(fromEventId)};
        List<JoinedEvent> joinedEventList = readJoinedTableRows(joinedSelection,
                joinedSelectionArgs);

        // Query on the queries table
        String queriesSelection = QueriesContract.QueriesEntry.QUERY_ID + " > ?";
        String[] queriesSelectionArgs = {String.valueOf(fromQueryId)};
        List<Query> queryList = readQueryRows(queriesSelection, queriesSelectionArgs);
        for (Query query : queryList) {
            joinedEventList.add(new JoinedEvent.Builder()
                    .setQueryId(query.getQueryId())
                    .setQueryData(query.getQueryData())
                    .setQueryTimeMillis(query.getTimeMillis())
                    .setServicePackageName(query.getServicePackageName())
                    .build());
        }
        return joinedEventList;
    }

    private List<Query> readQueryRows(String selection, String[] selectionArgs) {
        List<Query> queries = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String orderBy = QueriesContract.QueriesEntry.QUERY_ID;
        try (Cursor cursor = db.query(
                QueriesContract.QueriesEntry.TABLE_NAME,
                /* projection= */ null,
                selection,
                selectionArgs,
                /* groupBy= */ null,
                /* having= */ null,
                orderBy
        )) {
            while (cursor.moveToNext()) {
                long queryId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_ID));
                byte[] queryData = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_DATA));
                long timeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.TIME_MILLIS));
                String servicePackageName = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                QueriesContract.QueriesEntry.SERVICE_PACKAGE_NAME));
                queries.add(new Query.Builder()
                        .setQueryId(queryId)
                        .setQueryData(queryData)
                        .setTimeMillis(timeMillis)
                        .setServicePackageName(servicePackageName)
                        .build()
                );
            }
        } catch (IllegalArgumentException e) {
            sLogger.e(e, TAG + ": Failed parse resulting query");
            return new ArrayList<>();
        }
        return queries;
    }

    private List<JoinedEvent> readJoinedTableRows(String selection, String[] selectionArgs) {
        List<JoinedEvent> joinedEventList = new ArrayList<>();

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        String eventTimeMillisCol = "eventTimeMillis";
        String queryTimeMillisCol = "queryTimeMillis";
        String select = "SELECT "
                + EventsContract.EventsEntry.EVENT_ID + ","
                + EventsContract.EventsEntry.ROW_INDEX + ","
                + EventsContract.EventsEntry.TYPE + ","
                + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.SERVICE_PACKAGE_NAME + ","
                + EventsContract.EventsEntry.EVENT_DATA + ","
                + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.TIME_MILLIS + " AS " + eventTimeMillisCol + ","
                + EventsContract.EventsEntry.TABLE_NAME + "."
                + EventsContract.EventsEntry.QUERY_ID + ","
                + QueriesContract.QueriesEntry.QUERY_DATA + ","
                + QueriesContract.QueriesEntry.TABLE_NAME + "."
                + QueriesContract.QueriesEntry.TIME_MILLIS + " AS " + queryTimeMillisCol;
        String from = " FROM " + EventsContract.EventsEntry.TABLE_NAME
                + " INNER JOIN " + QueriesContract.QueriesEntry.TABLE_NAME
                + " ON "
                + QueriesContract.QueriesEntry.TABLE_NAME + "."
                + QueriesContract.QueriesEntry.QUERY_ID + " = "
                + EventsContract.EventsEntry.TABLE_NAME + "." + EventsContract.EventsEntry.QUERY_ID;
        String where = " WHERE " + selection;
        String orderBy = " ORDER BY " + EventsContract.EventsEntry.EVENT_ID;
        String query = select + from + where + orderBy;
        try (Cursor cursor = db.rawQuery(query, selectionArgs)) {
            while (cursor.moveToNext()) {
                long eventId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.EVENT_ID));
                int rowIndex = cursor.getInt(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.ROW_INDEX));
                int type = cursor.getInt(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.TYPE));
                String servicePackageName = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                EventsContract.EventsEntry.SERVICE_PACKAGE_NAME));
                byte[] eventData = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(EventsContract.EventsEntry.EVENT_DATA));
                long eventTimeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(eventTimeMillisCol));
                long queryId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_ID));
                byte[] queryData = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(QueriesContract.QueriesEntry.QUERY_DATA));
                long queryTimeMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(queryTimeMillisCol));
                joinedEventList.add(new JoinedEvent.Builder()
                        .setEventId(eventId)
                        .setRowIndex(rowIndex)
                        .setType(type)
                        .setEventData(eventData)
                        .setEventTimeMillis(eventTimeMillis)
                        .setQueryId(queryId)
                        .setQueryData(queryData)
                        .setQueryTimeMillis(queryTimeMillis)
                        .setServicePackageName(servicePackageName)
                        .build()
                );
            }
        } catch (IllegalArgumentException e) {
            sLogger.e(e, TAG + ": Failed parse resulting query of join statement");
            return new ArrayList<>();
        }
        return joinedEventList;
    }

    /**
     * Deletes all eventStates for the given packageName
     *
     * @return true if the delete executed successfully, false otherwise.
     */
    public boolean deleteEventState(String packageName) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            String selection = EventStateContract.EventStateEntry.SERVICE_PACKAGE_NAME + " = ?";
            String[] selectionArgs = {packageName};
            db.delete(EventStateContract.EventStateEntry.TABLE_NAME, selection,
                    selectionArgs);
        } catch (Exception e) {
            sLogger.e(e, TAG + ": Failed to delete eventState for: " + packageName);
            return false;
        }
        return true;
    }

    /**
     * Deletes all events and queries older than the given timestamp
     *
     * @return true if the delete executed successfully, false otherwise.
     */
    public boolean deleteEventsAndQueries(long timestamp) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            // Delete from events table first to satisfy FK requirements.
            String eventsSelection = EventsContract.EventsEntry.TIME_MILLIS + " < ?";
            String[] eventsSelectionArgs = {String.valueOf(timestamp)};
            db.delete(EventsContract.EventsEntry.TABLE_NAME, eventsSelection,
                    eventsSelectionArgs);

            // Delete from queries table older than timestamp AND have no events left.
            String queriesSelection = QueriesContract.QueriesEntry.TIME_MILLIS + " < ?"
                    + " AND " + QueriesContract.QueriesEntry.QUERY_ID
                    + " NOT IN (SELECT " + EventsContract.EventsEntry.QUERY_ID
                    + " FROM " + EventsContract.EventsEntry.TABLE_NAME + ")";
            String[] queriesSelectionArgs = {String.valueOf(timestamp)};
            db.delete(QueriesContract.QueriesEntry.TABLE_NAME, queriesSelection,
                    queriesSelectionArgs);

            db.setTransactionSuccessful();
        } catch (Exception e) {
            sLogger.e(e, TAG + ": Failed to delete events and queries older than: " + timestamp);
            return false;
        } finally {
            db.endTransaction();
        }
        return true;
    }
}
