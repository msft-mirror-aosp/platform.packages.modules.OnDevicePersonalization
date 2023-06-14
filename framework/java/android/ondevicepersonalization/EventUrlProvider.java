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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Generates event tracking URLs for a request.
 *
 * @hide
 */
public class EventUrlProvider {
    /** Return a 204 No Content HTTP response. */
    public static final int RESPONSE_TYPE_NO_CONTENT = 1;

    /** Redirect to the provided destination URL. */
    public static final int RESPONSE_TYPE_REDIRECT = 2;

    /** Return a 1x1 blank GIF image. */
    public static final int RESPONSE_TYPE_1X1_IMAGE = 3;

    private static final long ASYNC_TIMEOUT_MS = 1000;

    @NonNull private final IDataAccessService mDataAccessService;

    public EventUrlProvider(@NonNull IDataAccessService binder) {
        mDataAccessService = Objects.requireNonNull(binder);
    }

    /** Return an Event URL for a single event. */
    @NonNull public String getEventUrl(
            @NonNull PersistableBundle eventParams,
            int responseType,
            @Nullable String destinationUrl) throws OnDevicePersonalizationException {
        try {
            BlockingQueue<CallbackResult> asyncResult = new ArrayBlockingQueue<>(1);
            Bundle params = new Bundle();
            params.putParcelable(Constants.EXTRA_EVENT_PARAMS, eventParams);
            params.putInt(Constants.EXTRA_RESPONSE_TYPE, responseType);
            params.putString(Constants.EXTRA_DESTINATION_URL, destinationUrl);
            mDataAccessService.onRequest(
                    Constants.DATA_ACCESS_OP_GET_EVENT_URL,
                    params,
                    new IDataAccessServiceCallback.Stub() {
                        @Override
                        public void onSuccess(@NonNull Bundle result) {
                            asyncResult.add(new CallbackResult(result, 0));
                        }
                        @Override
                        public void onError(int errorCode) {
                            asyncResult.add(new CallbackResult(null, errorCode));
                        }
                });
            CallbackResult callbackResult =
                    asyncResult.poll(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Objects.requireNonNull(callbackResult);
            if (callbackResult.mErrorCode != 0) {
                throw new OnDevicePersonalizationException(callbackResult.mErrorCode);
            }
            Bundle result = Objects.requireNonNull(callbackResult.mResult);
            String url = Objects.requireNonNull(result.getString(Constants.EXTRA_RESULT));
            return url;
        } catch (InterruptedException | RemoteException e) {
            throw new OnDevicePersonalizationException(
                    Constants.STATUS_INTERNAL_ERROR, (Throwable) e);
        }
    }

    private static class CallbackResult {
        final Bundle mResult;
        final int mErrorCode;

        CallbackResult(Bundle result, int errorCode) {
            mResult = result;
            mErrorCode = errorCode;
        }
    }
}
