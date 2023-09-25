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

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertThrows;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.content.ContentValues;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Unit Tests of LogReader API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class LogReaderTest {

    LogReader mLogReader;

    @Before
    public void setup() {
        mLogReader = new LogReader(
                IDataAccessService.Stub.asInterface(
                        new LogReaderTest.LocalDataService()));
    }

    @Test
    public void testGetRequestIdsSuccess() {
        List<Long> result = mLogReader.getRequestIds(10, 100);
        assertEquals(1L, (long) result.get(0));
        assertEquals(2L, (long) result.get(1));
        assertEquals(3L, (long) result.get(2));
    }

    @Test
    public void testGetRequestIdsError() {
        // Triggers an expected error in the mock service.
        assertThrows(IllegalStateException.class, () -> mLogReader.getRequestIds(7, 100));
    }

    @Test
    public void testGetRequestIdsNegativeTimeError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getRequestIds(-1, 100));
    }

    @Test
    public void testGetRequestIdsBadTimeRangeError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getRequestIds(100, 100));
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getRequestIds(1000, 100));
    }

    @Test
    public void testGetEventIdsSuccess() {
        List<Long> result = mLogReader.getEventIds(10, 100);
        assertEquals(1L, (long) result.get(0));
        assertEquals(2L, (long) result.get(1));
        assertEquals(3L, (long) result.get(2));
    }

    @Test
    public void testGetEventIdsError() {
        // Triggers an expected error in the mock service.
        assertThrows(IllegalStateException.class, () -> mLogReader.getEventIds(7, 100));
    }

    @Test
    public void testGetEventIdsNegativeTimeError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getEventIds(-1, 100));
    }

    @Test
    public void testGetEventIdsInputError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getEventIds(100, 100));
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getEventIds(1000, 100));
    }

    @Test
    public void testGetEventIdsForRequestSuccess() {
        List<Long> result = mLogReader.getEventIdsForRequest(1);
        assertEquals(1L, (long) result.get(0));
        assertEquals(2L, (long) result.get(1));
        assertEquals(3L, (long) result.get(2));
    }

    @Test
    public void testGetEventIdsForRequestError() {
        // Triggers an expected error in the mock service.
        assertThrows(IllegalStateException.class, () -> mLogReader.getEventIdsForRequest(7));
    }

    @Test
    public void testGetEventIdsForRequestInputError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getEventIdsForRequest(-1));
    }

    @Test
    public void testGetRequestLogRecordSuccess() {
        RequestLogRecord record = mLogReader.getRequestLogRecord(1);
        assertEquals(1, (int) (record.getRows().get(0).getAsInteger("x")));
    }

    @Test
    public void testGetRequestLogRecordError() {
        // Triggers an expected error in the mock service.
        assertThrows(IllegalStateException.class, () -> mLogReader.getRequestLogRecord(7));
    }

    @Test
    public void testGetRequestLogRecordInputError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getRequestLogRecord(-1));
    }


    @Test
    public void testGetJoinedLogRecordSuccess() {
        JoinedLogRecord record = mLogReader.getJoinedLogRecord(1);
        assertEquals(1L, record.getEventTimeMillis());
        assertEquals(1L, record.getRequestTimeMillis());
    }

    @Test
    public void testGetJoinedLogRecordError() {
        // Triggers an expected error in the mock service.
        assertThrows(IllegalStateException.class, () -> mLogReader.getJoinedLogRecord(7));
    }

    @Test
    public void testGetJoinedLogRecordInputError() {
        assertThrows(IllegalArgumentException.class, () -> mLogReader.getJoinedLogRecord(-1));
    }


    public static class LocalDataService extends IDataAccessService.Stub {
        long[] mIds;

        public LocalDataService() {
            mIds = new long[]{1L, 2L, 3L};
        }

        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback) {
            if (operation == Constants.DATA_ACCESS_OP_GET_REQUEST_IDS
                    || operation == Constants.DATA_ACCESS_OP_EVENT_IDS) {
                long[] timestamps = params.getLongArray(Constants.EXTRA_LOOKUP_KEYS);
                if (timestamps[0] == 7) {
                    // Raise expected error.
                    try {
                        callback.onError(Constants.STATUS_INTERNAL_ERROR);
                    } catch (RemoteException e) {
                        // Ignored.
                    }
                    return;
                }

                Bundle result = new Bundle();
                result.putLongArray(Constants.EXTRA_RESULT, mIds);
                try {
                    callback.onSuccess(result);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }

            long id = params.getLong(Constants.EXTRA_LOOKUP_KEYS);
            if (id == 7) {
                // Raise expected error.
                try {
                    callback.onError(Constants.STATUS_INTERNAL_ERROR);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }
            Bundle result = new Bundle();
            if (operation == Constants.DATA_ACCESS_OP_GET_EVENT_IDS_FOR_REQUEST) {
                result.putSerializable(Constants.EXTRA_RESULT, mIds);
            }
            if (operation == Constants.DATA_ACCESS_OP_GET_REQUEST_LOG_RECORD) {
                ContentValues values = new ContentValues();
                values.put("x", 1);
                RequestLogRecord record = new RequestLogRecord.Builder()
                        .addRow(values)
                        .build();
                result.putParcelable(Constants.EXTRA_RESULT, record);
            }
            if (operation == Constants.DATA_ACCESS_OP_GET_JOINED_LOG_RECORD) {
                JoinedLogRecord record = new JoinedLogRecord.Builder()
                        .setEventTimeMillis(1L)
                        .setRequestTimeMillis(1L)
                        .build();
                result.putParcelable(Constants.EXTRA_RESULT, record);
            }
            try {
                callback.onSuccess(result);
            } catch (RemoteException e) {
                // Ignored.
            }
        }
    }
}
