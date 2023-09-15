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

package android.adservices.ondevicepersonalization;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.os.RemoteException;


import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** @hide */
public class RemoteDataImpl implements KeyValueStore {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "RemoteDataImpl";
    @NonNull
    IDataAccessService mDataAccessService;

    /** @hide */
    public RemoteDataImpl(@NonNull IDataAccessService binder) {
        mDataAccessService = Objects.requireNonNull(binder);
    }

    @Override @Nullable
    public byte[] get(@NonNull String key) {
        Objects.requireNonNull(key);
        try {
            BlockingQueue<Bundle> asyncResult = new ArrayBlockingQueue<>(1);
            Bundle params = new Bundle();
            params.putStringArray(Constants.EXTRA_LOOKUP_KEYS, new String[]{key});
            mDataAccessService.onRequest(
                    Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP,
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
            Bundle result = asyncResult.take();
            HashMap<String, byte[]> data = result.getSerializable(
                            Constants.EXTRA_RESULT, HashMap.class);
            if (null == data) {
                sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
                throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
            }
            return data.get(key);
        } catch (InterruptedException | RemoteException e) {
            sLogger.e(TAG + ": Failed to retrieve key from remoteData", e);
            throw new IllegalStateException(e);
        }
    }

    @Override @NonNull
    public Set<String> keySet() {
        try {
            BlockingQueue<Bundle> asyncResult = new ArrayBlockingQueue<>(1);
            mDataAccessService.onRequest(
                    Constants.DATA_ACCESS_OP_REMOTE_DATA_KEYSET,
                    Bundle.EMPTY,
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
            Bundle result = asyncResult.take();
            HashSet<String> resultSet =
                    result.getSerializable(Constants.EXTRA_RESULT, HashSet.class);
            if (null == resultSet) {
                sLogger.e(TAG + ": No EXTRA_RESULT was present in bundle");
                throw new IllegalStateException("Bundle missing EXTRA_RESULT.");
            }
            return resultSet;
        } catch (InterruptedException | RemoteException e) {
            sLogger.e(TAG + ": Failed to retrieve keySet from remoteData", e);
            throw new IllegalStateException(e);
        }
    }
}