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

import static com.android.federatedcompute.services.http.HttpClientUtil.API_KEY_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.CONTENT_LENGTH_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.CONTENT_TYPE_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.FAKE_API_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.PROTOBUF_CONTENT_TYPE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.proto.ByteStreamResource;
import com.android.federatedcompute.proto.ClientVersion;
import com.android.federatedcompute.proto.ForwardingInfo;
import com.android.federatedcompute.proto.RejectionInfo;
import com.android.federatedcompute.proto.Resource;
import com.android.federatedcompute.proto.Resource.InlineResource;
import com.android.federatedcompute.proto.ResourceCapabilities;
import com.android.federatedcompute.proto.ResourceCompressionFormat;
import com.android.federatedcompute.proto.StartAggregationDataUploadResponse;
import com.android.federatedcompute.proto.StartTaskAssignmentRequest;
import com.android.federatedcompute.proto.StartTaskAssignmentResponse;
import com.android.federatedcompute.proto.TaskAssignment;
import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;

import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
public final class HttpFederatedProtocolTest {
    private static final String TASK_ASSIGNMENT_TARGET_URI = "https://taskassignment.uri/";
    private static final String AGGREGATION_TARGET_URI = "https://aggregation.uri/";
    private static final String PLAN_URI = "https://fake.uri/plan";
    private static final String CHECKPOINT_URI = "https://fake.uri/checkpoint";
    private static final String BYTE_STREAM_TARGET_URI = "https://bytestream.uri/";
    private static final String SECOND_STAGE_AGGREGATION_TARGET_URI =
            "https://aggregation.second.uri/";
    private static final String POPULATION_NAME = "TEST/POPULATION";
    private static final byte[] PLAN = "CLIENT_ONLY_PLAN".getBytes(UTF_8);
    private static final String INIT_CHECKPOINT = "INIT_CHECKPOINT";
    private static final String CLIENT_VERSION = "CLIENT_VERSION";
    private static final String CLIENT_SESSION_ID = "CLIENT_SESSION_ID";
    private static final String AGGREGATION_SESSION_ID = "AGGREGATION_SESSION_ID";
    private static final String AUTHORIZATION_TOKEN = "AUTHORIZATION_TOKEN";
    private static final String RESOURCE_NAME = "CHECKPOINT_RESOURCE";
    private static final String CLIENT_TOKEN = "CLIENT_TOKEN";
    private static final byte[] COMPUTATION_RESULT = "COMPUTATION_RESULT".getBytes(UTF_8);

    private static final StartTaskAssignmentRequest START_TASK_ASSIGNMENT_REQUEST =
            StartTaskAssignmentRequest.newBuilder()
                    .setClientVersion(ClientVersion.newBuilder().setVersionCode(CLIENT_VERSION))
                    .setPopulationName(POPULATION_NAME)
                    .setResourceCapabilities(
                            ResourceCapabilities.newBuilder()
                                    .addSupportedCompressionFormats(
                                            ResourceCompressionFormat
                                                    .RESOURCE_COMPRESSION_FORMAT_GZIP)
                                    .build())
                    .build();
    private static final FederatedComputeHttpResponse SUCCESS_EMPTY_HTTP_RESPONSE =
            new FederatedComputeHttpResponse.Builder().setStatusCode(200).build();

    @Captor private ArgumentCaptor<FederatedComputeHttpRequest> mHttpRequestCaptor;

    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Mock private HttpClient mMockHttpClient;
    private HttpFederatedProtocol mHttpFederatedProtocol;

    @Before
    public void setUp() throws Exception {
        mHttpFederatedProtocol =
                new HttpFederatedProtocol(
                        TASK_ASSIGNMENT_TARGET_URI,
                        CLIENT_VERSION,
                        POPULATION_NAME,
                        mMockHttpClient);
    }

