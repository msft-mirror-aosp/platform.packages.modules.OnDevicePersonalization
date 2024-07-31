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
import static com.android.federatedcompute.services.common.FileUtils.createTempFile;
import static com.android.federatedcompute.services.common.FileUtils.writeToFile;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_OK_STATUS;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.PhFlags;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * The HTTP client to be used by the FederatedCompute to communicate with remote federated servers.
 */
public class HttpClient {

    interface HttpIOSupplier<T> {
        T get() throws IOException; // Declares to throw IOException
    }

    private static final String TAG = HttpClient.class.getSimpleName();
    private static final int NETWORK_CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(5);
    private static final int NETWORK_READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(30);
    private final Flags mFlags;

    public HttpClient() {
        mFlags = PhFlags.getInstance();
    }

    @NonNull
    @VisibleForTesting
    URLConnection setup(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(NETWORK_CONNECT_TIMEOUT_MS);
        urlConnection.setReadTimeout(NETWORK_READ_TIMEOUT_MS);
        return urlConnection;
    }

    /**
     * Perform HTTP requests based on given information asynchronously with retries in case http
     * will return not OK response code.
     */
    @NonNull
    public ListenableFuture<FederatedComputeHttpResponse> performRequestAsyncWithRetry(
            FederatedComputeHttpRequest request) {
        return performCallableAsync(() -> performRequestWithRetry(() -> performRequest(request)));
    }

    /**
     * Perform HTTP requests based on given information asynchronously with retries in case http
     * will return not OK response code. Payload will be saved directly into the file.
     */
    @NonNull
    public ListenableFuture<FederatedComputeHttpResponse> performRequestIntoFileAsyncWithRetry(
            FederatedComputeHttpRequest request) {
        return performCallableAsync(
                () -> performRequestWithRetry(() -> performRequest(request, true)));
    }

    /**
     * Perform HTTP requests based on given information asynchronously with retries in case http
     * will return not OK response code.
     */
    @NonNull
    public ListenableFuture<FederatedComputeHttpResponse> performCallableAsync(
            Callable<FederatedComputeHttpResponse> callable) {
        try {
            return getBlockingExecutor().submit(callable);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    /** Perform HTTP requests based on given information with retries. */
    @NonNull
    @VisibleForTesting
    FederatedComputeHttpResponse performRequestWithRetry(
            HttpIOSupplier<FederatedComputeHttpResponse> supplier) throws IOException {
        FederatedComputeHttpResponse response = null;
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

    /** Perform HTTP requests based on given information. */
    @NonNull
    @VisibleForTesting
    FederatedComputeHttpResponse performRequest(FederatedComputeHttpRequest request)
            throws IOException {
        return performRequest(request, false);
    }

    /** Perform HTTP requests based on given information. */
    @NonNull
    @VisibleForTesting
    FederatedComputeHttpResponse performRequest(FederatedComputeHttpRequest request,
            boolean savePayloadIntoFile)
            throws IOException {
        if (request.getUri() == null || request.getHttpMethod() == null) {
            LogUtil.e(TAG, "Endpoint or http method is empty");
            throw new IllegalArgumentException("Endpoint or http method is empty");
        }

        URL url;
        try {
            url = new URL(request.getUri());
        } catch (MalformedURLException e) {
            LogUtil.e(TAG, e, "Malformed registration target URL");
            throw new IllegalArgumentException("Malformed registration target URL", e);
        }

        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) setup(url);
        } catch (IOException e) {
            LogUtil.e(TAG, e, "Failed to open target URL");
            throw new IOException("Failed to open target URL", e);
        }

        try {
            urlConnection.setRequestMethod(request.getHttpMethod().name());
            urlConnection.setInstanceFollowRedirects(true);

            if (request.getExtraHeaders() != null && !request.getExtraHeaders().isEmpty()) {
                for (Map.Entry<String, String> entry : request.getExtraHeaders().entrySet()) {
                    urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (request.getBody() != null && request.getBody().length > 0) {
                urlConnection.setDoOutput(true);
                try (BufferedOutputStream out =
                        new BufferedOutputStream(urlConnection.getOutputStream())) {
                    out.write(request.getBody());
                }
            }

            int responseCode = urlConnection.getResponseCode();
            if (HTTP_OK_STATUS.contains(responseCode)) {
                FederatedComputeHttpResponse.Builder builder =
                        new FederatedComputeHttpResponse.Builder()
                                .setHeaders(urlConnection.getHeaderFields())
                                .setStatusCode(responseCode);
                if (savePayloadIntoFile) {
                    String inputFile = createTempFile("input", ".tmp");
                    long downloadedSize =
                            saveIntoFile(
                                    inputFile,
                                    urlConnection.getInputStream(),
                                    urlConnection.getContentLengthLong());
                    if (downloadedSize != 0) {
                        builder.setPayloadFileName(inputFile);
                        builder.setDownloadedPayloadSize(downloadedSize);
                    }
                } else {
                    builder.setPayload(
                            getByteArray(
                                    urlConnection.getInputStream(),
                                    urlConnection.getContentLengthLong()));
                }
                return builder.build();
            } else {
                return new FederatedComputeHttpResponse.Builder()
                        .setPayload(
                                getByteArray(
                                        urlConnection.getErrorStream(),
                                        urlConnection.getContentLengthLong()))
                        .setHeaders(urlConnection.getHeaderFields())
                        .setStatusCode(responseCode)
                        .build();
            }
        } catch (IOException e) {
            LogUtil.e(TAG, e, "Failed to get registration response");
            throw new IOException("Failed to get registration response", e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private static long saveIntoFile(String fileName,
            @Nullable InputStream in, long contentLength)
            throws IOException {
        if (contentLength == 0) {
            return 0;
        }
        try (in) {
            // Process download resource.
            long downloadedSize = writeToFile(fileName, in);
            LogUtil.d(TAG, "Downloaded data file size: %d", downloadedSize);
            return downloadedSize;
        }
    }

    private static byte[] getByteArray(@Nullable InputStream in, long contentLength)
            throws IOException {
        if (contentLength == 0) {
            return HttpClientUtil.EMPTY_BODY;
        }
        try {
            // TODO(b/297952090): evaluate the large file download.
            byte[] buffer = new byte[HttpClientUtil.DEFAULT_BUFFER_SIZE];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
