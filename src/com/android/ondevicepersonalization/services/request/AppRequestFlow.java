/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.request;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.ExecuteInputParcel;
import android.adservices.ondevicepersonalization.ExecuteOutputParcel;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.federatedcompute.FederatedComputeServiceImpl;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfig;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.process.ProcessRunnerImpl;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.CryptUtils;
import com.android.ondevicepersonalization.services.util.LogUtils;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.PrivacyUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles a surface package request from an app or SDK.
 */
public class AppRequestFlow {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AppRequestFlow.class.getSimpleName();
    private static final String TASK_NAME = "AppRequest";
    public static final String ISOLATED_MODEL_SERVICE_NAME =
            "com.android.ondevicepersonalization.services.inference.IsolatedModelService";
    @NonNull
    private final String mCallingPackageName;
    @NonNull
    private final ComponentName mService;
    @NonNull
    private final PersistableBundle mParams;
    @NonNull
    private final IExecuteCallback mCallback;
    @NonNull
    private final Context mContext;
    private final long mStartTimeMillis;
    private int mErrorCode = Constants.STATUS_SUCCESS;
    private AbstractServiceBinder<IIsolatedModelService> mModelService;

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        ListeningScheduledExecutorService getScheduledExecutor() {
            return OnDevicePersonalizationExecutors.getScheduledExecutor();
        }

        ProcessRunner getProcessRunner() {
            return ProcessRunnerImpl.getInstance();
        }

        boolean isPersonalizationStatusEnabled() {
            UserPrivacyStatus privacyStatus = UserPrivacyStatus.getInstance();
            return privacyStatus.isPersonalizationStatusEnabled();
        }