    @Test
    public void testTaskAssignedPlanDataFetchSuccess() throws Exception {
        FederatedComputeHttpResponse startTaskAssignmentResponse =
                createStartTaskAssignmentHttpResponse();
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(PLAN)
                        .build();
        // The workflow is start task assignment and download plan. The checkpoint is defined as
        // inline resource.
        when(mMockHttpClient.performRequest(mHttpRequestCaptor.capture()))
                .thenReturn(startTaskAssignmentResponse)
                .thenReturn(planHttpResponse);

        mHttpFederatedProtocol.issueCheckin().get();

        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();

        // Verify task assignment request.
        FederatedComputeHttpRequest actualStartTaskAssignmentRequest = actualHttpRequests.get(0);
        assertThat(actualStartTaskAssignmentRequest.getUri())
                .isEqualTo(
                        "https://taskassignment.uri/v1/populations/TEST/POPULATION/taskassignments:start?%24alt=proto");
        assertThat(actualStartTaskAssignmentRequest.getBody())
                .isEqualTo(START_TASK_ASSIGNMENT_REQUEST.toByteArray());
        assertThat(actualStartTaskAssignmentRequest.getHttpMethod()).isEqualTo(HttpMethod.POST);
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(API_KEY_HDR, FAKE_API_KEY);
        expectedHeaders.put(CONTENT_LENGTH_HDR, String.valueOf(40));
        expectedHeaders.put(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);
        assertThat(actualStartTaskAssignmentRequest.getExtraHeaders())
                .containsExactlyEntriesIn(expectedHeaders);

        FederatedComputeHttpRequest actualPlanHttpRequest = actualHttpRequests.get(1);
        assertThat(actualPlanHttpRequest.getUri()).isEqualTo(PLAN_URI);
        assertThat(actualPlanHttpRequest.getHttpMethod()).isEqualTo(HttpMethod.GET);
    }

