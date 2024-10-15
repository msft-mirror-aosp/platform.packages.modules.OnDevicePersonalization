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

import com.android.odp.module.common.http.HttpClientUtils;
import com.android.odp.module.common.http.HttpClientUtils.HttpMethod;
import com.android.odp.module.common.http.OdpHttpRequest;

import com.google.internal.federatedcompute.v1.ForwardingInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * A helper class to create {@link OdpHttpRequest} with base uri, request headers and compression
 * setting for federated compute.
 */
final class ProtocolRequestCreator {
    private final String mRequestBaseUri;
    private final HashMap<String, String> mHeaderList;

    ProtocolRequestCreator(String requestBaseUri, HashMap<String, String> headerList) {
        this.mRequestBaseUri = requestBaseUri;
        this.mHeaderList = headerList;
    }

    /**
     * Creates a {@link ProtocolRequestCreator} based on forwarding info. Validates and extracts the
     * base URI for the subsequent requests.
     */
    static ProtocolRequestCreator create(ForwardingInfo forwardingInfo) {
        if (forwardingInfo.getTargetUriPrefix().isEmpty()) {
            throw new IllegalArgumentException("Missing `ForwardingInfo.target_uri_prefix`");
        }
        HashMap<String, String> extraHeaders = new HashMap<>();
        extraHeaders.putAll(forwardingInfo.getExtraRequestHeadersMap());
        return new ProtocolRequestCreator(forwardingInfo.getTargetUriPrefix(), extraHeaders);
    }

    /** Creates a {@link OdpHttpRequest} with base uri and compression setting. */
    OdpHttpRequest createProtoRequest(
            String uri, HttpMethod httpMethod, byte[] requestBody, boolean isProtobufEncoded) {
        HashMap<String, String> extraHeaders = new HashMap<>();
        return createProtoRequest(uri, httpMethod, extraHeaders, requestBody, isProtobufEncoded);
    }

    /** Creates a {@link OdpHttpRequest} with base uri, request headers and compression setting. */
    OdpHttpRequest createProtoRequest(
            String uri,
            HttpMethod httpMethod,
            Map<String, String> extraHeaders,
            byte[] requestBody,
            boolean isProtobufEncoded) {
        HashMap<String, String> requestHeader = new HashMap<>();
        requestHeader.putAll(mHeaderList);
        requestHeader.putAll(extraHeaders);

        if (isProtobufEncoded && requestBody.length > 0) {
            requestHeader.put(
                    HttpClientUtils.CONTENT_TYPE_HDR, HttpClientUtils.PROTOBUF_CONTENT_TYPE);
        }
        return OdpHttpRequest.create(
                HttpClientUtils.joinBaseUriWithSuffix(mRequestBaseUri, uri),
                httpMethod,
                requestHeader,
                requestBody);
    }

}
