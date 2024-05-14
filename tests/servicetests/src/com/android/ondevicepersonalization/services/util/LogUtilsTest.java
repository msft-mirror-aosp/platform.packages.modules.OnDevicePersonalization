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

package com.android.ondevicepersonalization.services.util;

import static org.junit.Assert.assertEquals;

import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;

import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class LogUtilsTest {
    private static final String APP = "com.example.app";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final ComponentName mService =
            ComponentName.createRelative(mContext.getPackageName(), ".ServiceClass");
    private final OnDevicePersonalizationDbHelper mDbHelper =
            OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testNoData() throws Exception {
        assertEquals(
                -1L,
                LogUtils.writeLogRecords(
                        mContext,
                        APP,
                        mService,
                        null,
                        Collections.emptyList()).get().longValue());
    }

    @Test
    public void testWriteQuery() throws Exception {
        long queriesSizeBefore = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeBefore = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        RequestLogRecord requestLogRecord =
                new RequestLogRecord.Builder()
                        .addRow(new ContentValues())
                        .build();
        long queryId = LogUtils.writeLogRecords(
                mContext,
                APP,
                mService,
                requestLogRecord,
                Collections.emptyList()).get().longValue();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(0, eventsSizeAfter - eventsSizeBefore);
    }

    @Test
    public void testWriteEmptyQuery() throws Exception {
        long queriesSizeBefore = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeBefore = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        RequestLogRecord requestLogRecord =
                new RequestLogRecord.Builder().build();
        long queryId = LogUtils.writeLogRecords(
                mContext,
                APP,
                mService,
                requestLogRecord,
                Collections.emptyList()).get().longValue();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(0, eventsSizeAfter - eventsSizeBefore);
    }

    @Test
    public void testWriteEventForExistingQuery() throws Exception {
        long queriesSizeBefore = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeBefore = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        RequestLogRecord requestLogRecord =
                new RequestLogRecord.Builder()
                        .addRow(new ContentValues())
                        .addRow(new ContentValues())
                        .build();
        long queryId = LogUtils.writeLogRecords(
                mContext,
                APP,
                mService,
                requestLogRecord,
                Collections.emptyList()).get().longValue();
        RequestLogRecord requestLogRecord2 =
                new RequestLogRecord.Builder()
                        .setRequestId(queryId)
                        .setRows(requestLogRecord.getRows())
                        .build();
        EventLogRecord eventLogRecord1 =
                new EventLogRecord.Builder()
                        .setType(1)
                        .setRowIndex(1)
                        .setData(new ContentValues())
                        .setRequestLogRecord(requestLogRecord2)
                        .build();
        EventLogRecord eventLogRecord2 =
                new EventLogRecord.Builder()
                        .setType(2)
                        .setRowIndex(1)
                        .setData(new ContentValues())
                        .setRequestLogRecord(requestLogRecord2)
                        .build();
        EventLogRecord eventLogRecord3 =
                new EventLogRecord.Builder()
                        .setType(1)  // Same as eventLogRecord1
                        .setRowIndex(1)
                        .setData(new ContentValues())
                        .setRequestLogRecord(requestLogRecord2)
                        .build();
        queryId = LogUtils.writeLogRecords(
                mContext,
                APP,
                mService,
                null,
                List.of(eventLogRecord1, eventLogRecord2, eventLogRecord3))
                .get().longValue();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(3, eventsSizeAfter - eventsSizeBefore);
    }

    @Test
    public void testWriteEventWithNewQuery() throws Exception {
        long queriesSizeBefore = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeBefore = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        RequestLogRecord requestLogRecord =
                new RequestLogRecord.Builder()
                        .addRow(new ContentValues())
                        .addRow(new ContentValues())
                        .build();
        EventLogRecord eventLogRecord =
                new EventLogRecord.Builder()
                        .setRowIndex(1)
                        .setData(new ContentValues())
                        .build();
        long queryId = LogUtils.writeLogRecords(
                mContext,
                APP,
                mService,
                requestLogRecord,
                List.of(eventLogRecord)).get().longValue();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(1, eventsSizeAfter - eventsSizeBefore);
    }

    private int getDbTableSize(String tableName) {
        return mDbHelper.getReadableDatabase().query(tableName, null,
                null, null, null, null, null).getCount();
    }
}
