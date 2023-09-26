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

package android.adservices.ondevicepersonalization;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.os.Bundle;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

/**
 * An interface to a read logs from REQUESTS and EVENTS
 *
 * Used as a Data Access Object for the REQUESTS and EVENTS table
 * {@link IsolatedService#getLogReader}.
 *
 * @hide
 */
public class LogReader {
    private static final String TAG = "LogReader";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    @NonNull
    private final IDataAccessService mDataAccessService;

    /** @hide */
    public LogReader(@NonNull IDataAccessService binder) {
        mDataAccessService = Objects.requireNonNull(binder);
    }


    /**
     * Retrieves a List of request ids for RequestLogRecords written by this IsolatedService within
     * the specified time range.
     */
    @WorkerThread
    @NonNull
    public List<Long> getRequestIds(long startTimeMillis, long endTimeMillis) {
        if (endTimeMillis <= startTimeMillis) {
            throw new IllegalArgumentException(
                    "endTimeMillis must be greater than startTimeMillis");
        }
        if (startTimeMillis < 0) {
            throw new IllegalArgumentException("startTimeMillis must be greater than 0");
        }
        Bundle params = new Bundle();
        params.putLongArray(Constants.EXTRA_LOOKUP_KEYS,
                new long[]{startTimeMillis, endTimeMillis});
        return handleListLookupRequest(Constants.DATA_ACCESS_OP_GET_REQUEST_IDS, params);
    }


    /**
     * Return event ids for EventLogRecords written by this
     * IsolatedService within the specified time range.
     */
    @WorkerThread
    @NonNull
    public List<Long> getEventIds(long startTimeMillis, long endTimeMillis) {
        if (endTimeMillis <= startTimeMillis) {
            throw new IllegalArgumentException(
                    "endTimeMillis must be greater than startTimeMillis");
        }
        if (startTimeMillis < 0) {
            throw new IllegalArgumentException("startTimeMillis must be greater than 0");
        }
        Bundle params = new Bundle();
        params.putLongArray(Constants.EXTRA_LOOKUP_KEYS,
                new long[]{startTimeMillis, endTimeMillis});
        return handleListLookupRequest(Constants.DATA_ACCESS_OP_EVENT_IDS, params);
    }

    /**
     * Return event ids for EventLogRecords that are associated with the
     * specified request id.
     */
    @WorkerThread
    @NonNull
    public List<Long> getEventIdsForRequest(long requestId) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be greater than 0");
        }
        Bundle params = new Bundle();
        params.putLong(Constants.EXTRA_LOOKUP_KEYS, requestId);
        return handleListLookupRequest(Constants.DATA_ACCESS_OP_GET_EVENT_IDS_FOR_REQUEST, params);
    }

    /**
     * Return the RequestLogRecord with the specified request id.
     */
    @WorkerThread
    public RequestLogRecord getRequestLogRecord(long requestId) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be greater than 0");
        }
        Bundle params = new Bundle();
        params.putLong(Constants.EXTRA_LOOKUP_KEYS, requestId);
        Bundle result = handleAsyncRequest(Constants.DATA_ACCESS_OP_GET_REQUEST_LOG_RECORD, params);
        RequestLogRecord data = result.getParcelable(
                Constants.EXTRA_RESULT, RequestLogRecord.class);
        if (null == data) {
            sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
            throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
        }
        return data;
    }

    /**
     * Return the EventLogRecord with the specified event id, joined with
     * its matching RequestLogRecord.
     */
    @WorkerThread
    public JoinedLogRecord getJoinedLogRecord(long eventId) {
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be greater than 0");
        }
        Bundle params = new Bundle();
        params.putLong(Constants.EXTRA_LOOKUP_KEYS, eventId);
        Bundle result = handleAsyncRequest(Constants.DATA_ACCESS_OP_GET_JOINED_LOG_RECORD, params);
        JoinedLogRecord data = result.getParcelable(
                Constants.EXTRA_RESULT, JoinedLogRecord.class);
        if (null == data) {
            sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
            throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
        }
        return data;
    }

    private Bundle handleAsyncRequest(int op, Bundle params) {
        try {
            BlockingQueue<Bundle> asyncResult = new ArrayBlockingQueue<>(1);
            mDataAccessService.onRequest(
                    op,
                    params,
                    new IDataAccessServiceCallback.Stub() {
                        @Override
                        public void onSuccess(@NonNull Bundle result) {
                            if (result != null) {
                                asyncResult.add(result);
                            } else {
                                asyncResult.add(Bundle.EMPTY);
                            }
                        }

                        @Override
                        public void onError(int errorCode) {
                            asyncResult.add(Bundle.EMPTY);
                        }
                    });
            return asyncResult.take();
        } catch (InterruptedException | RemoteException e) {
            sLogger.e(TAG + ": Failed to retrieve result", e);
            throw new IllegalStateException(e);
        }
    }

    private List<Long> handleListLookupRequest(int op, Bundle params) {
        Bundle result = handleAsyncRequest(op, params);
        long[] data = result.getLongArray(
                Constants.EXTRA_RESULT);
        if (null == data) {
            sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
            throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
        }
        return Arrays.stream(data)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
