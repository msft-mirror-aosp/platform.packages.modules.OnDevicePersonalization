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

import static com.android.federatedcompute.services.common.Constants.TRACE_HTTP_ISSUE_CHECKIN;
import static com.android.federatedcompute.services.common.Constants.TRACE_HTTP_REPORT_RESULT;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBackgroundExecutor;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getLightweightExecutor;
import static com.android.federatedcompute.services.common.FileUtils.createTempFile;
import static com.android.federatedcompute.services.common.FileUtils.readFileAsByteArray;
import static com.android.federatedcompute.services.common.FileUtils.writeToFile;
import static com.android.federatedcompute.services.http.HttpClientUtil.ACCEPT_ENCODING_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.FCP_OWNER_ID_DIGEST;
import static com.android.federatedcompute.services.http.HttpClientUtil.GZIP_ENCODING_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_OK_OR_UNAUTHENTICATED_STATUS;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_OK_STATUS;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_UNAUTHENTICATED_STATUS;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_AUTHENTICATION_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_AUTHORIZATION_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_IDEMPOTENCY_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.compressWithGzip;
import static com.android.federatedcompute.services.http.HttpClientUtil.getTotalReceivedBytes;
import static com.android.federatedcompute.services.http.HttpClientUtil.getTotalSentBytes;
import static com.android.federatedcompute.services.http.HttpClientUtil.uncompressWithGzip;

import android.content.Context;
import android.os.Trace;
import android.util.Base64;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.NetworkStats;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.data.ODPAuthorizationToken;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.federatedcompute.services.encryption.Encrypter;
import com.android.federatedcompute.services.http.HttpClientUtil.FederatedComputePayloadDataContract;
import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;
import com.android.federatedcompute.services.training.util.ComputationResult;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federatedcompute.v1.ClientVersion;
import com.google.internal.federatedcompute.v1.RejectionInfo;
import com.google.internal.federatedcompute.v1.Resource;
import com.google.internal.federatedcompute.v1.ResourceCapabilities;
import com.google.internal.federatedcompute.v1.ResourceCompressionFormat;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest.Result;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;
import com.google.ondevicepersonalization.federatedcompute.proto.UploadInstruction;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/** Implements a single session of HTTP-based federated compute protocol. */
public final class HttpFederatedProtocol {
    public static final String TAG = HttpFederatedProtocol.class.getSimpleName();
    private final String mClientVersion;
    private final String mPopulationName;
    private final HttpClient mHttpClient;
    private final ProtocolRequestCreator mTaskAssignmentRequestCreator;
    private final Encrypter mEncrypter;
    private final TrainingEventLogger mTrainingEventLogger;
    private String mTaskId;
    private String mAggregationId;
    private String mAssignmentId;

    private final ODPAuthorizationTokenDao mODPAuthorizationTokenDao;

    private final Clock mClock;

    // 7 days in milliseconds
    private static final long ODP_AUTHORIZATION_TOKEN_TTL = 7 * 24 * 60 * 60 * 1000L;

    @VisibleForTesting
    HttpFederatedProtocol(
            String entryUri,
            String clientVersion,
            String populationName,
            HttpClient httpClient,
            Encrypter encrypter,
            TrainingEventLogger trainingEventLogger,
            ODPAuthorizationTokenDao odpAuthorizationTokenDao,
            Clock clock) {
        this.mClientVersion = clientVersion;
        this.mPopulationName = populationName;
        this.mHttpClient = httpClient;
        this.mTaskAssignmentRequestCreator = new ProtocolRequestCreator(entryUri, new HashMap<>());
        this.mEncrypter = encrypter;
        this.mTrainingEventLogger = trainingEventLogger;
        this.mODPAuthorizationTokenDao = odpAuthorizationTokenDao;
        this.mClock = clock;
    }

    /** Creates a HttpFederatedProtocol object. */
    public static HttpFederatedProtocol create(
            Context context,
            String entryUri,
            String clientVersion,
            String populationName,
            Encrypter encrypter,
            TrainingEventLogger trainingEventLogger) {
        return new HttpFederatedProtocol(
                entryUri,
                clientVersion,
                populationName,
                new HttpClient(),
                encrypter,
                trainingEventLogger,
                ODPAuthorizationTokenDao.getInstance(context),
                MonotonicClock.getInstance());
    }

