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

import com.google.common.collect.ImmutableSet;

import org.json.JSONObject;

/** Utility class containing http related variable e.g. headers, method. */
public final class HttpClientUtil {
    public static final String CONTENT_ENCODING_HDR = "Content-Encoding";

    public static final String ACCEPT_ENCODING_HDR = "Accept-Encoding";
    public static final String CONTENT_LENGTH_HDR = "Content-Length";
    public static final String GZIP_ENCODING_HDR = "gzip";
    public static final ImmutableSet<Integer> HTTP_OK_STATUS = ImmutableSet.of(200, 201);

    public static final Integer HTTP_UNAUTHENTICATED_STATUS = 401;

    public static final Integer HTTP_UNAUTHORIZED_STATUS = 403;

    public static final ImmutableSet<Integer> HTTP_OK_OR_UNAUTHENTICATED_STATUS =
            ImmutableSet.of(200, 201, 401);

    // This key indicates the key attestation record used for authentication.
    public static final String ODP_AUTHENTICATION_KEY = "odp-authentication-key";

    // This key indicates a UUID as a verified token for the device.
    public static final String ODP_AUTHORIZATION_KEY = "odp-authorization-key";

    public static final String ODP_IDEMPOTENCY_KEY = "odp-idempotency-key";

    public static final String FCP_OWNER_ID_DIGEST = "fcp-owner-id-digest";

    public static final byte[] EMPTY_BODY = new byte[0];

    static final class FederatedComputePayloadDataContract {
        public static final String KEY_ID = "keyId";

        public static final String ENCRYPTED_PAYLOAD = "encryptedPayload";

        public static final String ASSOCIATED_DATA_KEY = "associatedData";

        public static final byte[] ASSOCIATED_DATA = new JSONObject().toString().getBytes();
    }

    private HttpClientUtil() {}
}
