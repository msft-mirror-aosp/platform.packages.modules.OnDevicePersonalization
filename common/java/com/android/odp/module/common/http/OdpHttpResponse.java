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

import static com.android.odp.module.common.http.HttpClientUtils.CONTENT_ENCODING_HDR;
import static com.android.odp.module.common.http.HttpClientUtils.GZIP_ENCODING_HDR;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OdpHttpResponse {
    private Integer mStatusCode;
    private Map<String, List<String>> mHeaders = new HashMap<>();
    private byte[] mPayload;
    private String mPayloadFileName;
    private long mDownloadedPayloadSize;

    private OdpHttpResponse() {}

    public int getStatusCode() {
        return mStatusCode;
    }

    @NonNull
    public Map<String, List<String>> getHeaders() {
        return mHeaders;
    }

    @Nullable
    public byte[] getPayload() {
        return mPayload;
    }

    @Nullable
    public String getPayloadFileName() {
        return mPayloadFileName;
    }

    public long getDownloadedPayloadSize() {
        return mDownloadedPayloadSize;
    }

    /** Returns whether http response body is compressed with gzip. */
    public boolean isResponseCompressed() {
        if (mHeaders.containsKey(CONTENT_ENCODING_HDR)) {
            for (String format : mHeaders.get(CONTENT_ENCODING_HDR)) {
                if (format.contains(GZIP_ENCODING_HDR)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Builder for {@link OdpHttpResponse}. */
    public static final class Builder {
        private final OdpHttpResponse mHttpResponse;

        /** Default constructor of {@link OdpHttpResponse}. */
        public Builder() {
            mHttpResponse = new OdpHttpResponse();
        }

        /** Set the status code of http response. */
        public Builder setStatusCode(int statusCode) {
            mHttpResponse.mStatusCode = statusCode;
            return this;
        }

        /** Set headers of http response. */
        public Builder setHeaders(Map<String, List<String>> headers) {
            mHttpResponse.mHeaders = headers;
            return this;
        }

        /** Set payload of http response. */
        public Builder setPayload(byte[] payload) {
            mHttpResponse.mPayload = payload;
            return this;
        }

        /** Set payload file name where payload is saved. */
        public Builder setPayloadFileName(String fileName) {
            mHttpResponse.mPayloadFileName = fileName;
            return this;
        }

        /** Set payload file name where payload is saved. */
        public Builder setDownloadedPayloadSize(long downloadedSize) {
            mHttpResponse.mDownloadedPayloadSize = downloadedSize;
            return this;
        }

        /** Build {@link OdpHttpResponse}. */
        public OdpHttpResponse build() {
            if (mHttpResponse.mStatusCode == null) {
                throw new IllegalArgumentException("Empty status code.");
            }
            return mHttpResponse;
        }
    }
}