        boolean isOutputDataAllowed(
                String servicePackageName, String appPackageName, Context context) {
            return PrivacyUtils.isOutputDataAllowed(servicePackageName, appPackageName, context);
        }
    }

    @NonNull
    private final Injector mInjector;

    public AppRequestFlow(
            @NonNull String callingPackageName,
            @NonNull ComponentName service,
            @NonNull PersistableBundle params,
            @NonNull IExecuteCallback callback,
            @NonNull Context context,
            long startTimeMillis) {
        this(callingPackageName, service, params,
                callback, context, startTimeMillis,
                new Injector());
    }

    @VisibleForTesting
    AppRequestFlow(
            @NonNull String callingPackageName,
            @NonNull ComponentName service,
            @NonNull PersistableBundle params,
            @NonNull IExecuteCallback callback,
            @NonNull Context context,
            long startTimeMillis,
            @NonNull Injector injector) {
        sLogger.d(TAG + ": AppRequestFlow created.");
        mCallingPackageName = Objects.requireNonNull(callingPackageName);
        mService = Objects.requireNonNull(service);
        mParams = Objects.requireNonNull(params);
        mCallback = Objects.requireNonNull(callback);
        mContext = Objects.requireNonNull(context);
        mStartTimeMillis = startTimeMillis;
        mInjector = Objects.requireNonNull(injector);
    }

    /** Runs the request processing flow. */
    public void run() {
        var unused = Futures.submit(this::processRequest, mInjector.getExecutor());
    }

    private void processRequest() {
        try {
            checkPersonalizationStatus();
            checkAppManifestConfig();

            if (mErrorCode != Constants.STATUS_SUCCESS) {
                sendErrorResult(mErrorCode);
                return;
            }

            ListenableFuture<IsolatedServiceInfo> loadFuture =
                    mInjector.getProcessRunner().loadIsolatedService(
                        TASK_NAME, mService);
            ListenableFuture<ExecuteOutputParcel> executeResultFuture =
                    FluentFuture.from(loadFuture)
                    .transformAsync(
                            result -> executeAppRequest(result),
                            mInjector.getExecutor()
                    )
                    .transform(
                            result -> {
                                return result.getParcelable(
                                        Constants.EXTRA_RESULT, ExecuteOutputParcel.class);
                            },
                            mInjector.getExecutor()
                    );

            ListenableFuture<Long> queryIdFuture = FluentFuture.from(executeResultFuture)
                    .transformAsync(input -> logQuery(input), mInjector.getExecutor());

            ListenableFuture<Bundle> outputResultFuture =
                    FluentFuture.from(
                            Futures.whenAllSucceed(executeResultFuture, queryIdFuture)
                                .callAsync(
                                        () -> createResultBundle(
                                                executeResultFuture, queryIdFuture),
                                        mInjector.getExecutor()))
                            .withTimeout(
                                mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                                TimeUnit.SECONDS,
                                mInjector.getScheduledExecutor()
                            );

            Futures.addCallback(
                    outputResultFuture,
                    new FutureCallback<Bundle>() {
                          @Override
                          public void onSuccess(Bundle bundle) {
                              sendSuccessResult(bundle);
                          }

                          @Override
                          public void onFailure(Throwable t) {
                              sLogger.w(TAG + ": Request failed.", t);
                              sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                          }
                    },
                    mInjector.getExecutor());

            var unused =
                    Futures.whenAllComplete(loadFuture, outputResultFuture)
                            .callAsync(
                                    () -> {
                                        unBindFromModelService();
                                        return mInjector
                                                .getProcessRunner()
                                                .unloadIsolatedService(loadFuture.get());
                                    },
                                    mInjector.getExecutor());
        } catch (Exception e) {
            sLogger.e(TAG + ": Could not process request.", e);
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
        }
    }

    private void checkPersonalizationStatus() {
        if (!mInjector.isPersonalizationStatusEnabled()) {
            sLogger.d(TAG + ": Personalization is disabled.");
            mErrorCode = Constants.STATUS_PERSONALIZATION_DISABLED;
        }
    }

    private void checkAppManifestConfig() {
        AppManifestConfig config = null;
        try {
            config = Objects.requireNonNull(
                    AppManifestConfigHelper.getAppManifestConfig(
                            mContext, mService.getPackageName()));
        } catch (Exception e) {
            sLogger.d(TAG + ": Failed to read manifest.", e);
            mErrorCode = Constants.STATUS_NAME_NOT_FOUND;
            return;
        }

        if (!mService.getClassName().equals(config.getServiceName())) {
            sLogger.d(TAG + "service class not found");
            mErrorCode = Constants.STATUS_CLASS_NOT_FOUND;
        }
    }

    private Bundle getServiceParams() {
        Bundle serviceParams = new Bundle();

        serviceParams.putParcelable(
                Constants.EXTRA_INPUT,
                new ExecuteInputParcel.Builder()
                        .setAppPackageName(mCallingPackageName)
                        .setAppParams(mParams)
                        .build());
        serviceParams.putBinder(
                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER,
                new DataAccessServiceImpl(
                        mService.getPackageName(),
                        mContext,
                        /* includeLocalData */ true,
                        /* includeEventData */ true));
        serviceParams.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new FederatedComputeServiceImpl(mService, mContext));
        serviceParams.putParcelable(
                Constants.EXTRA_USER_DATA,
                new UserDataAccessor().getUserData());
        serviceParams.putBinder(
                Constants.EXTRA_MODEL_SERVICE_BINDER,
                getModelService().asBinder());

        return serviceParams;
    }

    private ListenableFuture<Bundle> executeAppRequest(
            IsolatedServiceInfo isolatedServiceInfo) {
        sLogger.d(TAG + ": executeAppRequest() started.");

        Bundle serviceParams = getServiceParams();
        ListenableFuture<Bundle> result = mInjector.getProcessRunner().runIsolatedService(
                isolatedServiceInfo, Constants.OP_EXECUTE, serviceParams);
        return FluentFuture.from(result)
                .transform(
                    val -> {
                        writeServiceRequestMetrics(
                                val, isolatedServiceInfo.getStartTimeMillis(),
                                Constants.STATUS_SUCCESS);
                        return val;
                    },
                    mInjector.getExecutor()
                )
                .catchingAsync(
                    Exception.class,
                    e -> {
                        writeServiceRequestMetrics(
                                null, isolatedServiceInfo.getStartTimeMillis(),
                                Constants.STATUS_INTERNAL_ERROR);
                        return Futures.immediateFailedFuture(e);
                    },
                    mInjector.getExecutor()
                );
    }

    private ListenableFuture<Long> logQuery(ExecuteOutputParcel result) {
        sLogger.d(TAG + ": logQuery() started.");
        return LogUtils.writeLogRecords(
                mContext,
                mService.getPackageName(),
                result.getRequestLogRecord(),
                result.getEventLogRecords());
    }

    private IIsolatedModelService getModelService() {
        // TODO(b/323304647): bind to shared isolated process.
        mModelService =
                AbstractServiceBinder.getServiceBinderByServiceName(
                        mContext,
                        ISOLATED_MODEL_SERVICE_NAME,
                        mContext.getPackageName(),
                        IIsolatedModelService.Stub::asInterface);
        return mModelService.getService(Runnable::run);
    }

    private void unBindFromModelService() {
        mModelService.unbindFromService();
    }

    private ListenableFuture<Bundle> createResultBundle(
            ListenableFuture<ExecuteOutputParcel> resultFuture,
            ListenableFuture<Long> queryIdFuture) {
        try {
            sLogger.d(TAG + ": createResultBundle() started.");
            ExecuteOutputParcel result = Futures.getDone(resultFuture);
            long queryId = Futures.getDone(queryIdFuture);
            RenderingConfig renderingConfig = result.getRenderingConfig();

            String token;
            if (renderingConfig == null) {
                token = null;
            } else {
                SlotWrapper wrapper = new SlotWrapper(
                        result.getRequestLogRecord(), renderingConfig,
                        mService.getPackageName(), queryId);
                token = CryptUtils.encrypt(wrapper);
            }
            Bundle bundle = new Bundle();
            bundle.putString(Constants.EXTRA_SURFACE_PACKAGE_TOKEN_STRING, token);
            if (mInjector.isOutputDataAllowed(
                    mService.getPackageName(), mCallingPackageName, mContext)) {
                bundle.putByteArray(Constants.EXTRA_OUTPUT_DATA, result.getOutputData());
            }
            return Futures.immediateFuture(bundle);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private void sendSuccessResult(Bundle result) {
        int responseCode = Constants.STATUS_SUCCESS;
        try {
            mCallback.onSuccess(result);
        } catch (RemoteException e) {
            responseCode = Constants.STATUS_INTERNAL_ERROR;
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            writeAppRequestMetrics(responseCode);
        }
    }

    private void sendErrorResult(int errorCode) {
        try {
            mCallback.onError(errorCode);
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            writeAppRequestMetrics(errorCode);
        }
    }

    private void writeAppRequestMetrics(int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - mStartTimeMillis);
        ApiCallStats callStats = new ApiCallStats.Builder(ApiCallStats.API_EXECUTE)
                .setLatencyMillis(latencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }

    private void writeServiceRequestMetrics(Bundle result, long startTimeMillis, int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - startTimeMillis);
        int overheadLatencyMillis =
                (int) StatsUtils.getOverheadLatencyMillis(latencyMillis, result);
        ApiCallStats callStats = new ApiCallStats.Builder(ApiCallStats.API_SERVICE_ON_EXECUTE)
                .setLatencyMillis(latencyMillis)
                .setOverheadLatencyMillis(overheadLatencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }
}


