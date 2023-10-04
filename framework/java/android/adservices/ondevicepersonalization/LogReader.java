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
import android.os.Parcelable;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.OdpParceledListSlice;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
     * Retrieves a List of RequestLogRecords written by this IsolatedService within
     * the specified time range.
     */
    @WorkerThread
    @NonNull
    public List<RequestLogRecord> getRequests(long startTimeMillis, long endTimeMillis) {
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
        OdpParceledListSlice<RequestLogRecord> result =
                handleListLookupRequest(Constants.DATA_ACCESS_OP_GET_REQUESTS, params);
        return result.getList();
    }

    /**
     * Retrieves a List of EventLogRecord with its corresponding RequestLogRecord written by this
     * IsolatedService within the specified time range.
     */
    @WorkerThread
    public List<EventLogRecord> getJoinedEvents(long startTimeMillis, long endTimeMillis) {
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
        OdpParceledListSlice<EventLogRecord> result =
                handleListLookupRequest(Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS, params);
        return result.getList();
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

    private <T extends Parcelable> OdpParceledListSlice<T> handleListLookupRequest(int op,
            Bundle params) {
        Bundle result = handleAsyncRequest(op, params);
        try {
            OdpParceledListSlice<T> data = result.getParcelable(
                    Constants.EXTRA_RESULT, OdpParceledListSlice.class);
            if (null == data) {
                sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
                throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
            }
            return data;
        } catch (ClassCastException e) {
            throw new IllegalStateException("Failed to retrieve parceled list");
        }
    }
}
