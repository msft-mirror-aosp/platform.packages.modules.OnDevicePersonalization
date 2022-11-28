/*
 * Copyright 2022 The Android Open Source Project
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

package android.ondevicepersonalization;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * Unit Tests of RemoteData API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RemoteDataTest {
    RemoteData mRemoteData = new RemoteDataImpl(
            IDataAccessService.Stub.asInterface(
                    new RemoteDataService()));
    CountDownLatch mLatch = new CountDownLatch(1);
    Map<String, byte[]> mResult;
    Exception mException;

    @Test
    public void testLookupSuccess() throws Exception {
        List<String> keys = new ArrayList<String>();
        keys.add("a");
        keys.add("c");
        keys.add("e");
        mRemoteData.lookup(
                keys,
                Executors.newSingleThreadExecutor(),
                new ResultReceiver());
        mLatch.await();
        HashMap<String, byte[]> expectedResult = new HashMap<String, byte[]>();
        expectedResult.put("a", new byte[] {1, 2, 3});
        expectedResult.put("c", new byte[] {7, 8, 9});
        assertEquals(expectedResult.keySet(), mResult.keySet());
        for (String key : expectedResult.keySet()) {
            assertArrayEquals(expectedResult.get(key), mResult.get(key));
        }
        assertEquals(null, mException);
    }

    @Test
    public void testLookupError() throws Exception {
        List<String> keys = new ArrayList<String>();
        keys.add("z");  // Triggers an expected error in the mock service.
        mRemoteData.lookup(
                keys,
                Executors.newSingleThreadExecutor(),
                new ResultReceiver());
        mLatch.await();
        assertEquals(null, mResult);
        assertTrue(mException instanceof OnDevicePersonalizationException);
        assertEquals(
                Constants.STATUS_INTERNAL_ERROR,
                ((OnDevicePersonalizationException) mException).getErrorCode());
    }

    public class ResultReceiver
            implements OutcomeReceiver<Map<String, byte[]>, Exception> {
        @Override
        public void onResult(Map<String, byte[]> result) {
            mResult = result;
            mLatch.countDown();
        }

        @Override
        public void onError(Exception e) {
            mException = e;
            mLatch.countDown();
        }
    }

    public static class RemoteDataService extends IDataAccessService.Stub {
        HashMap<String, byte[]> mContents = new HashMap<String, byte[]>();

        public RemoteDataService() {
            mContents.put("a", new byte[] {1, 2, 3});
            mContents.put("b", new byte[] {4, 5, 6});
            mContents.put("c", new byte[] {7, 8, 9});
        }

        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback) {
            if (operation != Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP) {
                try {
                    callback.onError(Constants.STATUS_INTERNAL_ERROR);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }
            String[] keys = params.getStringArray(Constants.EXTRA_LOOKUP_KEYS);
            if (keys.length == 1 && keys[0].equals("z")) {
                // Raise expected error.
                try {
                    callback.onError(Constants.STATUS_INTERNAL_ERROR);
                } catch (RemoteException e) {
                    // Ignored.
                }
                return;
            }
            HashMap<String, byte[]> dict = new HashMap<String, byte[]>();
            for (int i = 0; i < keys.length; ++i) {
                if (mContents.containsKey(keys[i])) {
                    dict.put(keys[i], mContents.get(keys[i]));
                }
            }
            Bundle result = new Bundle();
            result.putSerializable(Constants.EXTRA_RESULT, dict);
            try {
                callback.onSuccess(result);
            } catch (RemoteException e) {
                // Ignored.
            }
        }
    }
}
