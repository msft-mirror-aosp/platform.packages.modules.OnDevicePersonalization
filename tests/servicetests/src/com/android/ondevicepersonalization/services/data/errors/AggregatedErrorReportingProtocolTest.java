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

package com.android.ondevicepersonalization.services.data.errors;

import static com.android.odp.module.common.http.HttpClientUtils.CONTENT_TYPE_HDR;
import static com.android.odp.module.common.http.HttpClientUtils.OCTET_STREAM;
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorCodesLoggerTest.getExpectedErrorData;
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorReportingProtocol.convertToProto;
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorReportingProtocol.createAggregatedErrorReportingProtocol;
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorReportingProtocol.getHttpRequest;
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorReportingProtocol.getReportRequest;
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorReportingProtocol.getRequestUri;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import android.content.ComponentName;
import android.content.Context;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.odp.module.common.http.HttpClient;
import com.android.odp.module.common.http.HttpClientUtils;
import com.android.odp.module.common.http.OdpHttpRequest;
import com.android.odp.module.common.http.OdpHttpResponse;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.PhFlags;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.internal.federatedcompute.v1.UploadInstruction;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportExceptionResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(JUnit4.class)
public class AggregatedErrorReportingProtocolTest {
    private static final String TEST_PACKAGE = "test_package";
    private static final String TEST_CLASS = "test_class";
    private static final String TEST_SERVER_URL = "https://google.com";
    private static final long TEST_CLIENT_VERSION = 1;

    private static final ComponentName TEST_COMPONENT_NAME =
            new ComponentName(TEST_PACKAGE, TEST_CLASS);

    private static final String UPLOAD_LOCATION_URI = "https://dataupload.uri";

    private static final ListenableFuture<Boolean> SUCCESSFUL_FUTURE =
            Futures.immediateFuture(true);

    private static final int HTTP_OK_STATUS = 200;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private int mDayIndexUtc;

    private ErrorData mErrorData;

    private AggregatedErrorReportingProtocol mInstanceUnderTest;

    private CountDownLatch mCountDownLatch = new CountDownLatch(1);

    @Mock private Flags mMockFlags;

