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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@MockStatic(OdpStatsdLogger.class)
public final class LogUtilsTest {
    private static final String APP = "com.example.app";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final ComponentName mService =
            ComponentName.createRelative(mContext.getPackageName(), ".ServiceClass");
    private final OnDevicePersonalizationDbHelper mDbHelper =
            OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();
    @Mock private OdpStatsdLogger mMockOdpStatsdLogger;

    @Before
    public void setup(){
        when(OdpStatsdLogger.getInstance()).thenReturn(mMockOdpStatsdLogger);
    }

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
                        Constants.TASK_TYPE_EXECUTE,
                        mContext,
                        APP,
                        mService,
                        null,
                        Collections.emptyList()).get().longValue());
        verify(mMockOdpStatsdLogger)
                .logTraceEventStats(
                        anyInt(),
                        eq(Constants.EVENT_TYPE_WRITE_REQUEST_LOG),
                        eq(Constants.STATUS_REQUEST_LOG_IS_NULL),
                        anyLong(),
                        anyString());
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
                Constants.TASK_TYPE_EXECUTE,
                mContext,
                APP,
                mService,
                requestLogRecord,
                Collections.emptyList()).get();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(0, eventsSizeAfter - eventsSizeBefore);
        verify(mMockOdpStatsdLogger)
                .logTraceEventStats(
                        anyInt(),
                        eq(Constants.EVENT_TYPE_WRITE_REQUEST_LOG),
                        eq(Constants.STATUS_REQUEST_LOG_DB_SUCCESS),
                        anyLong(),
                        anyString());
    }

    @Test
    public void testWriteEmptyQuery() throws Exception {
        long queriesSizeBefore = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeBefore = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        RequestLogRecord requestLogRecord =
                new RequestLogRecord.Builder().build();
        long queryId = LogUtils.writeLogRecords(
                Constants.TASK_TYPE_EXECUTE,
                mContext,
                APP,
                mService,
                requestLogRecord,
                Collections.emptyList()).get();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(0, eventsSizeAfter - eventsSizeBefore);
        verify(mMockOdpStatsdLogger)
                .logTraceEventStats(
                        anyInt(),
                        eq(Constants.EVENT_TYPE_WRITE_REQUEST_LOG),
                        eq(Constants.STATUS_REQUEST_LOG_IS_EMPTY),
                        anyLong(),
                        anyString());
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
                Constants.TASK_TYPE_EXECUTE,
                mContext,
                APP,
                mService,
                requestLogRecord,
                Collections.emptyList()).get();
        verify(mMockOdpStatsdLogger)
                .logTraceEventStats(
                        anyInt(),
                        eq(Constants.EVENT_TYPE_WRITE_REQUEST_LOG),
                        eq(Constants.STATUS_REQUEST_LOG_DB_SUCCESS),
                        anyLong(),
                        anyString());
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
                        Constants.TASK_TYPE_EXECUTE,
                        mContext,
                        APP,
                        mService,
                        null,
                        List.of(eventLogRecord1, eventLogRecord2, eventLogRecord3))
                .get();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(3, eventsSizeAfter - eventsSizeBefore);
        verify(mMockOdpStatsdLogger)
                .logTraceEventStats(
                        anyInt(),
                        eq(Constants.EVENT_TYPE_WRITE_EVENT_LOG),
                        eq(Constants.STATUS_EVENT_LOG_DB_SUCCESS),
                        anyLong(),
                        anyString());
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
                Constants.TASK_TYPE_EXECUTE,
                mContext,
                APP,
                mService,
                requestLogRecord,
                List.of(eventLogRecord)).get();
        long queriesSizeAfter = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        long eventsSizeAfter = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        assertEquals(1, queriesSizeAfter - queriesSizeBefore);
        assertEquals(1, eventsSizeAfter - eventsSizeBefore);
        verify(mMockOdpStatsdLogger)
                .logTraceEventStats(
                        anyInt(),
                        eq(Constants.EVENT_TYPE_WRITE_REQUEST_LOG),
                        eq(Constants.STATUS_REQUEST_LOG_DB_SUCCESS),
                        anyLong(),
                        anyString());
        verify(mMockOdpStatsdLogger)
                .logTraceEventStats(
                        anyInt(),
                        eq(Constants.EVENT_TYPE_WRITE_EVENT_LOG),
                        eq(Constants.STATUS_EVENT_LOG_DB_SUCCESS),
                        anyLong(),
                        anyString());
    }

    private int getDbTableSize(String tableName) {
        return mDbHelper.getReadableDatabase().query(tableName, null,
                null, null, null, null, null).getCount();
    }
}
