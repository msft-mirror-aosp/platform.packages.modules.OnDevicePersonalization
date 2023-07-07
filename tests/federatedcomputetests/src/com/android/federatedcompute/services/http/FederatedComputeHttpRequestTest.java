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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

@RunWith(JUnit4.class)
public final class FederatedComputeHttpRequestTest {
    private static final byte[] PAYLOAD = "non_empty_request_body".getBytes();

    @Test
    public void testCreateRequestInvalidUri_fails() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FederatedComputeHttpRequest.create(
                                "http://invalid.com",
                                HttpMethod.GET,
                                new HashMap<>(),
                                PAYLOAD,
                                /* useCompression= */ false));
    }

    @Test
    public void testCreateWithInvalidRequestBody_fails() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FederatedComputeHttpRequest.create(
                                "https://valid.com",
                                HttpMethod.GET,
                                new HashMap<>(),
                                PAYLOAD,
                                /* useCompression= */ false));
    }

    @Test
    public void testCreateWithContentLengthHeader_fails() throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Length", "1234");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FederatedComputeHttpRequest.create(
                                "https://valid.com",
                                HttpMethod.POST,
                                headers,
                                PAYLOAD,
                                /* useCompression= */ false));
    }

    @Test
    public void createGetRequest_valid() throws Exception {
        String expectedUri = "https://valid.com";
        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        expectedUri,
                        HttpMethod.GET,
                        new HashMap<>(),
                        HttpClientUtil.EMPTY_BODY,
                        /* useCompression= */ false);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getBody()).isEqualTo(HttpClientUtil.EMPTY_BODY);
        assertTrue(request.getExtraHeaders().isEmpty());
    }

    @Test
    public void createGetRequestWithHeader_valid() throws Exception {
        String expectedUri = "https://valid.com";
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Foo", "Bar");

        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        expectedUri,
                        HttpMethod.GET,
                        expectedHeaders,
                        HttpClientUtil.EMPTY_BODY,
                        /* useCompression= */ false);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getExtraHeaders()).isEqualTo(expectedHeaders);
    }

    @Test
    public void createPostRequestWithoutBody_valid() throws Exception {
        String expectedUri = "https://valid.com";

        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        expectedUri,
                        HttpMethod.POST,
                        new HashMap<>(),
                        HttpClientUtil.EMPTY_BODY,
                        /* useCompression= */ false);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertTrue(request.getExtraHeaders().isEmpty());
        assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getBody()).isEqualTo(HttpClientUtil.EMPTY_BODY);
    }

    @Test
    public void createPostRequestWithBody_valid() throws Exception {
        String expectedUri = "https://valid.com";

        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        expectedUri,
                        HttpMethod.POST,
                        new HashMap<>(),
                        PAYLOAD,
                        /* useCompression= */ false);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getBody()).isEqualTo(PAYLOAD);
    }

    @Test
    public void createPostRequestWithBodyHeader_valid() throws Exception {
        String expectedUri = "https://valid.com";
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Foo", "Bar");

        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        expectedUri,
                        HttpMethod.POST,
                        expectedHeaders,
                        PAYLOAD,
                        /* useCompression= */ false);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getBody()).isEqualTo(PAYLOAD);
        assertThat(request.getExtraHeaders()).isEqualTo(expectedHeaders);
    }

    @Test
    public void createPostRequestWithCompression_valid() throws Exception {
        String expectedUri = "https://valid.com";

        FederatedComputeHttpRequest request =
                FederatedComputeHttpRequest.create(
                        expectedUri,
                        HttpMethod.POST,
                        new HashMap<>(),
                        PAYLOAD,
                        /* useCompression= */ true);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getBody()).isEqualTo(HttpClientUtil.compressWithGzip(PAYLOAD));
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(HttpClientUtil.CONTENT_ENCODING_HDR, HttpClientUtil.GZIP_ENCODING_HDR);
        expectedHeaders.put(HttpClientUtil.CONTENT_LENGTH_HDR, String.valueOf(42));
        assertThat(request.getExtraHeaders()).isEqualTo(expectedHeaders);
    }
}
