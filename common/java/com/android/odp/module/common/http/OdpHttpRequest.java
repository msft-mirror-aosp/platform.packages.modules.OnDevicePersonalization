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

import static com.android.odp.module.common.http.HttpClientUtils.CONTENT_LENGTH_HDR;

import com.android.odp.module.common.http.HttpClientUtils.HttpMethod;

import java.util.Map;

/** Class to hold http requests for federated compute and other odp use-cases. */
public final class OdpHttpRequest {
    private static final String TAG = "FCPHttpRequest";
    private static final String HTTPS_SCHEMA = "https://";
    private static final String LOCAL_HOST_URI = "http://localhost:";

    private final String mUri;
    private final HttpMethod mHttpMethod;
    private final Map<String, String> mExtraHeaders;
    private final byte[] mBody;

    private OdpHttpRequest(
            String uri, HttpMethod httpMethod, Map<String, String> extraHeaders, byte[] body) {
        this.mUri = uri;
        this.mHttpMethod = httpMethod;
        this.mExtraHeaders = extraHeaders;
        this.mBody = body;
    }

    /** Creates a {@link OdpHttpRequest} based on given inputs. */
    public static OdpHttpRequest create(
            String uri, HttpMethod httpMethod, Map<String, String> extraHeaders, byte[] body) {
        if (!uri.startsWith(HTTPS_SCHEMA) && !uri.startsWith(LOCAL_HOST_URI)) {
            throw new IllegalArgumentException("Non-HTTPS URIs are not supported: " + uri);
        }
        if (extraHeaders.containsKey(CONTENT_LENGTH_HDR)) {
            throw new IllegalArgumentException("Content-Length header should not be provided!");
        }
        if (body.length > 0) {
            if (httpMethod != HttpMethod.POST && httpMethod != HttpMethod.PUT) {
                throw new IllegalArgumentException(
                        "Request method does not allow request mBody: " + httpMethod);
            }
            extraHeaders.put(CONTENT_LENGTH_HDR, String.valueOf(body.length));
        }
        return new OdpHttpRequest(uri, httpMethod, extraHeaders, body);
    }

    public String getUri() {
        return mUri;
    }

    public byte[] getBody() {
        return mBody;
    }

    public HttpMethod getHttpMethod() {
        return mHttpMethod;
    }

    public Map<String, String> getExtraHeaders() {
        return mExtraHeaders;
    }
}
