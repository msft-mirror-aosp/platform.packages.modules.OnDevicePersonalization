/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;

import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/** Utilities for logging */
public class LogUtils {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "LogUtils";

    /** Writes the provided records to the REQUESTS and EVENTS tables. */
    public static ListenableFuture<Long> writeLogRecords(
            @NonNull Context context,
            @NonNull String appPackageName,
            @NonNull ComponentName service,
            @Nullable RequestLogRecord requestLogRecord,
            @NonNull List<EventLogRecord> eventLogRecords) {
        sLogger.d(TAG + ": writeLogRecords() started.");
        try {
            String serviceName = DbUtils.toTableValue(service);
            String certDigest =
                    PackageUtils.getCertDigest(
                            OnDevicePersonalizationApplication.getAppContext(),
                            service.getPackageName());
            EventsDao eventsDao = EventsDao.getInstance(context);
            // Insert query
            long queryId = -1;
            if (requestLogRecord != null) {
                List<ContentValues> rows = requestLogRecord.getRows();
                if (rows.isEmpty()) {
                    rows = List.of(new ContentValues());
                }
                byte[] queryData = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                        serviceName, certDigest, rows);
                Query query = new Query.Builder(
                        System.currentTimeMillis(),
                        appPackageName,
                        service,
                        certDigest,
                        queryData).build();
                queryId = eventsDao.insertQuery(query);
                if (queryId == -1) {
                    return Futures.immediateFailedFuture(
                            new RuntimeException("Failed to log query."));
                }
            }

            // Insert events
            List<Event> events = new ArrayList<>();
            for (EventLogRecord eventLogRecord : eventLogRecords) {
                RequestLogRecord parent;
                long parentRequestId;
                if (eventLogRecord.getRequestLogRecord() != null) {
                    parent = eventLogRecord.getRequestLogRecord();
                    parentRequestId = parent.getRequestId();
                } else {
                    parent = requestLogRecord;
                    parentRequestId = queryId;
                }
                // Verify requestLogRecord exists and has the corresponding rowIndex
                if (parent == null || parentRequestId <= 0
                        || eventLogRecord.getRowIndex() >= parent.getRows().size()) {
                    continue;
                }
                // Make sure query exists for package in QUERY table and
                // rowIndex <= written row count.
                Query queryRow = eventsDao.readSingleQueryRow(parentRequestId, service);
                if (queryRow == null || eventLogRecord.getRowIndex()
                        >= OnDevicePersonalizationFlatbufferUtils
                                .getContentValuesLengthFromQueryData(
                                        queryRow.getQueryData())) {
                    continue;
                }
                Event event = new Event.Builder()
                        .setEventData(OnDevicePersonalizationFlatbufferUtils.createEventData(
                                eventLogRecord.getData()))
                        .setQueryId(parentRequestId)
                        .setRowIndex(eventLogRecord.getRowIndex())
                        .setService(service)
                        .setTimeMillis(System.currentTimeMillis())
                        .setType(eventLogRecord.getType())
                        .build();
                events.add(event);
            }
            if (!eventsDao.insertEvents(events)) {
                return Futures.immediateFailedFuture(
                        new RuntimeException("Failed to log events."));
            }

            return Futures.immediateFuture(queryId);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private LogUtils() {}
}
