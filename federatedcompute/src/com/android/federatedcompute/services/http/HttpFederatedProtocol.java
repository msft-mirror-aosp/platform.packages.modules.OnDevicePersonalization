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

import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBackgroundExecutor;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getLightweightExecutor;
import static com.android.federatedcompute.services.http.HttpClientUtil.CLIENT_DECODE_GZIP_SUFFIX;
import static com.android.federatedcompute.services.http.HttpClientUtil.FAKE_API_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_OK_STATUS;
import static com.android.federatedcompute.services.http.HttpClientUtil.OCTET_STREAM;

import android.util.Log;

import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.internal.federatedcompute.v1.ClientVersion;
import com.google.internal.federatedcompute.v1.Resource;
import com.google.internal.federatedcompute.v1.ResourceCapabilities;
import com.google.internal.federatedcompute.v1.ResourceCompressionFormat;
import com.google.internal.federatedcompute.v1.StartAggregationDataUploadRequest;
import com.google.internal.federatedcompute.v1.StartAggregationDataUploadResponse;
import com.google.internal.federatedcompute.v1.StartTaskAssignmentRequest;
import com.google.internal.federatedcompute.v1.StartTaskAssignmentResponse;
import com.google.internal.federatedcompute.v1.SubmitAggregationResultRequest;
import com.google.internal.federatedcompute.v1.TaskAssignment;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/** Implements a single session of HTTP-based federated compute protocol. */
public final class HttpFederatedProtocol {
    public static final String TAG = "HttpFederatedProtocol";

    private final String mClientVersion;
    private final String mPopulationName;
    private final HttpClient mHttpClient;
    private String mAggregatedSessionId;
    private String mAggregationAuthroizationToken;
    private String mAggregationResourceName;
    private final ProtocolRequestCreator mTaskAssignmentRequestCreator;
    private ProtocolRequestCreator mAggregationRequestCreator;
    private ProtocolRequestCreator mDataUploadRequestCreator;

    @VisibleForTesting
    HttpFederatedProtocol(
            String entryUri, String clientVersion, String populationName, HttpClient httpClient) {
        this.mClientVersion = clientVersion;
        this.mPopulationName = populationName;
        this.mHttpClient = httpClient;
        this.mTaskAssignmentRequestCreator =
                new ProtocolRequestCreator(entryUri, FAKE_API_KEY, new HashMap<>(), false);
    }

    /** Creates a HttpFederatedProtocol object. */
    public static HttpFederatedProtocol create(
            String entryUri, String clientVersion, String populationName) {
        return new HttpFederatedProtocol(entryUri, clientVersion, populationName, new HttpClient());
    }

    /** Helper function to perform check in and download federated task from remote servers. */
    public ListenableFuture<CheckinResult> issueCheckin() {
        ListenableFuture<TaskAssignment> taskAssignmentFuture =
                FluentFuture.from(getBackgroundExecutor().submit(() -> callStartTaskAssignment()))
                        .transformAsync(
                                getTaskAssignmentHttpResponse ->
                                        getTaskAssignment(getTaskAssignmentHttpResponse),
                                FederatedComputeExecutors.getLightweightExecutor());

        ListenableFuture<FederatedComputeHttpResponse> planDataResponseFuture =
                FluentFuture.from(taskAssignmentFuture)
                        .transformAsync(
                                taskAssignment ->
                                        getBackgroundExecutor()
                                                .submit(
                                                        () ->
                                                                fetchTaskResource(
                                                                        taskAssignment.getPlan())),
                                getBackgroundExecutor());

        ListenableFuture<FederatedComputeHttpResponse> checkpointDataResponseFuture =
                FluentFuture.from(taskAssignmentFuture)
                        .transformAsync(
                                taskAssignment ->
                                        getBackgroundExecutor()
                                                .submit(
                                                        () -> {
                                                            Resource checkpointResource =
                                                                    taskAssignment
                                                                            .getInitCheckpoint();
                                                            return fetchTaskResource(
                                                                    checkpointResource);
                                                        }),
                                getBackgroundExecutor());

        return Futures.whenAllSucceed(
                        taskAssignmentFuture, planDataResponseFuture, checkpointDataResponseFuture)
                .callAsync(
                        new AsyncCallable<CheckinResult>() {
                            @Override
                            public ListenableFuture<CheckinResult> call() {
                                return getCheckinResult(
                                        planDataResponseFuture,
                                        checkpointDataResponseFuture,
                                        taskAssignmentFuture);
                            }
                        },
                        getBackgroundExecutor());
    }

