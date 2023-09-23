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
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_OK_STATUS;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;
import com.android.federatedcompute.services.training.util.ComputationResult;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federatedcompute.v1.ClientVersion;
import com.google.internal.federatedcompute.v1.Resource;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest.Result;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;
import com.google.ondevicepersonalization.federatedcompute.proto.UploadInstruction;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

/** Implements a single session of HTTP-based federated compute protocol. */
public final class HttpFederatedProtocol {
    public static final String TAG = "HttpFederatedProtocol";
    private static final int BUFFER_SIZE = 1024;

    private final String mClientVersion;
    private final String mPopulationName;
    private final HttpClient mHttpClient;
    private String mTaskId;
    private String mAggregationId;
    private String mAssignmentId;
    private final ProtocolRequestCreator mTaskAssignmentRequestCreator;

    @VisibleForTesting
    HttpFederatedProtocol(
            String entryUri, String clientVersion, String populationName, HttpClient httpClient) {
        this.mClientVersion = clientVersion;
        this.mPopulationName = populationName;
        this.mHttpClient = httpClient;
        this.mTaskAssignmentRequestCreator =
                new ProtocolRequestCreator(entryUri, new HashMap<>(), false);
    }

    /** Creates a HttpFederatedProtocol object. */
    public static HttpFederatedProtocol create(
            String entryUri, String clientVersion, String populationName) {
        return new HttpFederatedProtocol(entryUri, clientVersion, populationName, new HttpClient());
    }

