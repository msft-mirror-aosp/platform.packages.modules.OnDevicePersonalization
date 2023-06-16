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
 * Generates event tracking URLs for a request. The {@link IsolatedComputationService} can
 * embed these URLs in the HTML output. When the HTML is rendered, ODP will intercept requests
 * to these URLs, call {@link IsolatedComputationCallback#onEvent}, and log the returned output
 * in the EVENTS table.
 *
 * @hide
 */
public class EventUrlProvider {
    private static final long ASYNC_TIMEOUT_MS = 1000;

    @NonNull private final IDataAccessService mDataAccessService;

    public EventUrlProvider(@NonNull IDataAccessService binder) {
        mDataAccessService = Objects.requireNonNull(binder);
    }

    /**
     * Creates an event tracking URL that returns the provided response. Returns HTTP Status
     * 200 (OK) if the response data is not empty. Returns HTTP Status 204 (No Content) if the
     * response data is empty.
     *
     * @param eventParams The data to be passed to {@link IsolatedComputationCallback#onEvent}
     *     when the event occurs.
     * @param responseData The content to be returned to the WebView when the URL is fetched.
     * @param mimeType The Mime Type of the URL response.
     * @return An ODP event URL that can be inserted into a WebView.
     */
    @NonNull public String getEventTrackingUrl(
            @NonNull PersistableBundle eventParams,
            @Nullable byte[] responseData,
            @Nullable String mimeType) throws OnDevicePersonalizationException {
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_EVENT_PARAMS, eventParams);
        params.putByteArray(Constants.EXTRA_RESPONSE_DATA, responseData);
        params.putString(Constants.EXTRA_MIME_TYPE, mimeType);
        return getUrl(params);
    }

    /**
     * Creates an event tracking URL that redirects to the provided destination URL.
     *
     * @param eventParams The data to be passed to {@link IsolatedComputationCallback#onEvent}
     *     when the event occurs
     * @param destinationUrl The URL to redirect to.
     * @return An ODP event URL that can be inserted into a WebView.
     */
    @NonNull public String getEventTrackingUrlWithRedirect(
            @NonNull PersistableBundle eventParams,
            @Nullable String destinationUrl) throws OnDevicePersonalizationException {
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_EVENT_PARAMS, eventParams);
        params.putString(Constants.EXTRA_DESTINATION_URL, destinationUrl);
        return getUrl(params);
    }

    @NonNull private String getUrl(@NonNull Bundle params)
            throws OnDevicePersonalizationException {
        try {
            BlockingQueue<CallbackResult> asyncResult = new ArrayBlockingQueue<>(1);

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
