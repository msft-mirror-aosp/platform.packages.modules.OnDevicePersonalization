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

import static com.android.odp.module.common.HttpClientUtils.CONTENT_TYPE_HDR;
import static com.android.odp.module.common.HttpClientUtils.PROTOBUF_CONTENT_TYPE;

import android.content.Context;
import android.util.Base64;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.HttpClient;
import com.android.odp.module.common.HttpClientUtils;
import com.android.odp.module.common.OdpHttpRequest;
import com.android.odp.module.common.OdpHttpResponse;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.internal.federatedcompute.v1.ResourceCompressionFormat;
import com.google.internal.federatedcompute.v1.UploadInstruction;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportExceptionRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportExceptionResponse;
import com.google.protobuf.Timestamp;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages the http connection and request/response from client->server for one error report.
 *
 * <p>Called into by the {@link AggregatedErrorReportingWorker} to offload the details of http
 * connection and request/response.
 *
 * <p>The {@link ErrorData} to be reported and the vendor URL/path are set at creation time, refer
 * to {@link #createAggregatedErrorReportingProtocol(ImmutableList, String, Context)} for details.
 */
class AggregatedErrorReportingProtocol implements ReportingProtocol {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AggregatedErrorReportingProtocol.class.getSimpleName();

    /** Data to be reported. */
    private final ImmutableList<ErrorData> mErrorData;

    private final String mRequestBaseUri;
    private final ImmutableMap<String, String> mHeaderList;

    // TODO(b/329921267): update proto to include client version.
    private final long mClientVersion;

    private final HttpClient mHttpClient;

    private final Injector mInjector;

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getBlockingExecutor() {
            return OnDevicePersonalizationExecutors.getBlockingExecutor();
        }

        ListeningExecutorService getBackgroundExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        ListeningScheduledExecutorService getScheduledExecutor() {
            return OnDevicePersonalizationExecutors.getScheduledExecutor();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        // Allows for easier injection a mock client in tests.
        HttpClient getClient() {
            return new HttpClient(
                    getFlags().getAggregatedErrorReportingHttpRetryLimit(), getBlockingExecutor());
        }
    }

    private AggregatedErrorReportingProtocol(
            ImmutableList<ErrorData> errorData,
            String requestBaseUri,
            ImmutableMap<String, String> headerList,
            long clientVersion,
            Injector injector) {
        this.mErrorData = errorData;
        this.mRequestBaseUri = requestBaseUri;
        this.mHeaderList = headerList;
        this.mClientVersion = clientVersion;
        this.mInjector = injector;
        this.mHttpClient = injector.getClient();
    }

    /**
     * Creates and returns a new {@link AggregatedErrorReportingProtocol} object to manage the
     * lifecycle of reporting for one vendor's data based on given {@link ErrorData} and the vendors
     * server URL.
     */
    static AggregatedErrorReportingProtocol createAggregatedErrorReportingProtocol(
            ImmutableList<ErrorData> errorData, String requestBaseUri, Context context) {
        return createAggregatedErrorReportingProtocol(
                errorData, requestBaseUri, PackageUtils.getApexVersion(context), new Injector());
    }

    @VisibleForTesting
    static AggregatedErrorReportingProtocol createAggregatedErrorReportingProtocol(
            ImmutableList<ErrorData> errorData,
            String requestBaseUri,
            long clientVersion,
            Injector injector) {
        // Test only version of creator method.
        return new AggregatedErrorReportingProtocol(
                errorData, requestBaseUri, ImmutableMap.of(), clientVersion, injector);
    }

    /**
     * Report the exception data for this vendor based on error data and URL provided during
     * construction.
     *
     * @return a {@link ListenableFuture} that resolves with true/false when reporting is
     *     successful/failed.
     */
    public ListenableFuture<Boolean> reportExceptionData() {
        // TODO(b/329921267): add encryption and authorization support
        // First report ReportExceptionRequest, then upload result
        try {
            Preconditions.checkState(!mErrorData.isEmpty() && !mRequestBaseUri.isEmpty());
            String requestUri = getRequestUri(mRequestBaseUri, mInjector.getFlags());

            // Report exception request, to get upload location from server.
            ListenableFuture<OdpHttpResponse> reportRequest =
                    mHttpClient.performRequestAsyncWithRetry(
                            getHttpRequest(
                                    requestUri,
                                    new HashMap<>(mHeaderList),
                                    getReportRequest().toByteArray()));

            // Perform upload based on server provided response.
            ListenableFuture<Boolean> reportFuture =
                    FluentFuture.from(reportRequest)
                            .transformAsync(
                                    this::uploadExceptionData, mInjector.getBackgroundExecutor())
                            .transform(
                                    response ->
                                            validateHttpResponseStatus(
                                                    /* stage= */ "reportRequest", response),
                                    mInjector.getBackgroundExecutor());

            return FluentFuture.from(reportFuture)
                    .withTimeout(
                            mInjector.getFlags().getAggregatedErrorReportingHttpTimeoutSeconds(),
                            TimeUnit.SECONDS,
                            mInjector.getScheduledExecutor());
        } catch (Exception e) {
            sLogger.e(TAG + " : failed to  report exception data.", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    @VisibleForTesting
    ListenableFuture<OdpHttpResponse> uploadExceptionData(OdpHttpResponse response) {
        try {
            validateHttpResponseStatus(/* stage= */ "reportRequest", response);
            ReportExceptionResponse uploadResponse =
                    ReportExceptionResponse.parseFrom(response.getPayload());
            UploadInstruction uploadInstruction = uploadResponse.getUploadInstruction();
            Preconditions.checkArgument(
                    !uploadInstruction.getUploadLocation().isEmpty(),
                    "UploadInstruction.upload_location must not be empty");
            byte[] outputBytes = createEncryptedRequestBody();
            // Apply a top-level compression to the payload.
            if (uploadInstruction.getCompressionFormat()
                    == ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP) {
                outputBytes = HttpClientUtils.compressWithGzip(outputBytes);
            }
            HashMap<String, String> requestHeader =
                    new HashMap<>(uploadInstruction.getExtraRequestHeadersMap());
            OdpHttpRequest httpUploadRequest =
                    getHttpRequest(
                            uploadInstruction.getUploadLocation(), requestHeader, outputBytes);
            return mHttpClient.performRequestAsyncWithRetry(httpUploadRequest);
        } catch (Exception e) {
            sLogger.e(
                    TAG
                            + " : failed to receive response for report request for URI : "
                            + mRequestBaseUri,
                    e);
            return Futures.immediateFailedFuture(e);
        }
    }

    @VisibleForTesting
    byte[] createEncryptedRequestBody() throws JSONException {
        // Creates and encrypts the error data that is uploaded to the server.
        // create payload
        com.google.ondevicepersonalization.federatedcompute.proto.ErrorDataList errorDataList =
                convertToProto(mErrorData);
        byte[] output = errorDataList.toByteArray();
        final JSONObject body = new JSONObject();

        // TODO(b/329921267): add encryption support
        body.put(
                AggregatedErrorDataPayloadContract.ENCRYPTED_PAYLOAD,
                Base64.encodeToString(output, Base64.NO_WRAP));
        return body.toString().getBytes();
    }

    private static boolean validateHttpResponseStatus(String stage, OdpHttpResponse httpResponse) {
        if (!HttpClientUtils.HTTP_OK_STATUS.contains(httpResponse.getStatusCode())) {
            throw new IllegalStateException(stage + " failed: " + httpResponse.getStatusCode());
        }
        // Automated testing would rely on this log.
        sLogger.i(TAG, stage + " success.");
        return true;
    }

    @VisibleForTesting
    /* Gets the full request URI based on the */
    static String getRequestUri(String requestBaseUri, Flags flags) {
        // By default https://{host}/debugreporting/v1/exceptions:report-exceptions
        return HttpClientUtils.joinBaseUriWithSuffix(
                requestBaseUri, flags.getAggregatedErrorReportingServerPath());
    }

    private static ReportExceptionRequest getReportRequest() {
        Timestamp requestTime =
                Timestamp.newBuilder().setSeconds(DateTimeUtils.epochSecondsUtc()).build();
        return ReportExceptionRequest.newBuilder()
                .setRequestTimestamp(requestTime)
                .setResourceCapabilities(HttpClientUtils.getResourceCapabilities())
                .build();
    }

    @VisibleForTesting
    static com.google.ondevicepersonalization.federatedcompute.proto.ErrorDataList convertToProto(
            List<ErrorData> errorDataList) {
        com.google.ondevicepersonalization.federatedcompute.proto.ErrorDataList.Builder builder =
                com.google.ondevicepersonalization.federatedcompute.proto.ErrorDataList
                        .newBuilder();
        for (ErrorData errorData : errorDataList) {
            builder.addErrorData(convertToProto(errorData));
        }
        return builder.build();
    }

    private static com.google.ondevicepersonalization.federatedcompute.proto.ErrorData
            convertToProto(ErrorData errorDataPojo) {
        // convert from pojo to proto error data
        com.google.ondevicepersonalization.federatedcompute.proto.ErrorData.Builder builder =
                com.google.ondevicepersonalization.federatedcompute.proto.ErrorData.newBuilder();
        builder.setErrorCode(errorDataPojo.getErrorCode())
                .setErrorCount(errorDataPojo.getErrorCount())
                .setEpochDay(errorDataPojo.getEpochDay())
                .setServicePackageVersion(errorDataPojo.getServicePackageVersion());
        return builder.build();
    }

    private static OdpHttpRequest getHttpRequest(
            String uri, Map<String, String> requestHeaders, byte[] body) {
        // Helper method for http request that contains serialized proto payload.
        HashMap<String, String> headers = new HashMap<>(requestHeaders);
        headers.put(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);
        return OdpHttpRequest.create(uri, HttpClientUtils.HttpMethod.PUT, headers, body);
    }

    @VisibleForTesting
    static final class AggregatedErrorDataPayloadContract {
        // TODO(b/329921267): add encryption support and populate/use key-id etc.
        public static final String KEY_ID = "keyId";

        public static final String ENCRYPTED_PAYLOAD = "encryptedPayload";
    }
}