    /** Helper function to perform check in and download federated task from remote servers. */
    public ListenableFuture<CheckinResult> issueCheckin() {
        ListenableFuture<TaskAssignment> taskAssignmentFuture =
                FluentFuture.from(createTaskAssignment())
                        .transform(
                                getTaskAssignmentHttpResponse ->
                                        getTaskAssignment(getTaskAssignmentHttpResponse),
                                getLightweightExecutor());

        ListenableFuture<FederatedComputeHttpResponse> planDataResponseFuture =
                FluentFuture.from(taskAssignmentFuture)
                        .transformAsync(
                                taskAssignment -> fetchTaskResource(taskAssignment.getPlan()),
                                getBackgroundExecutor());

        ListenableFuture<FederatedComputeHttpResponse> checkpointDataResponseFuture =
                FluentFuture.from(taskAssignmentFuture)
                        .transformAsync(
                                taskAssignment ->
                                        fetchTaskResource(taskAssignment.getInitCheckpoint()),
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

    /** Helper functions to reporting result and upload result. */
    public FluentFuture<Void> reportResult(ComputationResult computationResult) {
        return FluentFuture.from(performReportResult(computationResult))
                .transformAsync(
                        reportResp ->
                                processReportResultResponseAndUploadResult(
                                        reportResp, computationResult),
                        getBackgroundExecutor())
                .transform(
                        resp -> {
                            validateHttpResponseStatus("Upload result", resp);
                            return null;
                        },
                        getLightweightExecutor());
    }

    private ListenableFuture<FederatedComputeHttpResponse> createTaskAssignment() {
        CreateTaskAssignmentRequest request =
                CreateTaskAssignmentRequest.newBuilder()
                        .setClientVersion(ClientVersion.newBuilder().setVersionCode(mClientVersion))
                        .build();
        String taskAssignmentUriSuffix =
                String.format("/v1/population/%1$s:create-task-assignment", mPopulationName);
        FederatedComputeHttpRequest httpRequest =
                mTaskAssignmentRequestCreator.createProtoRequest(
                        taskAssignmentUriSuffix,
                        HttpMethod.POST,
                        request.toByteArray(),
                        /* isProtobufEncoded= */ true);
        return mHttpClient.performRequestAsync(httpRequest);
    }

    private TaskAssignment getTaskAssignment(FederatedComputeHttpResponse httpResponse) {
        validateHttpResponseStatus("Start task assignment", httpResponse);
        CreateTaskAssignmentResponse taskAssignmentResponse;
        try {
            taskAssignmentResponse =
                    CreateTaskAssignmentResponse.parseFrom(httpResponse.getPayload());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Could not parse StartTaskAssignmentResponse proto", e);
        }
        LogUtil.i(TAG, "start task assignment response: %s", taskAssignmentResponse);
        if (taskAssignmentResponse.hasRejectionInfo()) {
            throw new IllegalStateException("Device rejected by server.");
        }
        if (!taskAssignmentResponse.hasTaskAssignment()) {
            throw new IllegalStateException(
                    "Could not find both task assignment and rejection info.");
        }
        validateTaskAssignment(taskAssignmentResponse.getTaskAssignment());
        return taskAssignmentResponse.getTaskAssignment();
    }

    private void validateTaskAssignment(TaskAssignment taskAssignment) {
        Preconditions.checkArgument(
                taskAssignment.getPopulationName().equals(mPopulationName),
                "Population name should match");
        // These fields are required to construct ReportResultRequest.
        Preconditions.checkArgument(
                !taskAssignment.getTaskId().isEmpty(), "Task id should not be empty");
        Preconditions.checkArgument(
                !taskAssignment.getAggregationId().isEmpty(), "Aggregation id should not be empty");
        Preconditions.checkArgument(
                !taskAssignment.getAssignmentId().isEmpty(), "Assignment id should not be empty");
        this.mTaskId = taskAssignment.getTaskId();
        this.mAggregationId = taskAssignment.getAggregationId();
        this.mAssignmentId = taskAssignment.getAssignmentId();
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
            validateHttpResponseStatus("Fetch plan", planDataResponse);
            validateHttpResponseStatus("Fetch checkpoint", checkpointDataResponse);
            ClientOnlyPlan clientOnlyPlan;
            try {
                clientOnlyPlan = ClientOnlyPlan.parseFrom(planDataResponse.getPayload());

            } catch (InvalidProtocolBufferException e) {
                LogUtil.e(TAG, e, "Could not parse ClientOnlyPlan proto");
                return Futures.immediateFailedFuture(
                        new IllegalStateException("Could not parse ClientOnlyPlan proto", e));
            }
            return Futures.immediateFuture(
                    new CheckinResult(
                            checkpointDataResponse.getPayload(), clientOnlyPlan, taskAssignment));

        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<FederatedComputeHttpResponse> performReportResult(
            ComputationResult computationResult) {
        Result result = computationResult.isResultSuccess() ? Result.COMPLETED : Result.FAILED;
        ReportResultRequest startDataUploadRequest =
                ReportResultRequest.newBuilder().setResult(result).build();
        String startDataUploadUri =
                String.format(
                        "/v1/population/%1$s/task/%2$s/aggregation/%3$s/task-assignment/%4$s:report-result",
                        mPopulationName, mTaskId, mAggregationId, mAssignmentId);
        FederatedComputeHttpRequest httpRequest =
                mTaskAssignmentRequestCreator.createProtoRequest(
                        startDataUploadUri,
                        HttpMethod.PUT,
                        startDataUploadRequest.toByteArray(),
                        /* isProtobufEncoded= */ true);
        return mHttpClient.performRequestAsync(httpRequest);
    }

    private ListenableFuture<FederatedComputeHttpResponse>
            processReportResultResponseAndUploadResult(
                    FederatedComputeHttpResponse httpResponse,
                    ComputationResult computationResult) {
        try {
            validateHttpResponseStatus("ReportResult", httpResponse);
            ReportResultResponse reportResultResponse =
                    ReportResultResponse.parseFrom(httpResponse.getPayload());

            // TODO(b/297605806): better handle rejection info.
            if (reportResultResponse.hasRejectionInfo()) {
                return Futures.immediateFailedFuture(
                        new IllegalStateException(
                                "ReportResult got rejection: " + httpResponse.getStatusCode()));
            }
            Preconditions.checkArgument(
                    !computationResult.getOutputCheckpointFile().isEmpty(),
                    "Output checkpoint file should not be empty");
            byte[] outputBytes = readFileAsByteArray(computationResult.getOutputCheckpointFile());

            UploadInstruction uploadInstruction = reportResultResponse.getUploadInstruction();
            Preconditions.checkArgument(
                    !uploadInstruction.getUploadLocation().isEmpty(),
                    "UploadInstruction.upload_location must not be empty");
            HashMap<String, String> requestHeader = new HashMap<>();
            uploadInstruction
                    .getExtraRequestHeadersMap()
                    .forEach(
                            (key, value) -> {
                                requestHeader.put(key, value);
                            });
            FederatedComputeHttpRequest httpUploadRequest =
                    FederatedComputeHttpRequest.create(
                            uploadInstruction.getUploadLocation(),
                            HttpMethod.PUT,
                            requestHeader,
                            outputBytes,
                            /* useCompression= */ false);
            return mHttpClient.performRequestAsync(httpUploadRequest);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private void validateHttpResponseStatus(
            String stage, FederatedComputeHttpResponse httpResponse) {
        if (!HTTP_OK_STATUS.contains(httpResponse.getStatusCode())) {
            throw new IllegalStateException(stage + " failed: " + httpResponse.getStatusCode());
        }
    }

    private byte[] readFileAsByteArray(String filePath) throws IOException {
        File file = new File(filePath);
        long fileLength = file.length();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream((int) fileLength);
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int len = inputStream.read(buffer); len > 0; len = inputStream.read(buffer)) {
                outputStream.write(buffer, 0, len);
            }
        } catch (IOException e) {
            LogUtil.e(TAG, e, "Failed to read the content of binary file %s", filePath);
            throw e;
        }
        return outputStream.toByteArray();
    }

    private ListenableFuture<FederatedComputeHttpResponse> fetchTaskResource(Resource resource) {
        switch (resource.getResourceCase()) {
            case URI:
                Preconditions.checkArgument(
                        !resource.getUri().isEmpty(), "Resource.uri must be non-empty when set");
                FederatedComputeHttpRequest httpRequest =
                        FederatedComputeHttpRequest.create(
                                resource.getUri(),
                                HttpMethod.GET,
                                new HashMap<String, String>(),
                                HttpClientUtil.EMPTY_BODY,
                                /* useCompression= */ false);
                return mHttpClient.performRequestAsync(httpRequest);
            case INLINE_RESOURCE:
                return Futures.immediateFailedFuture(
                        new UnsupportedOperationException("Inline resource is not supported yet."));
            default:
                return Futures.immediateFailedFuture(
                        new UnsupportedOperationException("Unknown Resource type"));
        }
    }
}
