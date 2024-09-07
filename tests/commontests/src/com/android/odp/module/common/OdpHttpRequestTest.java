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

package com.android.odp.module.common;

import static com.android.odp.module.common.HttpClientUtils.ACCEPT_ENCODING_HDR;
import static com.android.odp.module.common.HttpClientUtils.GZIP_ENCODING_HDR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

@RunWith(JUnit4.class)
public final class OdpHttpRequestTest {
    private static final byte[] PAYLOAD = "non_empty_request_body".getBytes();

    @Test
    public void testCreateRequestInvalidUri_fails() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OdpHttpRequest.create(
                                "http://invalid.com",
                                HttpClientUtils.HttpMethod.GET,
                                new HashMap<>(),
                                PAYLOAD));
    }

    @Test
    public void testCreateWithInvalidRequestBody_fails() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OdpHttpRequest.create(
                                "https://valid.com",
                                HttpClientUtils.HttpMethod.GET,
                                new HashMap<>(),
                                PAYLOAD));
    }

    @Test
    public void testCreateWithContentLengthHeader_fails() throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Length", "1234");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OdpHttpRequest.create(
                                "https://valid.com",
                                HttpClientUtils.HttpMethod.POST,
                                headers,
                                PAYLOAD));
    }

    @Test
    public void createGetRequest_valid() throws Exception {
        String expectedUri = "https://valid.com";
        OdpHttpRequest request =
                OdpHttpRequest.create(
                        expectedUri,
                        HttpClientUtils.HttpMethod.GET,
                        new HashMap<>(),
                        HttpClientUtils.EMPTY_BODY);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpClientUtils.HttpMethod.GET);
        assertThat(request.getBody()).isEqualTo(HttpClientUtils.EMPTY_BODY);
        assertTrue(request.getExtraHeaders().isEmpty());
    }

    @Test
    public void createGetRequestWithHeader_valid() throws Exception {
        String expectedUri = "https://valid.com";
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Foo", "Bar");

        OdpHttpRequest request =
                OdpHttpRequest.create(
                        expectedUri,
                        HttpClientUtils.HttpMethod.GET,
                        expectedHeaders,
                        HttpClientUtils.EMPTY_BODY);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getExtraHeaders()).isEqualTo(expectedHeaders);
    }

    @Test
    public void createPostRequestWithoutBody_valid() {
        String expectedUri = "https://valid.com";

        OdpHttpRequest request =
                OdpHttpRequest.create(
                        expectedUri,
                        HttpClientUtils.HttpMethod.POST,
                        new HashMap<>(),
                        HttpClientUtils.EMPTY_BODY);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertTrue(request.getExtraHeaders().isEmpty());
        assertThat(request.getHttpMethod()).isEqualTo(HttpClientUtils.HttpMethod.POST);
        assertThat(request.getBody()).isEqualTo(HttpClientUtils.EMPTY_BODY);
    }

    @Test
    public void createPostRequestWithBody_valid() {
        String expectedUri = "https://valid.com";

        OdpHttpRequest request =
                OdpHttpRequest.create(
                        expectedUri, HttpClientUtils.HttpMethod.POST, new HashMap<>(), PAYLOAD);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpClientUtils.HttpMethod.POST);
        assertThat(request.getBody()).isEqualTo(PAYLOAD);
    }

    @Test
    public void createPostRequestWithBodyHeader_valid() {
        String expectedUri = "https://valid.com";
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Foo", "Bar");

        OdpHttpRequest request =
                OdpHttpRequest.create(
                        expectedUri, HttpClientUtils.HttpMethod.POST, expectedHeaders, PAYLOAD);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpClientUtils.HttpMethod.POST);
        assertThat(request.getBody()).isEqualTo(PAYLOAD);
        assertThat(request.getExtraHeaders()).isEqualTo(expectedHeaders);
    }

    @Test
    public void createGetRequestWithAcceptCompression_valid() {
        String expectedUri = "https://valid.com";
        HashMap<String, String> headerList = new HashMap<>();
        headerList.put(ACCEPT_ENCODING_HDR, GZIP_ENCODING_HDR);
        OdpHttpRequest request =
                OdpHttpRequest.create(
                        expectedUri, HttpClientUtils.HttpMethod.POST, headerList, PAYLOAD);

        assertThat(request.getUri()).isEqualTo(expectedUri);
        assertThat(request.getHttpMethod()).isEqualTo(HttpClientUtils.HttpMethod.POST);
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(HttpClientUtils.CONTENT_LENGTH_HDR, String.valueOf(22));
        expectedHeaders.put(ACCEPT_ENCODING_HDR, GZIP_ENCODING_HDR);
        assertThat(request.getExtraHeaders()).isEqualTo(expectedHeaders);
    }
}