    /**
     * Helper function to perform check in and download federated tasks. AttestationRecord is
     * skipped.
     */
    public ListenableFuture<CheckinResult> issueCheckin(
            String ownerId, String ownerIdCertDigest, boolean allowUnauthenticated) {
        return issueCheckin(ownerId, ownerIdCertDigest, allowUnauthenticated, null);
    }

    /** Helper function to perform check in and download federated task from remote servers. */
    public ListenableFuture<CheckinResult> issueCheckin(
            String ownerId,
            String ownerIdCertDigest,
            boolean allowUnauthenticated,
            List<String> attestationRecord) {
        Trace.beginAsyncSection(TRACE_HTTP_ISSUE_CHECKIN, 0);
        // Clear task id before issue checkin request.
        mTrainingEventLogger.setTaskId(0);
        NetworkStats networkStats = new NetworkStats();

        return FluentFuture.from(
                        createTaskAssignment(
                                ownerId, ownerIdCertDigest, networkStats, attestationRecord))
                .transformAsync(
                        federatedComputeHttpResponse -> {
                            if (federatedComputeHttpResponse.getStatusCode()
                                    == HTTP_UNAUTHENTICATED_STATUS) {
                                mODPAuthorizationTokenDao.deleteAuthorizationToken(ownerId);
                            }
                            validateHttpResponseStatus(
                                    "Start task assignment",
                                    federatedComputeHttpResponse,
                                    allowUnauthenticated
                                            ? HTTP_OK_OR_UNAUTHENTICATED_STATUS
                                            : HTTP_OK_STATUS);
                            networkStats.addBytesDownloaded(
                                    getTotalReceivedBytes(federatedComputeHttpResponse));
                            CreateTaskAssignmentResponse taskAssignmentResponse;
                            try {
                                taskAssignmentResponse =
                                        CreateTaskAssignmentResponse.parseFrom(
                                                federatedComputeHttpResponse.getPayload());
                            } catch (InvalidProtocolBufferException e) {
                                mTrainingEventLogger.logCheckinInvalidPayload(networkStats);
                                throw new IllegalStateException(
                                        "Could not parse StartTaskAssignmentResponse proto", e);
                            }
                            if (taskAssignmentResponse.hasRejectionInfo()) {
                                mTrainingEventLogger.logCheckinRejected(networkStats);
                                return Futures.immediateFuture(
                                        new CheckinResult(
                                                taskAssignmentResponse.getRejectionInfo()));
                            }
                            TaskAssignment taskAssignment =
                                    getTaskAssignment(taskAssignmentResponse);
                            mTrainingEventLogger.setTaskId(taskAssignment.getTaskName().hashCode());
                            mTrainingEventLogger.logCheckinPlanUriReceived(networkStats);
                            NetworkStats resourceStats = new NetworkStats();
                            return checkinSuccessfulTaskAssignment(taskAssignment, resourceStats);
                        },
                        getBackgroundExecutor());
    }

    private ListenableFuture<CheckinResult> checkinSuccessfulTaskAssignment(
            TaskAssignment taskAssignment, NetworkStats resourceStats) {
        ListenableFuture<FederatedComputeHttpResponse> planDataResponseFuture =
                fetchTaskResource(taskAssignment.getPlan(), resourceStats);
        ListenableFuture<FederatedComputeHttpResponse> checkpointDataResponseFuture =
                fetchTaskResource(taskAssignment.getInitCheckpoint(), resourceStats);
        return Futures.whenAllSucceed(planDataResponseFuture, checkpointDataResponseFuture)
                .call(
                        new Callable<CheckinResult>() {
                            @Override
                            public CheckinResult call() throws Exception {
                                return getCheckinResult(
                                        planDataResponseFuture,
                                        checkpointDataResponseFuture,
                                        taskAssignment,
                                        resourceStats);
                            }
                        },
                        getBackgroundExecutor());
    }

