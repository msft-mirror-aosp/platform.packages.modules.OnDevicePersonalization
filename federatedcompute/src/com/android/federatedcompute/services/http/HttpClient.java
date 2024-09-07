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

package com.android.federatedcompute.services.http;

import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBlockingExecutor;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_OK_STATUS;

import android.annotation.NonNull;

import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.PhFlags;
import com.android.odp.module.common.HttpClientUtils;
import com.android.odp.module.common.OdpHttpRequest;
import com.android.odp.module.common.OdpHttpResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * The HTTP client to be used by the FederatedCompute to communicate with remote federated servers.
 */
public class HttpClient {

    interface HttpIOSupplier<T> {
        T get() throws IOException; // Declared to throw IOException
    }

    private static final String TAG = HttpClient.class.getSimpleName();
    private static final int NETWORK_CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(5);
    private static final int NETWORK_READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(30);
    private final Flags mFlags;

    public HttpClient() {
        mFlags = PhFlags.getInstance();
    }

    /**
     * Perform HTTP requests based on given information asynchronously with retries in case http
     * will return not OK response code.
     */
    @NonNull
    public ListenableFuture<OdpHttpResponse> performRequestAsyncWithRetry(OdpHttpRequest request) {
        return performCallableAsync(
                () -> performRequestWithRetry(() -> HttpClientUtils.performRequest(request)));
    }

    /**
     * Perform HTTP requests based on given information asynchronously with retries in case http
     * will return not OK response code. Payload will be saved directly into the file.
     */
    @NonNull
    public ListenableFuture<OdpHttpResponse> performRequestIntoFileAsyncWithRetry(
            OdpHttpRequest request) {
        return performCallableAsync(
                () -> performRequestWithRetry(() -> HttpClientUtils.performRequest(request, true)));
    }

    /**
     * Perform HTTP requests based on given information asynchronously with retries in case http
     * will return not OK response code.
     */
    @NonNull
    public ListenableFuture<OdpHttpResponse> performCallableAsync(
            Callable<OdpHttpResponse> callable) {
        try {
            return getBlockingExecutor().submit(callable);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    /** Perform HTTP requests based on given information with retries. */
    @NonNull
    @VisibleForTesting
    OdpHttpResponse performRequestWithRetry(HttpIOSupplier<OdpHttpResponse> supplier)
            throws IOException {
        OdpHttpResponse response = null;
        int retryLimit = mFlags.getHttpRequestRetryLimit();
        while (retryLimit > 0) {
            try {
                response = supplier.get();
                if (HTTP_OK_STATUS.contains(response.getStatusCode())) {
                    return response;
                }
                // we want to continue retry in case it is IO exception.
            } catch (IOException e) {
                // propagate IO exception after RETRY_LIMIT times attempt.
                if (retryLimit <= 1) {
                    throw e;
                }
            } finally {
                retryLimit--;
            }
        }
        return response;
    }
}
