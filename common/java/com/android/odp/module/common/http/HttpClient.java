/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.odp.module.common.http;

import static com.android.odp.module.common.http.HttpClientUtils.HTTP_OK_STATUS;

import android.annotation.NonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * The HTTP client to be used by FederatedCompute and ODP services/jobs to communicate with remote
 * servers.
 */
public class HttpClient {

    interface HttpIOSupplier<T> {
        T get() throws IOException; // Declared to throw IOException
    }

    private final int mRetryLimit;

    /** The executor to use for making http requests. */
    private final ListeningExecutorService mBlockingExecutor;

    public HttpClient(int retryLimit, ListeningExecutorService blockingExecutor) {
        mRetryLimit = retryLimit;
        mBlockingExecutor = blockingExecutor;
    }

    /**
     * Perform HTTP requests based on given {@link OdpHttpRequest} asynchronously with configured
     * number of retries.
     *
     * <p>Retry limit provided during construction is used in case http does not return {@code OK}
     * response code.
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
    private ListenableFuture<OdpHttpResponse> performCallableAsync(
            Callable<OdpHttpResponse> callable) {
        try {
            return mBlockingExecutor.submit(callable);
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
        int retryLimit = mRetryLimit;
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
