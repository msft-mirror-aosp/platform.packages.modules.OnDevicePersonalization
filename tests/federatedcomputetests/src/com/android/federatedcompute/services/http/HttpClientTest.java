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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.federatedcompute.services.common.PhFlags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.odp.module.common.HttpClientUtils;
import com.android.odp.module.common.OdpHttpRequest;
import com.android.odp.module.common.OdpHttpResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
@ExtendedMockitoRule.MockStatic(PhFlags.class)
public final class HttpClientTest {
    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    public static final OdpHttpRequest DEFAULT_GET_REQUEST =
            OdpHttpRequest.create(
                    "https://google.com",
                    HttpClientUtils.HttpMethod.GET,
                    new HashMap<>(),
                    HttpClientUtil.EMPTY_BODY);

    private static final int DEFAULT_RETRY_LIMIT = 3;

    @Spy private HttpClient mHttpClient = new HttpClient();

    @Mock private HttpURLConnection mMockHttpURLConnection;
    @Mock private PhFlags mMocKFlags;

    @Before
    public void setUp() throws Exception {
        when(PhFlags.getInstance()).thenReturn(mMocKFlags);
        when(mMocKFlags.getHttpRequestRetryLimit()).thenReturn(DEFAULT_RETRY_LIMIT);
    }

    @Test
    public void testPerformGetRequestFailsWithRetry() throws Exception {
        String failureMessage = "FAIL!";
        OdpHttpResponse testFailedResponse =
                new OdpHttpResponse.Builder()
                        .setHeaders(new HashMap<>())
                        .setPayload(failureMessage.getBytes(UTF_8))
                        .setStatusCode(503)
                        .build();
        TestHttpIOSupplier testSupplier = new TestHttpIOSupplier(testFailedResponse);

        OdpHttpResponse returnedResponse = mHttpClient.performRequestWithRetry(testSupplier);

        assertEquals(DEFAULT_RETRY_LIMIT, testSupplier.mCallCount.get());
        assertThat(returnedResponse.getStatusCode()).isEqualTo(503);
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
                        .setStatusCode(503)
                        .build();
        OdpHttpResponse testSuccessfulResponse =
                new OdpHttpResponse.Builder()
                        .setPayload(successMessage.getBytes(UTF_8))
                        .setStatusCode(200)
                        .setHeaders(mockHeaders)
                        .build();
        TestHttpIOSupplier testSupplier =
                new TestHttpIOSupplier(
                        testSuccessfulResponse, testFailedResponse, DEFAULT_RETRY_LIMIT - 1);

        OdpHttpResponse response = mHttpClient.performRequestWithRetry(testSupplier);

        assertEquals(DEFAULT_RETRY_LIMIT, testSupplier.mCallCount.get());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).isEqualTo(mockHeaders);
    }

    private static final class TestHttpIOSupplier
            implements HttpClient.HttpIOSupplier<OdpHttpResponse> {
        private AtomicInteger mCallCount = new AtomicInteger(0);

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
