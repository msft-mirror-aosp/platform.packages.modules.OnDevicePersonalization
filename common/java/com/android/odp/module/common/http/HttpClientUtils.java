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

import static com.android.odp.module.common.FileUtils.createTempFile;
import static com.android.odp.module.common.FileUtils.writeToFile;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableSet;
import com.google.internal.federatedcompute.v1.ResourceCapabilities;
import com.google.internal.federatedcompute.v1.ResourceCompressionFormat;
import com.google.protobuf.ByteString;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Shared utilities for http connections used by fcp and odp server requests. */
public class HttpClientUtils {
    private static final String TAG = HttpClientUtils.class.getSimpleName();

    @VisibleForTesting
    static final int NETWORK_CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(5);

    @VisibleForTesting
    static final int NETWORK_READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(30);

    public static final String CONTENT_ENCODING_HDR = "Content-Encoding";

    public static final String ACCEPT_ENCODING_HDR = "Accept-Encoding";
    public static final String CONTENT_LENGTH_HDR = "Content-Length";
    public static final String GZIP_ENCODING_HDR = "gzip";
    public static final String CONTENT_TYPE_HDR = "Content-Type";
    public static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";
    public static final String OCTET_STREAM = "application/octet-stream";
    public static final ImmutableSet<Integer> HTTP_OK_STATUS = ImmutableSet.of(200, 201);

    public static final int DEFAULT_BUFFER_SIZE = 1024;
    public static final byte[] EMPTY_BODY = new byte[0];

    /** Returns the full URI based on the provided base URL and suffix. */
    public static String joinBaseUriWithSuffix(String baseUri, String suffix) {
        if (suffix.isEmpty() || !suffix.startsWith("/")) {
            throw new IllegalArgumentException("uri_suffix be empty or must have a leading '/'");
        }

        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }
        suffix = suffix.substring(1);
        return String.join("/", baseUri, suffix);
    }

    interface HttpURLConnectionSupplier {
        HttpURLConnection get() throws IOException; // Declared to throw IOException
    }

    /** Get the current client capabilities. */
    public static ResourceCapabilities getResourceCapabilities() {
        // Compression formats supported for resources downloaded via `Resource.uri`.
        // All clients are assumed to support uncompressed payloads.
        return ResourceCapabilities.newBuilder()
                .addSupportedCompressionFormats(
                        ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP)
                .build();
    }

    /** Compresses the input data using Gzip. */
    public static byte[] compressWithGzip(byte[] uncompressedData) {
        try (ByteString.Output outputStream = ByteString.newOutput(uncompressedData.length);
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(uncompressedData);
            gzipOutputStream.finish();
            return outputStream.toByteString().toByteArray();
        } catch (IOException e) {
            LogUtil.e(TAG, "Failed to compress using Gzip");
            throw new IllegalStateException("Failed to compress using Gzip", e);
        }
    }

    /** Un-compresses the input data using Gzip. */
    public static byte[] uncompressWithGzip(byte[] data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                GZIPInputStream gzip = new GZIPInputStream(inputStream);
                ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            int length;
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            while ((length = gzip.read(buffer, 0, DEFAULT_BUFFER_SIZE)) > 0) {
                result.write(buffer, 0, length);
            }
            return result.toByteArray();
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Failed to decompress the data.");
            throw new IllegalStateException("Failed to un-compress using Gzip", e);
        }
    }

    /** Calculates total bytes are sent via network based on provided http request. */
    public static long getTotalSentBytes(OdpHttpRequest request) {
        long totalBytes = 0;
        totalBytes +=
                request.getHttpMethod().name().length()
                        + " ".length()
                        + request.getUri().length()
                        + " HTTP/1.1\r\n".length();
        for (String key : request.getExtraHeaders().keySet()) {
            totalBytes +=
                    key.length()
                            + ": ".length()
                            + request.getExtraHeaders().get(key).length()
                            + "\r\n".length();
        }
        if (request.getExtraHeaders().containsKey(CONTENT_LENGTH_HDR)) {
            totalBytes += Long.parseLong(request.getExtraHeaders().get(CONTENT_LENGTH_HDR));
        }
        return totalBytes;
    }

    /** Calculates total bytes are received via network based on provided http response. */
    public static long getTotalReceivedBytes(OdpHttpResponse response) {
        long totalBytes = 0;
        boolean foundContentLengthHdr = false;
        for (Map.Entry<String, List<String>> header : response.getHeaders().entrySet()) {
            if (header.getKey() == null) {
                continue;
            }
            for (String headerValue : header.getValue()) {
                totalBytes += header.getKey().length() + ": ".length();
                totalBytes += headerValue == null ? 0 : headerValue.length();
            }
            // Uses Content-Length header to estimate total received bytes which is the most
            // accurate.
            if (header.getKey().equals(CONTENT_LENGTH_HDR)) {
                totalBytes += Long.parseLong(header.getValue().get(0));
                foundContentLengthHdr = true;
            }
        }
        if (!foundContentLengthHdr) {
            if (response.getPayload() != null) {
                totalBytes += response.getPayload().length;
            } else if (response.getPayloadFileName() != null) {
                totalBytes += response.getDownloadedPayloadSize();
            }
        }
        return totalBytes;
    }

    /** Opens a {@link URLConnection} to the specified URL with default timeouts. */
    @VisibleForTesting
    @NonNull
    static URLConnection setup(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(NETWORK_CONNECT_TIMEOUT_MS);
        urlConnection.setReadTimeout(NETWORK_READ_TIMEOUT_MS);
        return urlConnection;
    }

    /** Perform HTTP requests based on given information and returns the {@link OdpHttpResponse}. */
    @NonNull
    public static OdpHttpResponse performRequest(OdpHttpRequest request) throws IOException {
        return performRequest(request, /* savePayloadIntoFile= */ false);
    }

    /** Perform HTTP requests based on given information and returns the {@link OdpHttpResponse}. */
    @NonNull
    public static OdpHttpResponse performRequest(
            OdpHttpRequest request, boolean savePayloadIntoFile) throws IOException {
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

        return performRequest(request, () -> (HttpURLConnection) setup(url), savePayloadIntoFile);
    }

    @NonNull
    @VisibleForTesting
    static OdpHttpResponse performRequest(
            OdpHttpRequest request,
            HttpURLConnectionSupplier urlConnectionProvider,
            boolean savePayloadIntoFile)
            throws IOException {
        HttpURLConnection urlConnection;
        try {
            urlConnection = urlConnectionProvider.get();
        } catch (Exception e) {
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
                OdpHttpResponse.Builder builder =
                        new OdpHttpResponse.Builder()
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
                return new OdpHttpResponse.Builder()
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

    private static long saveIntoFile(String fileName, @Nullable InputStream in, long contentLength)
            throws IOException {
        if (contentLength == 0) {
            return 0;
        }
        try (InputStream bufIn = new BufferedInputStream(in)) {
            // Process download resource.
            long downloadedSize = writeToFile(fileName, bufIn);
            return downloadedSize;
        }
    }

    private static byte[] getByteArray(@Nullable InputStream in, long contentLength)
            throws IOException {
        if (contentLength == 0) {
            return EMPTY_BODY;
        }
        try {
            // TODO(b/297952090): evaluate the large file download.
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
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

    private HttpClientUtils() {}

    /** The supported http methods. */
    public enum HttpMethod {
        GET,
        POST,
        PUT,
    }
}
