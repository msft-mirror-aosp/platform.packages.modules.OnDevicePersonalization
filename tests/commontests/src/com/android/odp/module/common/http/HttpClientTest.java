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
import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public final class HttpClientTest {
    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final int DEFAULT_RETRY_LIMIT = 3;
    private static final int HTTP_UNAVAILABLE = 503;
    private static final int HTTP_OK = 200;

    @Spy
    private HttpClient mHttpClient =
            new HttpClient(DEFAULT_RETRY_LIMIT, MoreExecutors.newDirectExecutorService());

    @Test
    public void testPerformGetRequestFailsWithRetry() throws Exception {
        String failureMessage = "FAIL!";
        OdpHttpResponse testFailedResponse =
                new OdpHttpResponse.Builder()
                        .setHeaders(new HashMap<>())
                        .setPayload(failureMessage.getBytes(UTF_8))
                        .setStatusCode(HTTP_UNAVAILABLE)
                        .build();
        TestHttpIOSupplier testSupplier = new TestHttpIOSupplier(testFailedResponse);

        OdpHttpResponse returnedResponse = mHttpClient.performRequestWithRetry(testSupplier);

        assertEquals(DEFAULT_RETRY_LIMIT, testSupplier.mCallCount.get());
        assertThat(returnedResponse.getStatusCode()).isEqualTo(HTTP_UNAVAILABLE);
        assertTrue(returnedResponse.getHeaders().isEmpty());
        assertThat(returnedResponse.getPayload()).isEqualTo(failureMessage.getBytes(UTF_8));
    }

    @Test
    public void testPerformGetRequestSuccessWithRetry() throws Exception {
        Map<String, List<String>> mockHeaders = new HashMap<>();
        mockHeaders.put("Header1", Arrays.asList("Value1"));
        String failureMessage = "FAIL!";
        String successMessage = "Success!";
        OdpHttpResponse testFailedResponse =
                new OdpHttpResponse.Builder()
                        .setHeaders(new HashMap<>())
                        .setPayload(failureMessage.getBytes(UTF_8))
                        .setStatusCode(HTTP_UNAVAILABLE)
                        .build();
        OdpHttpResponse testSuccessfulResponse =
                new OdpHttpResponse.Builder()
                        .setPayload(successMessage.getBytes(UTF_8))
                        .setStatusCode(HTTP_OK)
                        .setHeaders(mockHeaders)
                        .build();
        TestHttpIOSupplier testSupplier =
                new TestHttpIOSupplier(
                        testSuccessfulResponse, testFailedResponse, DEFAULT_RETRY_LIMIT - 1);

        OdpHttpResponse response = mHttpClient.performRequestWithRetry(testSupplier);

        assertEquals(DEFAULT_RETRY_LIMIT, testSupplier.mCallCount.get());
        assertThat(response.getStatusCode()).isEqualTo(HTTP_OK);
        assertThat(response.getHeaders()).isEqualTo(mockHeaders);
    }

    private static final class TestHttpIOSupplier
            implements HttpClient.HttpIOSupplier<OdpHttpResponse> {
        private final AtomicInteger mCallCount = new AtomicInteger(0);

        private final OdpHttpResponse mSuccessfulResponse;
        private final OdpHttpResponse mFailedResponse;
        private final int mNumFailedCalls;

        private TestHttpIOSupplier(OdpHttpResponse failedResponse) {
            this(null, failedResponse, 0);
        }

        private TestHttpIOSupplier(
                OdpHttpResponse successfulResponse,
                OdpHttpResponse failedResponse,
                int numFailedCalls) {
            this.mSuccessfulResponse = successfulResponse;
            this.mFailedResponse = failedResponse;
            this.mNumFailedCalls = numFailedCalls;
        }

        @Override
        public OdpHttpResponse get() throws IOException {
            int callCount = mCallCount.incrementAndGet();
            if (mSuccessfulResponse == null) {
                return mFailedResponse;
            }

            return callCount > mNumFailedCalls ? mSuccessfulResponse : mFailedResponse;
        }
    }
}