    @Mock private HttpClient mMockHttpClient;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDayIndexUtc = DateTimeUtils.dayIndexUtc();
        mErrorData = getExpectedErrorData(mDayIndexUtc);
        when(mMockFlags.getAggregatedErrorReportingServerPath())
                .thenReturn(PhFlags.DEFAULT_AGGREGATED_ERROR_REPORTING_URL_PATH);
        // Inject mock flags and a test ReportingProtocol object
    }

    @Test
    public void reportExceptionData_httpClientFails() {
        Throwable error = new IllegalStateException("Random failure");
        when(mMockHttpClient.performRequestAsyncWithRetry(any()))
                .thenReturn(Futures.immediateFailedFuture(error));
        mInstanceUnderTest =
                createAggregatedErrorReportingProtocol(
                        ImmutableList.of(mErrorData),
                        TEST_SERVER_URL,
                        TEST_CLIENT_VERSION,
                        new TestInjector());

        ListenableFuture<Boolean> reportingFuture = mInstanceUnderTest.reportExceptionData();

        assertTrue(reportingFuture.isDone());
        ExecutionException outException =
                assertThrows(ExecutionException.class, reportingFuture::get);
        assertThat(outException.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void reportExceptionData_httpClientFailedErrorCode() {
        OdpHttpResponse response = createReportExceptionResponse(404);
        when(mMockHttpClient.performRequestAsyncWithRetry(any()))
                .thenReturn(Futures.immediateFuture(response));
        mInstanceUnderTest =
                createAggregatedErrorReportingProtocol(
                        ImmutableList.of(mErrorData),
                        TEST_SERVER_URL,
                        TEST_CLIENT_VERSION,
                        new TestInjector());

        ListenableFuture<Boolean> reportingFuture = mInstanceUnderTest.reportExceptionData();

        assertTrue(reportingFuture.isDone());
        ExecutionException outException =
                assertThrows(ExecutionException.class, reportingFuture::get);
        assertThat(outException.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(outException.getCause().getMessage()).containsMatch(".*reportRequest.failed.*");
    }

    @Test
    public void reportExceptionData_httpClientMissingUploadLocation() {
        OdpHttpResponse response = createReportExceptionResponse(HTTP_OK_STATUS);
        when(mMockHttpClient.performRequestAsyncWithRetry(any()))
                .thenReturn(Futures.immediateFuture(response));
        mInstanceUnderTest =
                createAggregatedErrorReportingProtocol(
                        ImmutableList.of(mErrorData),
                        TEST_SERVER_URL,
                        TEST_CLIENT_VERSION,
                        new TestInjector());

        ListenableFuture<Boolean> reportingFuture = mInstanceUnderTest.reportExceptionData();

        assertTrue(reportingFuture.isDone());
        ExecutionException outException =
                assertThrows(ExecutionException.class, reportingFuture::get);
        assertThat(outException.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void reportExceptionData_httpClientSuccessful() throws Exception {
        // Tests successful upload flow, validates the requests sent via the http client.
        TestInjector testInjector = new TestInjector();
        ArgumentCaptor<OdpHttpRequest> clientRequestCaptor =
                ArgumentCaptor.forClass(OdpHttpRequest.class);
        OdpHttpResponse serverResponse =
                createReportExceptionResponse(HTTP_OK_STATUS, UPLOAD_LOCATION_URI);
        OdpHttpRequest expectedClientReportRequest =
                getHttpRequest(
                        getRequestUri(TEST_SERVER_URL, testInjector.getFlags()),
                        Map.of(),
                        getReportRequest().toByteArray());
        OdpHttpRequest expectedClientUploadRequest =
                createExpectedUploadRequest(UPLOAD_LOCATION_URI, ImmutableList.of(mErrorData));
        when(mMockHttpClient.performRequestAsyncWithRetry(any()))
                .thenReturn(Futures.immediateFuture(serverResponse));
        mInstanceUnderTest =
                createAggregatedErrorReportingProtocol(
                        ImmutableList.of(mErrorData),
                        TEST_SERVER_URL,
                        TEST_CLIENT_VERSION,
                        testInjector);

        ListenableFuture<Boolean> reportingFuture = mInstanceUnderTest.reportExceptionData();

        assertTrue(reportingFuture.isDone());
        assertTrue(reportingFuture.get());
        verify(mMockHttpClient, times(2))
                .performRequestAsyncWithRetry(clientRequestCaptor.capture());
        List<OdpHttpRequest> clientRequests = clientRequestCaptor.getAllValues();
        // Validate the report request
        assertEquals(HttpClientUtils.HttpMethod.PUT, clientRequests.get(0).getHttpMethod());
        assertEquals(expectedClientReportRequest.getUri(), clientRequests.get(0).getUri());
        // Validate the subsequent upload request
        assertEquals(HttpClientUtils.HttpMethod.PUT, clientRequests.get(1).getHttpMethod());
        assertEquals(expectedClientUploadRequest.getUri(), clientRequests.get(1).getUri());
        assertTrue(
                Arrays.equals(
                        expectedClientUploadRequest.getBody(), clientRequests.get(1).getBody()));
    }

    @Test
    public void reportExceptionData_httpClientTimeout() throws Exception {
        // Set short timeout and don't return anything from the mock http client.
        when(mMockFlags.getAggregatedErrorReportingHttpTimeoutSeconds()).thenReturn(5);
        when(mMockHttpClient.performRequestAsyncWithRetry(any()))
                .thenReturn(SettableFuture.create());
        mInstanceUnderTest =
                createAggregatedErrorReportingProtocol(
                        ImmutableList.of(mErrorData),
                        TEST_SERVER_URL,
                        TEST_CLIENT_VERSION,
                        new TestInjector());

        ListenableFuture<Boolean> reportingFuture = mInstanceUnderTest.reportExceptionData();
        Futures.addCallback(reportingFuture, new TestCallback(), MoreExecutors.directExecutor());

        boolean countedDown = mCountDownLatch.await(10, TimeUnit.SECONDS);

        assertTrue(countedDown);
        assertTrue(reportingFuture.isDone());
        ExecutionException outException =
                assertThrows(ExecutionException.class, reportingFuture::get);
        assertThat(outException.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    public void createEncryptedRequestBody() throws JSONException {
        // TODO(b/329921267): add encryption support
        com.google.ondevicepersonalization.federatedcompute.proto.ErrorDataList errorDataList =
                convertToProto(ImmutableList.of(mErrorData));
        String expectedErrorData =
                Base64.encodeToString(errorDataList.toByteArray(), Base64.NO_WRAP);

        JSONObject jsonResponse =
                new JSONObject(
                        new String(
                                AggregatedErrorReportingProtocol.createEncryptedRequestBody(
                                        ImmutableList.of(mErrorData))));
        assertEquals(
                expectedErrorData,
                jsonResponse.get(
                        AggregatedErrorReportingProtocol.AggregatedErrorDataPayloadContract
                                .ENCRYPTED_PAYLOAD));
    }

    private static OdpHttpRequest createExpectedUploadRequest(
            String uploadLocation, ImmutableList<ErrorData> errorData) throws JSONException {
        return getHttpRequest(
                uploadLocation,
                /* requestHeadersMap= */ Map.of(CONTENT_TYPE_HDR, OCTET_STREAM),
                AggregatedErrorReportingProtocol.createEncryptedRequestBody(errorData));
    }

    private static OdpHttpResponse createReportExceptionResponse(int statusCode) {
        // Create a response with only status code no upload instruction, payload etc.
        return createReportExceptionResponse(statusCode, /* uploadLocation= */ "");
    }

    private static OdpHttpResponse createReportExceptionResponse(
            int statusCode, String uploadLocation) {
        UploadInstruction.Builder uploadInstruction =
                UploadInstruction.newBuilder().setUploadLocation(uploadLocation);
        uploadInstruction.putExtraRequestHeaders(CONTENT_TYPE_HDR, OCTET_STREAM);

        ReportExceptionResponse response =
                ReportExceptionResponse.newBuilder()
                        .setUploadInstruction(uploadInstruction.build())
                        .build();

        return new OdpHttpResponse.Builder()
                .setStatusCode(statusCode)
                .setPayload(response.toByteArray())
                .build();
    }

    class TestInjector extends AggregatedErrorReportingProtocol.Injector {
        @Override
        ListeningExecutorService getBlockingExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ListeningExecutorService getBackgroundExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ListeningScheduledExecutorService getScheduledExecutor() {
            return MoreExecutors.listeningDecorator(newSingleThreadScheduledExecutor());
        }

        @Override
        Flags getFlags() {
            return mMockFlags;
        }

        @Override
        HttpClient getClient() {
            return mMockHttpClient;
        }
    }

    private class TestCallback implements FutureCallback<Boolean> {

        @Override
        public void onSuccess(Boolean result) {
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
            mCountDownLatch.countDown();
        }
    }
}