    /** Helper functions to reporting result and upload result. */
    public FluentFuture<RejectionInfo> reportResult(
            ComputationResult computationResult,
            FederatedComputeEncryptionKey encryptionKey,
            String ownerId,
            Boolean allowUnauthenticated,
            List<String> attestationRecord) {
        Trace.beginAsyncSection(TRACE_HTTP_REPORT_RESULT, 0);
        NetworkStats uploadStats = new NetworkStats();
        if (computationResult != null
                && computationResult.isResultSuccess()
                && encryptionKey != null) {
            return FluentFuture.from(
                            performReportResult(
                                    computationResult, ownerId, attestationRecord, uploadStats))
                    .transformAsync(
                            reportResp -> {
                                uploadStats.addBytesDownloaded(getTotalReceivedBytes(reportResp));
                                if (reportResp.getStatusCode() == HTTP_UNAUTHENTICATED_STATUS) {
                                    mODPAuthorizationTokenDao.deleteAuthorizationToken(ownerId);
                                }
                                ImmutableSet<Integer> acceptableStatuses =
                                        allowUnauthenticated
                                                ? HTTP_OK_OR_UNAUTHENTICATED_STATUS
                                                : HTTP_OK_STATUS;
                                ReportResultResponse reportResultResponse =
                                        getReportResultResponse(reportResp, acceptableStatuses);
                                if (reportResultResponse.hasRejectionInfo()) {
                                    mTrainingEventLogger.logResultUploadRejected(uploadStats);
                                    return Futures.immediateFuture(
                                            reportResultResponse.getRejectionInfo());
                                }
                                return FluentFuture.from(
                                                processReportResultResponseAndUploadResult(
                                                        reportResultResponse,
                                                        computationResult,
                                                        encryptionKey,
                                                        uploadStats))
                                        .transform(
                                                resp -> {
                                                    validateHttpResponseStatus(
                                                            "Upload result", resp, HTTP_OK_STATUS);
                                                    mTrainingEventLogger.logResultUploadCompleted(
                                                            uploadStats);
                                                    Trace.endAsyncSection(
                                                            TRACE_HTTP_REPORT_RESULT, 0);
                                                    return null;
                                                },
                                                getLightweightExecutor());
                            },
                            getBackgroundExecutor());
        } else {
            return FluentFuture.from(
                            performReportResult(
                                    computationResult, ownerId, attestationRecord, uploadStats))
                    .transform(
                            resp -> {
                                validateHttpResponseStatus(
                                        "Report failure result", resp, HTTP_OK_STATUS);
                                uploadStats.addBytesDownloaded(getTotalReceivedBytes(resp));
                                mTrainingEventLogger.logFailureResultUploadCompleted(uploadStats);
                                return null;
                            },
                            getLightweightExecutor());
        }
    }

    private ListenableFuture<FederatedComputeHttpResponse> createTaskAssignment(
            String ownerIdentifier,
            String ownerIdCertDigest,
            NetworkStats networkStats,
            List<String> attestationRecord) {
        CreateTaskAssignmentRequest request =
                CreateTaskAssignmentRequest.newBuilder()
                        .setClientVersion(ClientVersion.newBuilder().setVersionCode(mClientVersion))
                        .setResourceCapabilities(
                                ResourceCapabilities.newBuilder()
                                        .addSupportedCompressionFormats(
                                                ResourceCompressionFormat
                                                        .RESOURCE_COMPRESSION_FORMAT_GZIP))
                        .build();

        String taskAssignmentUriSuffix =
                String.format(
                        "/taskassignment/v1/population/%1$s:create-task-assignment",
                        mPopulationName);

        Map<String, String> headers = constructRequestHeader(attestationRecord, ownerIdentifier);
        headers.put(ODP_IDEMPOTENCY_KEY, System.currentTimeMillis() + " - " + UUID.randomUUID());
        headers.put(FCP_OWNER_ID_DIGEST, ownerIdentifier + "-" + ownerIdCertDigest);
        FederatedComputeHttpRequest httpRequest =
                mTaskAssignmentRequestCreator.createProtoRequest(
                        taskAssignmentUriSuffix,
                        HttpMethod.POST,
                        headers,
                        request.toByteArray(),
                        /* isProtobufEncoded= */ true);
        mTrainingEventLogger.logCheckinStarted();
        networkStats.addBytesUploaded(getTotalSentBytes(httpRequest));
        return mHttpClient.performRequestAsyncWithRetry(httpRequest);
    }

