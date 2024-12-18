/*
 * Copyright 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit Tests of LocalDataImpl API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class LocalDataTest {
    MutableKeyValueStore mLocalData;
    LocalDataService mLocalDataService;
    private static final long TIMEOUT_MILLI = 5000;

    @Before
    public void setup() {
        mLocalDataService = new LocalDataService();
        mLocalData = new LocalDataImpl(IDataAccessService.Stub.asInterface(mLocalDataService));
    }

    @Test
    public void testLookupSuccess() throws Exception {
        assertArrayEquals(new byte[] {1, 2, 3}, mLocalData.get("a"));
        assertArrayEquals(new byte[] {7, 8, 9}, mLocalData.get("c"));
        assertNull(mLocalData.get("e"));

        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode).isEqualTo(Constants.STATUS_SUCCESS);
    }

    @Test
    public void testLookupError() throws InterruptedException {
        // Triggers an expected error in the mock service.
        assertNull(mLocalData.get("z"));

        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode).isEqualTo(Constants.STATUS_SERVICE_FAILED);
    }

    @Test
    public void testLookupEmptyResult() throws Exception {
        // Triggers an expected error in the mock service.
        assertNull(mLocalData.get("empty"));

        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode)
                .isEqualTo(Constants.STATUS_SUCCESS_EMPTY_RESULT);
    }

    @Test
    public void testKeysetSuccess() throws InterruptedException {
        Set<String> expectedResult = new HashSet<>();
        expectedResult.add("a");
        expectedResult.add("b");
        expectedResult.add("c");

        assertThat(expectedResult).isEqualTo(mLocalData.keySet());
        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode).isEqualTo(Constants.STATUS_SUCCESS);
    }

    @Test
    public void testPutSuccess() throws Exception {
        assertArrayEquals(new byte[] {1, 2, 3}, mLocalData.put("a", new byte[10]));
        assertNull(mLocalData.put("e", new byte[] {1, 2, 3}));
        assertArrayEquals(new byte[] {1, 2, 3}, mLocalData.get("e"));

        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode).isEqualTo(Constants.STATUS_SUCCESS);
    }

    @Test
    public void testPutError() throws InterruptedException {
        // Triggers an expected error in the mock service.
        assertNull(mLocalData.put("z", new byte[10]));

        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode).isEqualTo(Constants.STATUS_SERVICE_FAILED);
    }

    @Test
    public void testRemoveSuccess() throws Exception {
        assertArrayEquals(new byte[] {1, 2, 3}, mLocalData.remove("a"));
        assertNull(mLocalData.remove("e"));
        assertNull(mLocalData.get("a"));

        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode).isEqualTo(Constants.STATUS_SUCCESS);
    }

    @Test
    public void testRemoveError() throws InterruptedException {
        // Triggers an expected error in the mock service.
        assertNull(mLocalData.remove("z"));

        assertTrue(
                "Failed to await countdown latch!",
                mLocalDataService.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertThat(mLocalDataService.mResponseCode).isEqualTo(Constants.STATUS_SERVICE_FAILED);
    }

    public static class LocalDataService extends IDataAccessService.Stub {
        HashMap<String, byte[]> mContents = new HashMap<String, byte[]>();
        int mResponseCode;
        CountDownLatch mLatch = new CountDownLatch(1);

        public LocalDataService() {
            mContents.put("a", new byte[] {1, 2, 3});
            mContents.put("b", new byte[] {4, 5, 6});
            mContents.put("c", new byte[] {7, 8, 9});
        }

        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback) {

            if (operation == Constants.DATA_ACCESS_OP_LOCAL_DATA_KEYSET) {
                Bundle result = new Bundle();
                result.putSerializable(Constants.EXTRA_RESULT,
                        new HashSet<>(mContents.keySet()));
                try {
                    callback.onSuccess(result);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }

            String key = params.getString(Constants.EXTRA_LOOKUP_KEYS);
            Objects.requireNonNull(key);
            if (key.equals("empty")) {
                // Raise expected error.
                try {
                    callback.onSuccess(null);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }
            if (key.equals("z")) {
                // Raise expected error.
                try {
                    callback.onError(Constants.STATUS_SERVICE_FAILED);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }
            ByteArrayParceledSlice parceledByteArray = params.getParcelable(
                    Constants.EXTRA_VALUE, ByteArrayParceledSlice.class);
            byte[] value = null;
            if (parceledByteArray != null) {
                value = parceledByteArray.getByteArray();
            }

            if (operation == Constants.DATA_ACCESS_OP_LOCAL_DATA_LOOKUP
                    || operation == Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT
                    || operation == Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE) {
                byte[] existingValue = null;
                if (mContents.containsKey(key)) {
                    existingValue = mContents.get(key);
                }
                if (operation == Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE) {
                    mContents.remove(key);
                }
                if (operation == Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT) {
                    mContents.put(key, value);
                }
                Bundle result = new Bundle();
                result.putParcelable(
                        Constants.EXTRA_RESULT, new ByteArrayParceledSlice(existingValue));
                try {
                    callback.onSuccess(result);
                } catch (RemoteException e) {
                    // Ignored.
                }
            }
        }

        @Override
        public void logApiCallStats(int apiName, long latencyMillis, int responseCode) {
            mLatch.countDown();
            mResponseCode = responseCode;
        }
    }
}
