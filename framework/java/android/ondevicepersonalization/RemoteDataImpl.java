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

package android.ondevicepersonalization;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.database.Cursor;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.OutcomeReceiver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** @hide */
public class RemoteDataImpl implements RemoteData {
    @NonNull
    IDataAccessService mDataAccessService;

    public RemoteDataImpl(@NonNull IDataAccessService binder) {
        mDataAccessService = Objects.requireNonNull(binder);
    }

    @Override
    public void lookup(
            @NonNull List<String> keys,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Map<String, byte[]>, Exception> receiver) {
        try {
            Bundle params = new Bundle();
            params.putStringArray(Constants.EXTRA_LOOKUP_KEYS, keys.toArray(new String[0]));
            mDataAccessService.onRequest(
                    Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP,
                    params,
                    new IDataAccessServiceCallback.Stub() {
                        @Override
                        public void onSuccess(@NonNull Bundle result) {
                            executor.execute(() -> {
                                try {
                                    HashMap<String, byte[]> data =
                                            (HashMap<String, byte[]>) result.getSerializable(
                                                    Constants.EXTRA_RESULT);
                                    receiver.onResult(data);
                                } catch (Exception e) {
                                    receiver.onError(e);
                                }
                            });
                        }

                        @Override
                        public void onError(int errorCode) {
                            executor.execute(() -> {
                                receiver.onError(new OnDevicePersonalizationException(errorCode));
                            });
                        }
                    });
        } catch (Exception e) {
            receiver.onError(e);
        }
    }

    @Override
    public void scan(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Cursor, Exception> receiver) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