    private TaskAssignment getTaskAssignment(CreateTaskAssignmentResponse taskAssignmentResponse) {
        if (taskAssignmentResponse.hasRejectionInfo()) {
            throw new IllegalStateException("Device rejected by server.");
        }
        if (!taskAssignmentResponse.hasTaskAssignment()) {
            throw new IllegalStateException(
                    "Could not find both task assignment and rejection info.");
        }
        validateTaskAssignment(taskAssignmentResponse.getTaskAssignment());
        TaskAssignment taskAssignment = taskAssignmentResponse.getTaskAssignment();
        LogUtil.d(
                TAG,
                "Receive CreateTaskAssignmentResponse: task name %s assignment id %s",
                taskAssignment.getTaskName(),
                taskAssignment.getAssignmentId());
        return taskAssignment;
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

    private CheckinResult getCheckinResult(
            ListenableFuture<FederatedComputeHttpResponse> planDataResponseFuture,
            ListenableFuture<FederatedComputeHttpResponse> checkpointDataResponseFuture,
            TaskAssignment taskAssignment,
            NetworkStats networkStats)
            throws Exception {

        FederatedComputeHttpResponse planDataResponse = Futures.getDone(planDataResponseFuture);
        FederatedComputeHttpResponse checkpointDataResponse =
                Futures.getDone(checkpointDataResponseFuture);
        validateHttpResponseStatus("Fetch plan", planDataResponse, HTTP_OK_STATUS);
        validateHttpResponseStatus("Fetch checkpoint", checkpointDataResponse, HTTP_OK_STATUS);
        networkStats.addBytesDownloaded(getTotalReceivedBytes(planDataResponse));
        networkStats.addBytesDownloaded(getTotalReceivedBytes(checkpointDataResponse));
        // Process download ClientOnlyPlan.
        byte[] planData = planDataResponse.getPayload();
        if (taskAssignment.getPlan().getCompressionFormat()
                        == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP
                || planDataResponse.isResponseCompressed()) {
            planData = uncompressWithGzip(planData);
        }
        ClientOnlyPlan clientOnlyPlan;
        try {
            clientOnlyPlan = ClientOnlyPlan.parseFrom(planData);
        } catch (InvalidProtocolBufferException e) {
            LogUtil.e(TAG, e, "Could not parse ClientOnlyPlan proto");
            mTrainingEventLogger.logCheckinInvalidPayload(networkStats);
            throw new IllegalStateException("Could not parse ClientOnlyPlan proto", e);
        }
        mTrainingEventLogger.logCheckinFinished(networkStats);

        // Process download checkpoint resource.
        String inputCheckpointFile = createTempFile("input", ".ckp");
        byte[] checkpointData = checkpointDataResponse.getPayload();
        if (taskAssignment.getInitCheckpoint().getCompressionFormat()
                        == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP
                || checkpointDataResponse.isResponseCompressed()) {
            checkpointData = uncompressWithGzip(checkpointData);
        }
        writeToFile(inputCheckpointFile, checkpointData);
        Trace.endAsyncSection(TRACE_HTTP_ISSUE_CHECKIN, 0);
        return new CheckinResult(inputCheckpointFile, clientOnlyPlan, taskAssignment);
    }

    private ListenableFuture<FederatedComputeHttpResponse> performReportResult(
            ComputationResult computationResult,
            String ownerIdentifier,
            List<String> attestationRecord,
            NetworkStats networkStats) {
        Result result =
                computationResult == null ? Result.FAILED : computationResult.convertToResult();
        if (result == Result.COMPLETED) {
            mTrainingEventLogger.logResultUploadStarted();
        } else {
            mTrainingEventLogger.logFailureResultUploadStarted();
        }
        ReportResultRequest startDataUploadRequest =
                ReportResultRequest.newBuilder().setResult(result).build();
        String startDataUploadUri =
                String.format(
                        "/taskassignment/v1/population/%1$s/task/%2$s/aggregation"
                                + "/%3$s/task-assignment/%4$s:report-result",
                        mPopulationName, mTaskId, mAggregationId, mAssignmentId);
        LogUtil.d(
                TAG,
                "send ReportResultRequest: population name %s, task name %s,"
                        + " assignment id %s, result %s",
                mPopulationName,
                mTaskId,
                mAssignmentId,
                result.toString());
        Map<String, String> headers = constructRequestHeader(attestationRecord, ownerIdentifier);
        FederatedComputeHttpRequest httpRequest =
                mTaskAssignmentRequestCreator.createProtoRequest(
                        startDataUploadUri,
                        HttpMethod.PUT,
                        headers,
                        startDataUploadRequest.toByteArray(),
                        /* isProtobufEncoded= */ true);
        networkStats.addBytesUploaded(getTotalSentBytes(httpRequest));
        return mHttpClient.performRequestAsyncWithRetry(httpRequest);
    }

    private ListenableFuture<FederatedComputeHttpResponse>
            processReportResultResponseAndUploadResult(
                    ReportResultResponse reportResultResponse,
                    ComputationResult computationResult,
                    FederatedComputeEncryptionKey encryptionKey,
                    NetworkStats networkStats) {
        try {
            Preconditions.checkArgument(
                    !computationResult.getOutputCheckpointFile().isEmpty(),
                    "Output checkpoint file should not be empty");
            UploadInstruction uploadInstruction = reportResultResponse.getUploadInstruction();
            Preconditions.checkArgument(
                    !uploadInstruction.getUploadLocation().isEmpty(),
                    "UploadInstruction.upload_location must not be empty");
            byte[] outputBytes =
                    createEncryptedRequestBody(
                            computationResult.getOutputCheckpointFile(), encryptionKey);
            // Apply a top-level compression to the payload.
            if (uploadInstruction.getCompressionFormat()
                    == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP) {
                outputBytes = compressWithGzip(outputBytes);
            }
            HashMap<String, String> requestHeader = new HashMap<>();
            uploadInstruction
                    .getExtraRequestHeadersMap()
                    .forEach(
                            (key, value) -> {
                                requestHeader.put(key, value);
                            });
            LogUtil.d(
                    TAG,
                    "Start upload training result: population name %s, task name %s,"
                            + " assignment id %s",
                    mPopulationName,
                    mTaskId,
                    mAssignmentId);
            FederatedComputeHttpRequest httpUploadRequest =
                    FederatedComputeHttpRequest.create(
                            uploadInstruction.getUploadLocation(),
                            HttpMethod.PUT,
                            requestHeader,
                            outputBytes);
            networkStats.addBytesUploaded(getTotalSentBytes(httpUploadRequest));
            return mHttpClient.performRequestAsyncWithRetry(httpUploadRequest);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private byte[] createEncryptedRequestBody(
            String filePath, FederatedComputeEncryptionKey encryptionKey) throws Exception {
        byte[] fileOutputBytes = readFileAsByteArray(filePath);
        if (!FlagsFactory.getFlags().isEncryptionEnabled()) {
            // encryption not enabled, upload the file contents directly
            return fileOutputBytes;
        }
        fileOutputBytes = compressWithGzip(fileOutputBytes);
        // encryption
        byte[] publicKey = Base64.decode(encryptionKey.getPublicKey(), Base64.NO_WRAP);

        byte[] encryptedOutput =
                mEncrypter.encrypt(
                        publicKey,
                        fileOutputBytes,
                        FederatedComputePayloadDataContract.ASSOCIATED_DATA);
        // create payload
        final JSONObject body = new JSONObject();
        body.put(FederatedComputePayloadDataContract.KEY_ID, encryptionKey.getKeyIdentifier());
        body.put(
                FederatedComputePayloadDataContract.ENCRYPTED_PAYLOAD,
                Base64.encodeToString(encryptedOutput, Base64.NO_WRAP));
        body.put(
                FederatedComputePayloadDataContract.ASSOCIATED_DATA_KEY,
                Base64.encodeToString(
                        FederatedComputePayloadDataContract.ASSOCIATED_DATA, Base64.NO_WRAP));
        return body.toString().getBytes();
    }

    private ReportResultResponse getReportResultResponse(
            FederatedComputeHttpResponse httpResponse, ImmutableSet<Integer> acceptableStatuses)
            throws InvalidProtocolBufferException {
        validateHttpResponseStatus("ReportResult", httpResponse, acceptableStatuses);
        return ReportResultResponse.parseFrom(httpResponse.getPayload());
    }

    private void validateHttpResponseStatus(
            String stage,
            FederatedComputeHttpResponse httpResponse,
            ImmutableSet<Integer> acceptableStatuses) {
        if (!acceptableStatuses.contains(httpResponse.getStatusCode())) {
            throw new IllegalStateException(stage + " failed: " + httpResponse.getStatusCode());
        } else if (HTTP_OK_STATUS.contains(httpResponse.getStatusCode())) {
            // Don't change %s success because the automated testing would rely on this log.
            LogUtil.i(TAG, stage + " success.");
        } else {
            LogUtil.i(TAG, "%s response status validated: %d", stage, httpResponse.getStatusCode());
        }
    }

    private ListenableFuture<FederatedComputeHttpResponse> fetchTaskResource(
            Resource resource, NetworkStats networkStats) {
        switch (resource.getResourceCase()) {
            case URI:
                Preconditions.checkArgument(
                        !resource.getUri().isEmpty(), "Resource.uri must be non-empty when set");
                HashMap<String, String> headerList = new HashMap<>();
                if (resource.getCompressionFormat()
                        == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP) {
                    // Set this header to disable decompressive transcoding when download from
                    // Google Cloud Storage.
                    // https://cloud.google.com/storage/docs/transcoding#decompressive_transcoding
                    headerList.put(ACCEPT_ENCODING_HDR, GZIP_ENCODING_HDR);
                }
                LogUtil.d(TAG, "start fetch task resources");
                FederatedComputeHttpRequest httpRequest =
                        FederatedComputeHttpRequest.create(
                                resource.getUri(),
                                HttpMethod.GET,
                                headerList,
                                HttpClientUtil.EMPTY_BODY);
                networkStats.addBytesUploaded(getTotalSentBytes(httpRequest));
                return mHttpClient.performRequestAsyncWithRetry(httpRequest);
            case INLINE_RESOURCE:
                return Futures.immediateFailedFuture(
                        new UnsupportedOperationException("Inline resource is not supported yet."));
            default:
                return Futures.immediateFailedFuture(
                        new UnsupportedOperationException("Unknown Resource type"));
        }
    }

    private Map<String, String> constructRequestHeader(
            List<String> attestationRecord, String ownerIdentifier) {
        Map<String, String> headers = new HashMap<>();
        if (attestationRecord != null) {
            // Only when the device is solving challenge, the attestation record is not null.
            JSONArray attestationArr = new JSONArray(attestationRecord);
            headers.put(ODP_AUTHENTICATION_KEY, attestationArr.toString());
            // generate a UUID and the UUID would serve as the authorization token.
            String authTokenUUID = UUID.randomUUID().toString();
            headers.put(ODP_AUTHORIZATION_KEY, authTokenUUID);
            mODPAuthorizationTokenDao.insertAuthorizationToken(
                    new ODPAuthorizationToken.Builder()
                            .setAuthorizationToken(authTokenUUID)
                            .setOwnerIdentifier(ownerIdentifier)
                            .setCreationTime(mClock.currentTimeMillis())
                            .setExpiryTime(mClock.currentTimeMillis() + ODP_AUTHORIZATION_TOKEN_TTL)
                            .build());
        } else {
            ODPAuthorizationToken authToken =
                    mODPAuthorizationTokenDao.getUnexpiredAuthorizationToken(ownerIdentifier);
            if (authToken != null) {
                headers.put(ODP_AUTHORIZATION_KEY, authToken.getAuthorizationToken());
            }
        }
        return headers;
    }
}
