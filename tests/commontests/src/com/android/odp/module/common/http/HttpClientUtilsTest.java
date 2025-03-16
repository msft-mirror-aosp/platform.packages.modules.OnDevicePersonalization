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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.odp.module.common.http.HttpClientUtils.HttpMethod;
import com.android.odp.module.common.http.HttpClientUtils.HttpURLConnectionSupplier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpClientUtilsTest {
    private static final String TEST_URI = "https://valid.com";
    private static final Map<String, String> TEST_REQUEST_HEADERS = Map.of("Foo", "Bar");

    private static final OdpHttpRequest DEFAULT_GET_REQUEST =
            OdpHttpRequest.create(
                    "https://google.com",
                    HttpClientUtils.HttpMethod.GET,
                    new HashMap<>(),
                    HttpClientUtils.EMPTY_BODY);

    private static final Map<String, List<String>> TEST_RESPONSE_HEADERS =
            ImmutableMap.of(
                    "x-content", ImmutableList.of("1", "2"), "api-key", ImmutableList.of("xyz"));

    private static final OdpHttpResponse TEST_RESPONSE =
            new OdpHttpResponse.Builder()
                    .setStatusCode(200)
                    .setPayload("payload".getBytes(UTF_8))
                    .setHeaders(TEST_RESPONSE_HEADERS)
                    .build();

    private static final OdpHttpRequest TEST_EMPTY_REQUEST =
            OdpHttpRequest.create(
                    TEST_URI, HttpMethod.GET, TEST_REQUEST_HEADERS, HttpClientUtils.EMPTY_BODY);

    private static final String TEST_FAILURE_MESSAGE = "FAIL!";
    private static final String TEST_SUCCESS_MESSAGE = "Success!";
    @Mock private HttpURLConnectionSupplier mHttpURLConnectionSupplier;
    @Mock private HttpURLConnection mMockHttpURLConnection;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(mMockHttpURLConnection).when(mHttpURLConnectionSupplier).get();
    }

    @Test
    public void testGetTotalSentBytes_emptyBody() {
        assertThat(HttpClientUtils.getTotalSentBytes(TEST_EMPTY_REQUEST)).isEqualTo(42);
    }

    @Test
    public void testGetTotalReceivedBytes() {
        assertThat(HttpClientUtils.getTotalReceivedBytes(TEST_RESPONSE)).isEqualTo(43);
    }

    @Test
    public void testUnableToOpenconnection_returnFailure() throws Exception {
        OdpHttpRequest request =
                OdpHttpRequest.create(
                        "https://google.com",
                        HttpClientUtils.HttpMethod.POST,
                        new HashMap<>(),
                        HttpClientUtils.EMPTY_BODY);
        doThrow(new IOException()).when(mHttpURLConnectionSupplier).get();

        assertThrows(
                IOException.class,
                () -> HttpClientUtils.performRequest(request, mHttpURLConnectionSupplier, false));
    }

    @Test
    public void testPerformGetRequestSuccess() throws Exception {
        InputStream mockStream = new ByteArrayInputStream(TEST_SUCCESS_MESSAGE.getBytes(UTF_8));
        Map<String, List<String>> mockHeaders = new HashMap<>();
        mockHeaders.put("Header1", Arrays.asList("Value1"));
        when(mMockHttpURLConnection.getInputStream()).thenReturn(mockStream);
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(200);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(mockHeaders);
        when(mMockHttpURLConnection.getContentLengthLong())
                .thenReturn((long) TEST_SUCCESS_MESSAGE.length());

        OdpHttpResponse response =
                HttpClientUtils.performRequest(
                        DEFAULT_GET_REQUEST, mHttpURLConnectionSupplier, false);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).isEqualTo(mockHeaders);
        assertThat(response.getPayload()).isEqualTo(TEST_SUCCESS_MESSAGE.getBytes(UTF_8));
    }

    @Test
    public void testPerformGetRequestPayloadIntoFileSuccess() throws Exception {
        InputStream mockStream = new ByteArrayInputStream(TEST_SUCCESS_MESSAGE.getBytes(UTF_8));
        Map<String, List<String>> mockHeaders = new HashMap<>();
        mockHeaders.put("Header1", Arrays.asList("Value1"));
        when(mMockHttpURLConnection.getInputStream()).thenReturn(mockStream);
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(200);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(mockHeaders);
        when(mMockHttpURLConnection.getContentLengthLong())
                .thenReturn((long) TEST_SUCCESS_MESSAGE.length());

        OdpHttpResponse response =
                HttpClientUtils.performRequest(
                        DEFAULT_GET_REQUEST, mHttpURLConnectionSupplier, true);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).isEqualTo(mockHeaders);
        assertThat(response.getPayload()).isEqualTo(null);
        assertThat(response.getPayloadFileName()).isNotEmpty();
        assertThat(new FileInputStream(response.getPayloadFileName()).readAllBytes())
                .isEqualTo(TEST_SUCCESS_MESSAGE.getBytes(UTF_8));
    }

    @Test
    public void testPerformGetRequestFails() throws Exception {
        InputStream mockStream = new ByteArrayInputStream(TEST_FAILURE_MESSAGE.getBytes(UTF_8));
        when(mMockHttpURLConnection.getErrorStream()).thenReturn(mockStream);
        when(mMockHttpURLConnection.getResponseCode()).thenReturn(503);
        when(mMockHttpURLConnection.getHeaderFields()).thenReturn(new HashMap<>());
        when(mMockHttpURLConnection.getContentLengthLong())
                .thenReturn((long) TEST_FAILURE_MESSAGE.length());

        OdpHttpResponse response =
                HttpClientUtils.performRequest(
                        DEFAULT_GET_REQUEST, mHttpURLConnectionSupplier, false);

        assertThat(response.getStatusCode()).isEqualTo(503);
        assertTrue(response.getHeaders().isEmpty());
        assertThat(response.getPayload()).isEqualTo(TEST_FAILURE_MESSAGE.getBytes(UTF_8));
    }

    @Test
    public void testSetup() throws Exception {
        URL testURL = new URL(TEST_URI);

        URLConnection urlConnection = HttpClientUtils.setup(testURL);

        assertEquals(HttpClientUtils.NETWORK_CONNECT_TIMEOUT_MS, urlConnection.getConnectTimeout());
        assertEquals(HttpClientUtils.NETWORK_READ_TIMEOUT_MS, urlConnection.getReadTimeout());
    }
}