    /** Helper functions to reporting result via simple aggregation. */
    public FluentFuture<Void> reportViaSimpleAggregation(byte[] computationResult) {
        return FluentFuture.from(getBackgroundExecutor().submit(() -> performStartDataUpload()))
                .transform(
                        startResp -> processStartDataUploadResponse(startResp),
                        FederatedComputeExecutors.getLightweightExecutor())
                .transformAsync(
                        voidIgnore ->
                                getBackgroundExecutor()
                                        .submit(
                                                () ->
                                                        uploadViaSimpleAggregation(
                                                                computationResult)),
                        getBackgroundExecutor())
                .transform(
                        resp -> processFederatedComputeHttpResponse("upload failed", resp),
                        FederatedComputeExecutors.getLightweightExecutor())
                .transformAsync(
                        voidIgnore ->
                                getBackgroundExecutor().submit(() -> subimitAggregationResult()),
                        getBackgroundExecutor())
                .transform(
                        resp ->
                                processFederatedComputeHttpResponse(
                                        "submit aggregation result failed", resp),
                        getLightweightExecutor());
    }

    private FederatedComputeHttpResponse callStartTaskAssignment() throws IOException {
        StartTaskAssignmentRequest request =
                StartTaskAssignmentRequest.newBuilder()
                        .setClientVersion(ClientVersion.newBuilder().setVersionCode(mClientVersion))
                        .setPopulationName(mPopulationName)
                        .setResourceCapabilities(
                                ResourceCapabilities.newBuilder()
                                        .addSupportedCompressionFormats(
                                                ResourceCompressionFormat
                                                        .RESOURCE_COMPRESSION_FORMAT_GZIP)
                                        .build())
                        .build();
        String taskAssignmentUriSuffix =
                String.format("/v1/populations/%1$s/taskassignments:start", mPopulationName);
        FederatedComputeHttpRequest httpRequest =
                mTaskAssignmentRequestCreator.createProtoRequest(
                        taskAssignmentUriSuffix,
                        HttpMethod.POST,
                        request.toByteArray(),
                        /* isProtobufEncoded= */ true);
        return mHttpClient.performRequest(httpRequest);
    }

    private ListenableFuture<TaskAssignment> getTaskAssignment(
            FederatedComputeHttpResponse httpResponse) {
        if (httpResponse.getStatusCode() != HTTP_OK_STATUS) {
            Log.e(TAG, "start task assignment failed: " + httpResponse.getStatusCode());
            throw new IllegalStateException(
                    "start task assignment failed: " + httpResponse.getStatusCode());
        }
        StartTaskAssignmentResponse taskAssignmentResponse;
        try {
            taskAssignmentResponse =
                    StartTaskAssignmentResponse.parseFrom(
                            httpResponse.getPayload(), ExtensionRegistryLite.getEmptyRegistry());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Could not parse StartTaskAssignmentResponse proto", e);
        }
        Log.i(TAG, "start task assignment response: " + taskAssignmentResponse);
        if (taskAssignmentResponse.hasRejectionInfo()) {
            throw new IllegalStateException("Device rejected by server.");
        }
        if (!taskAssignmentResponse.hasTaskAssignment()) {
            throw new IllegalStateException(
                    "Could not find both task assignment and rejection info.");
        }
        return Futures.immediateFuture(taskAssignmentResponse.getTaskAssignment());
    }