    @Test
    public void testCheckinFailsFromHttp() throws Exception {
        FederatedComputeHttpResponse httpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(404).build();
        when(mMockHttpClient.performRequest(any())).thenReturn(httpResponse);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mHttpFederatedProtocol.issueCheckin().get());

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause())
                .hasMessageThat()
                .isEqualTo("start task assignment failed: 404");
    }

    @Test
    public void testCheckinRejection() throws Exception {
        StartTaskAssignmentResponse startTaskAssignmentResponse =
                StartTaskAssignmentResponse.newBuilder()
                        .setRejectionInfo(RejectionInfo.getDefaultInstance())
                        .build();
        FederatedComputeHttpResponse httpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(startTaskAssignmentResponse.toByteArray())
                        .build();
        when(mMockHttpClient.performRequest(any())).thenReturn(httpResponse);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mHttpFederatedProtocol.issueCheckin().get());

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause()).hasMessageThat().isEqualTo("Device rejected by server.");
    }

    @Test
    public void testTaskAssignedPlanDataFetchFailed() throws Exception {
        FederatedComputeHttpResponse startTaskAssignmentResponse =
                createStartTaskAssignmentHttpResponse();
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(404).build();
        // The workflow is start task assignment and download plan. The checkpoint is defined as
        // inline resource.
        when(mMockHttpClient.performRequest(any()))
                .thenReturn(startTaskAssignmentResponse)
                .thenReturn(planHttpResponse);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mHttpFederatedProtocol.issueCheckin().get());

        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause()).hasMessageThat().isEqualTo("plan fetch failed: 404");
    }

    @Test
    public void testTaskAssignedCheckpointDataFetchFailed() throws Exception {
        StartTaskAssignmentResponse taskAssignmentResponse =
                createStartTaskAssignmentResponse(
                        Resource.newBuilder().setUri(PLAN_URI).build(),
                        Resource.newBuilder().setUri(CHECKPOINT_URI).build());
        FederatedComputeHttpResponse startTaskAssignmentResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(taskAssignmentResponse.toByteArray())
                        .build();
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(PLAN)
                        .build();
        FederatedComputeHttpResponse checkpointHttpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(404).build();
        // The workflow: start task assignment success, download plan success and download
        // checkpoint failed.
        when(mMockHttpClient.performRequest(any()))
                .thenReturn(startTaskAssignmentResponse)
                .thenReturn(planHttpResponse)
                .thenReturn(checkpointHttpResponse);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mHttpFederatedProtocol.issueCheckin().get());
        System.out.println(mHttpRequestCaptor.getAllValues());

        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportViaSimpleAggregation() throws Exception {
        FederatedComputeHttpResponse startTaskAssignmentResponse =
                createStartTaskAssignmentHttpResponse();
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(PLAN)
                        .build();
        StartAggregationDataUploadResponse startAggregationDataUploadResponse =
                StartAggregationDataUploadResponse.newBuilder()
                        .setAggregationProtocolForwardingInfo(
                                ForwardingInfo.newBuilder()
                                        .setTargetUriPrefix(SECOND_STAGE_AGGREGATION_TARGET_URI)
                                        .build())
                        .setResource(
                                ByteStreamResource.newBuilder()
                                        .setResourceName(RESOURCE_NAME)
                                        .setDataUploadForwardingInfo(
                                                ForwardingInfo.newBuilder()
                                                        .setTargetUriPrefix(BYTE_STREAM_TARGET_URI))
                                        .build())
                        .setClientToken(CLIENT_TOKEN)
                        .build();
        FederatedComputeHttpResponse startAggregationDataUploadHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(startAggregationDataUploadResponse.toByteArray())
                        .build();

        when(mMockHttpClient.performRequest(mHttpRequestCaptor.capture()))
                .thenReturn(startTaskAssignmentResponse)
                .thenReturn(planHttpResponse)
                .thenReturn(startAggregationDataUploadHttpResponse)
                .thenReturn(SUCCESS_EMPTY_HTTP_RESPONSE)
                .thenReturn(SUCCESS_EMPTY_HTTP_RESPONSE);

        mHttpFederatedProtocol.issueCheckin().get();
        mHttpFederatedProtocol.reportViaSimpleAggregation(COMPUTATION_RESULT).get();

        // Verify start aggregation request.
        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();
        FederatedComputeHttpRequest acutalStartAggregationRequest = actualHttpRequests.get(2);
        assertThat(acutalStartAggregationRequest.getUri())
                .isEqualTo(
                        "https://aggregation.uri/v1/aggregations/AGGREGATION_SESSION_ID/clients/AUTHORIZATION_TOKEN:startdataupload?%24alt=proto");
        assertThat(acutalStartAggregationRequest.getHttpMethod()).isEqualTo(HttpMethod.POST);
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(API_KEY_HDR, FAKE_API_KEY);
        expectedHeaders.put(CONTENT_LENGTH_HDR, String.valueOf(45));
        expectedHeaders.put(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);
        assertThat(acutalStartAggregationRequest.getExtraHeaders()).isEqualTo(expectedHeaders);

        // Verify upload data request.
        FederatedComputeHttpRequest actualDataUploadRequest = actualHttpRequests.get(3);
        assertThat(actualDataUploadRequest.getUri())
                .isEqualTo(
                        "https://bytestream.uri/upload/v1/media/CHECKPOINT_RESOURCE?upload_protocol=raw");
        assertThat(acutalStartAggregationRequest.getHttpMethod()).isEqualTo(HttpMethod.POST);
        expectedHeaders = new HashMap<>();
        expectedHeaders.put(API_KEY_HDR, FAKE_API_KEY);
        expectedHeaders.put(CONTENT_LENGTH_HDR, String.valueOf(18));
        assertThat(actualDataUploadRequest.getExtraHeaders()).isEqualTo(expectedHeaders);

        // Verify submit aggregation report request.
        FederatedComputeHttpRequest actualSubmitAggregationReportRequest =
                actualHttpRequests.get(4);
        assertThat(actualSubmitAggregationReportRequest.getUri())
                .isEqualTo(
                        "https://aggregation.second.uri/v1/aggregations/AGGREGATION_SESSION_ID/clients/CLIENT_TOKEN:submit?%24alt=proto");
        assertThat(actualSubmitAggregationReportRequest.getHttpMethod()).isEqualTo(HttpMethod.POST);
        expectedHeaders = new HashMap<>();
        expectedHeaders.put(API_KEY_HDR, FAKE_API_KEY);
        expectedHeaders.put(CONTENT_LENGTH_HDR, String.valueOf(21));
        expectedHeaders.put(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);
        assertThat(actualSubmitAggregationReportRequest.getExtraHeaders())
                .isEqualTo(expectedHeaders);
    }

    @Test
    public void testReportCompleteStartAggregationFailed() throws Exception {
        FederatedComputeHttpResponse startTaskAssignmentResponse =
                createStartTaskAssignmentHttpResponse();
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(PLAN)
                        .build();

        when(mMockHttpClient.performRequest(any()))
                .thenReturn(startTaskAssignmentResponse)
                .thenReturn(planHttpResponse)
                .thenReturn(new FederatedComputeHttpResponse.Builder().setStatusCode(503).build());

        mHttpFederatedProtocol.issueCheckin().get();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .reportViaSimpleAggregation(COMPUTATION_RESULT)
                                        .get());

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause())
                .hasMessageThat()
                .isEqualTo("start data upload failed: 503");
    }

    @Test
    public void testReportCompleteUploadFailed() throws Exception {
        FederatedComputeHttpResponse startTaskAssignmentResponse =
                createStartTaskAssignmentHttpResponse();
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(PLAN)
                        .build();
        StartAggregationDataUploadResponse startAggregationDataUploadResponse =
                StartAggregationDataUploadResponse.newBuilder()
                        .setAggregationProtocolForwardingInfo(
                                ForwardingInfo.newBuilder()
                                        .setTargetUriPrefix(SECOND_STAGE_AGGREGATION_TARGET_URI)
                                        .build())
                        .setResource(
                                ByteStreamResource.newBuilder()
                                        .setResourceName(RESOURCE_NAME)
                                        .setDataUploadForwardingInfo(
                                                ForwardingInfo.newBuilder()
                                                        .setTargetUriPrefix(BYTE_STREAM_TARGET_URI))
                                        .build())
                        .setClientToken(CLIENT_TOKEN)
                        .build();
        FederatedComputeHttpResponse startAggregationDataUploadHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(startAggregationDataUploadResponse.toByteArray())
                        .build();

        when(mMockHttpClient.performRequest(any()))
                .thenReturn(startTaskAssignmentResponse)
                .thenReturn(planHttpResponse)
                .thenReturn(startAggregationDataUploadHttpResponse)
                .thenReturn(new FederatedComputeHttpResponse.Builder().setStatusCode(503).build());

        mHttpFederatedProtocol.issueCheckin().get();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .reportViaSimpleAggregation(COMPUTATION_RESULT)
                                        .get());

        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause())
                .hasMessageThat()
                .isEqualTo("upload failed: 503 CHECKPOINT_RESOURCE");
    }

    @Test
    public void testReportCompleteSubmitAggregationFailed() throws Exception {
        FederatedComputeHttpResponse startTaskAssignmentResponse =
                createStartTaskAssignmentHttpResponse();
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(PLAN)
                        .build();
        StartAggregationDataUploadResponse startAggregationDataUploadResponse =
                StartAggregationDataUploadResponse.newBuilder()
                        .setAggregationProtocolForwardingInfo(
                                ForwardingInfo.newBuilder()
                                        .setTargetUriPrefix(SECOND_STAGE_AGGREGATION_TARGET_URI)
                                        .build())
                        .setResource(
                                ByteStreamResource.newBuilder()
                                        .setResourceName(RESOURCE_NAME)
                                        .setDataUploadForwardingInfo(
                                                ForwardingInfo.newBuilder()
                                                        .setTargetUriPrefix(BYTE_STREAM_TARGET_URI))
                                        .build())
                        .setClientToken(CLIENT_TOKEN)
                        .build();
        FederatedComputeHttpResponse startAggregationDataUploadHttpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(startAggregationDataUploadResponse.toByteArray())
                        .build();

        when(mMockHttpClient.performRequest(any()))
                .thenReturn(startTaskAssignmentResponse)
                .thenReturn(planHttpResponse)
                .thenReturn(startAggregationDataUploadHttpResponse)
                .thenReturn(SUCCESS_EMPTY_HTTP_RESPONSE)
                .thenReturn(new FederatedComputeHttpResponse.Builder().setStatusCode(503).build());

        mHttpFederatedProtocol.issueCheckin().get();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .reportViaSimpleAggregation(COMPUTATION_RESULT)
                                        .get());

        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause())
                .hasMessageThat()
                .isEqualTo("submit aggregation result failed: 503 CHECKPOINT_RESOURCE");
    }

    private FederatedComputeHttpResponse createStartTaskAssignmentHttpResponse() throws Exception {
        StartTaskAssignmentResponse startTaskAssignmentResponse =
                createStartTaskAssignmentResponse(
                        Resource.newBuilder().setUri(PLAN_URI).build(),
                        Resource.newBuilder()
                                .setInlineResource(
                                        InlineResource.newBuilder()
                                                .setData(ByteString.copyFromUtf8(INIT_CHECKPOINT))
                                                .build())
                                .build());

        return new FederatedComputeHttpResponse.Builder()
                .setStatusCode(200)
                .setPayload(startTaskAssignmentResponse.toByteArray())
                .build();
    }

    private StartTaskAssignmentResponse createStartTaskAssignmentResponse(
            Resource plan, Resource checkpoint) {
        ForwardingInfo forwardingInfo =
                ForwardingInfo.newBuilder().setTargetUriPrefix(AGGREGATION_TARGET_URI).build();
        TaskAssignment taskAssignment =
                TaskAssignment.newBuilder()
                        .setSessionId(CLIENT_SESSION_ID)
                        .setAggregationId(AGGREGATION_SESSION_ID)
                        .setAuthorizationToken(AUTHORIZATION_TOKEN)
                        .setPlan(plan)
                        .setInitCheckpoint(checkpoint)
                        .setAggregationDataForwardingInfo(forwardingInfo)
                        .build();
        return StartTaskAssignmentResponse.newBuilder().setTaskAssignment(taskAssignment).build();
    }
}