    private ListenableFuture<CheckinResult> getCheckinResult(
            ListenableFuture<FederatedComputeHttpResponse> planDataResponseFuture,
            ListenableFuture<FederatedComputeHttpResponse> checkpointDataResponseFuture,
            ListenableFuture<TaskAssignment> taskAssignmentFuture) {
        try {
            FederatedComputeHttpResponse planDataResponse = Futures.getDone(planDataResponseFuture);
            FederatedComputeHttpResponse checkpointDataResponse =
                    Futures.getDone(checkpointDataResponseFuture);
            TaskAssignment taskAssignment = Futures.getDone(taskAssignmentFuture);
            if (planDataResponse.getStatusCode() != HTTP_OK_STATUS) {
                throw new IllegalStateException(
                        "plan fetch failed: " + planDataResponse.getStatusCode());
            }
            if (checkpointDataResponse.getStatusCode() != HTTP_OK_STATUS) {
                throw new IllegalStateException(
                        "checkpoint data fetch failed: " + checkpointDataResponse.getStatusCode());
            }
            if (taskAssignment.getAggregationId().isEmpty()
                    || taskAssignment.getAuthorizationToken().isEmpty()) {
                throw new IllegalStateException(
                        "Aggregation id and authorization token should not be empty: "
                                + taskAssignment.getAggregationId()
                                + " "
                                + taskAssignment.getAuthorizationToken());
            }
            this.mAggregatedSessionId = taskAssignment.getAggregationId();
            this.mAggregationAuthroizationToken = taskAssignment.getAuthorizationToken();
            mAggregationRequestCreator =
                    ProtocolRequestCreator.create(
                            FAKE_API_KEY,
                            taskAssignment.getAggregationDataForwardingInfo(),
                            /* useCompression= */ false);

            return Futures.immediateFuture(
                    new CheckinResult(
                            planDataResponse.getPayload(), checkpointDataResponse.getPayload()));
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private FederatedComputeHttpResponse performStartDataUpload() throws IOException {
        StartAggregationDataUploadRequest startDataUploadRequest =
                StartAggregationDataUploadRequest.newBuilder()
                        .setAggregationId(mAggregatedSessionId)
                        .setAuthorizationToken(mAggregationAuthroizationToken)
                        .build();
        String startDataUploadUri =
                String.format(
                        "/v1/aggregations/%1$s/clients/%2$s:startdataupload",
                        mAggregatedSessionId, mAggregationAuthroizationToken);
        FederatedComputeHttpRequest httpRequest =
                mAggregationRequestCreator.createProtoRequest(
                        startDataUploadUri,
                        HttpMethod.POST,
                        startDataUploadRequest.toByteArray(),
                        /* isProtobufEncoded= */ true);
        return mHttpClient.performRequest(httpRequest);
    }

    private Void processStartDataUploadResponse(FederatedComputeHttpResponse httpResponse) {
        StartAggregationDataUploadResponse startDataUploadResponse;
        if (httpResponse.getStatusCode() != HTTP_OK_STATUS) {
            Log.e(TAG, "start data upload failed: " + httpResponse.getStatusCode());
            throw new IllegalStateException(
                    "start data upload failed: " + httpResponse.getStatusCode());
        }
        try {
            startDataUploadResponse =
                    StartAggregationDataUploadResponse.parseFrom(
                            httpResponse.getPayload(), ExtensionRegistryLite.getEmptyRegistry());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(
                    "Could not parse StartAggregationDataUploadResponse proto", e);
        }
        if (startDataUploadResponse
                .getAggregationProtocolForwardingInfo()
                .getTargetUriPrefix()
                .isEmpty()) {
            throw new IllegalStateException(
                    "Missing ForwardingInfo.target_uri_prefix in"
                            + " StartAggregationDataUploadResponse");
        }
        mAggregationRequestCreator =
                ProtocolRequestCreator.create(
                        FAKE_API_KEY,
                        startDataUploadResponse.getAggregationProtocolForwardingInfo(),
                        /* useCompression= */ false);
        mDataUploadRequestCreator =
                ProtocolRequestCreator.create(
                        FAKE_API_KEY,
                        startDataUploadResponse.getResource().getDataUploadForwardingInfo(),
                        /* useCompression= */ false);
        mAggregationAuthroizationToken =
                startDataUploadResponse.getClientToken().isEmpty()
                        ? mAggregationAuthroizationToken
                        : startDataUploadResponse.getClientToken();
        mAggregationResourceName = startDataUploadResponse.getResource().getResourceName();

        return null;
    }

    private FederatedComputeHttpResponse subimitAggregationResult() throws IOException {
        String submitAggregationResultUri =
                String.format(
                        "/v1/aggregations/%1$s/clients/%2$s:submit",
                        mAggregatedSessionId, mAggregationAuthroizationToken);
        SubmitAggregationResultRequest request =
                SubmitAggregationResultRequest.newBuilder()
                        .setResourceName(mAggregationResourceName)
                        .build();
        FederatedComputeHttpRequest httpRequest =
                mAggregationRequestCreator.createProtoRequest(
                        submitAggregationResultUri,
                        HttpMethod.POST,
                        request.toByteArray(),
                        /* isProtobufEncoded= */ true);
        return mHttpClient.performRequest(httpRequest);
    }

    private Void processFederatedComputeHttpResponse(
            String stage, FederatedComputeHttpResponse httpResponse) {
        if (httpResponse.getStatusCode() != HTTP_OK_STATUS) {
            throw new IllegalStateException(
                    stage + ": " + httpResponse.getStatusCode() + " " + mAggregationResourceName);
        }
        return null;
    }

    private FederatedComputeHttpResponse uploadViaSimpleAggregation(byte[] computationResult)
            throws IOException {
        String uploadUri = String.format("/upload/v1/media/%1$s", mAggregationResourceName);
        HashMap<String, String> params = new HashMap<>();
        params.put("upload_protocol", "raw");
        FederatedComputeHttpRequest request =
                mDataUploadRequestCreator.createProtoRequest(
                        uploadUri, HttpMethod.POST, params, computationResult, false);
        return mHttpClient.performRequest(request);
    }

    private FederatedComputeHttpResponse fetchTaskResource(Resource resource) throws IOException {
        if (resource.getResourceCase() == Resource.ResourceCase.URI) {
            if (resource.getUri().isEmpty()) {
                throw new IllegalArgumentException("Resource.uri must be non-empty when set");
            }
            if (!resource.getClientCacheId().isEmpty()) {
                throw new UnsupportedOperationException("Resource cache is not supported yet.");
            }

            FederatedComputeHttpRequest httpRequest =
                    FederatedComputeHttpRequest.create(
                            resource.getUri(),
                            HttpMethod.GET,
                            new HashMap<String, String>(),
                            HttpClientUtil.EMPTY_BODY,
                            /* useCompression= */ false);
            return mHttpClient.performRequest(httpRequest);

        } else if (resource.getResourceCase() == Resource.ResourceCase.INLINE_RESOURCE) {
            String contentType = OCTET_STREAM;
            if (resource.getInlineResource().getCompressionFormat()
                    == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP) {
                contentType = contentType.concat(CLIENT_DECODE_GZIP_SUFFIX);
            }
            return new FederatedComputeHttpResponse.Builder()
                    .setPayload(resource.getInlineResource().getData().toByteArray())
                    .setStatusCode(HTTP_OK_STATUS)
                    .setHeaders(new HashMap<String, List<String>>())
                    .build();
        }
        throw new UnsupportedOperationException("Unknown Resource type");
    }
}
