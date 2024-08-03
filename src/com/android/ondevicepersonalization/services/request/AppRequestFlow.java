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

import android.adservices.ondevicepersonalization.CalleeMetadata;
import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.ExecuteInIsolatedServiceRequest;
import android.adservices.ondevicepersonalization.ExecuteInputParcel;
import android.adservices.ondevicepersonalization.ExecuteOptionsParcel;
import android.adservices.ondevicepersonalization.ExecuteOutputParcel;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.DeviceConfig;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessPermission;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.federatedcompute.FederatedComputeServiceImpl;
import com.android.ondevicepersonalization.services.inference.IsolatedModelServiceProvider;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfig;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlow;
import com.android.ondevicepersonalization.services.util.AllowListUtils;
import com.android.ondevicepersonalization.services.util.CryptUtils;
import com.android.ondevicepersonalization.services.util.DebugUtils;
import com.android.ondevicepersonalization.services.util.LogUtils;
import com.android.ondevicepersonalization.services.util.NoiseUtil;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles a surface package request from an app or SDK.
 */
public class AppRequestFlow implements ServiceFlow<Bundle> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AppRequestFlow.class.getSimpleName();
    @NonNull
    private final String mCallingPackageName;
    @NonNull
    private final ComponentName mService;
    @NonNull private final Bundle mWrappedParams;
    @NonNull
    private final IExecuteCallback mCallback;
    @NonNull
    private final Context mContext;
    private final long mStartTimeMillis;
    private final long mServiceEntryTimeMillis;
    private final ExecuteOptionsParcel mOptions;

    @NonNull
    private AtomicReference<IsolatedModelServiceProvider> mModelServiceProvider =
            new AtomicReference<>(null);

    private AtomicLong mStartServiceTimeMillis = new AtomicLong();
    private byte[] mSerializedAppParams;

    @VisibleForTesting
    public static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        public Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        ListeningScheduledExecutorService getScheduledExecutor() {
            return OnDevicePersonalizationExecutors.getScheduledExecutor();
        }

        /** Returns whether should validate rendering configuration keys. */
        public boolean shouldValidateExecuteOutput() {
            return DeviceConfig.getBoolean(
                    /* namespace= */ "on_device_personalization",
                    /* name= */ "debug.validate_rendering_config_keys",
                    /* defaultValue= */ true);
        }

        public NoiseUtil getNoiseUtil() {
            return new NoiseUtil();
        }
    }

    @NonNull
    private final Injector mInjector;

    public AppRequestFlow(
            @NonNull String callingPackageName,
            @NonNull ComponentName service,
            @NonNull Bundle wrappedParams,
            @NonNull IExecuteCallback callback,
            @NonNull Context context,
            long startTimeMillis,
            long serviceEntryTimeMillis,
            ExecuteOptionsParcel options) {
        this(
                callingPackageName,
                service,
                wrappedParams,
                callback,
                context,
                startTimeMillis,
                serviceEntryTimeMillis,
                options,
                new Injector());
    }

    @VisibleForTesting
    public AppRequestFlow(
            @NonNull String callingPackageName,
            @NonNull ComponentName service,
            @NonNull Bundle wrappedParams,
            @NonNull IExecuteCallback callback,
            @NonNull Context context,
            long startTimeMillis,
            long serviceEntryTimeMillis,
            ExecuteOptionsParcel options,
            @NonNull Injector injector) {
        sLogger.d(TAG + ": AppRequestFlow created.");
        mCallingPackageName = Objects.requireNonNull(callingPackageName);
        mService = Objects.requireNonNull(service);
        mWrappedParams = Objects.requireNonNull(wrappedParams);
        mCallback = Objects.requireNonNull(callback);
        mContext = Objects.requireNonNull(context);
        mStartTimeMillis = startTimeMillis;
        mServiceEntryTimeMillis =  serviceEntryTimeMillis;
        mOptions = options;
        mInjector = Objects.requireNonNull(injector);
    }

    @Override
    public boolean isServiceFlowReady() {
        mStartServiceTimeMillis.set(mInjector.getClock().elapsedRealtime());

        try {
            ByteArrayParceledSlice paramsBuffer =
                    Objects.requireNonNull(
                            mWrappedParams.getParcelable(
                                    Constants.EXTRA_APP_PARAMS_SERIALIZED,
                                    ByteArrayParceledSlice.class));
            mSerializedAppParams = Objects.requireNonNull(paramsBuffer.getByteArray());
        } catch (Exception e) {
            sLogger.d(TAG + ": Failed to extract app params.", e);
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR, 0, e);
            return false;
        }

        AppManifestConfig config = null;
        try {
            config = Objects.requireNonNull(
                    AppManifestConfigHelper.getAppManifestConfig(
                            mContext, mService.getPackageName()));
        } catch (Exception e) {
            sLogger.d(TAG + ": Failed to read manifest.", e);
            sendErrorResult(
                    Constants.STATUS_MANIFEST_PARSING_FAILED, /* isolatedServiceErrorCode= */ 0, e);
            return false;
        }

        if (!mService.getClassName().equals(config.getServiceName())) {
            sLogger.d(TAG + ": service class not found");
            sendErrorResult(
                    Constants.STATUS_MANIFEST_MISCONFIGURED,
                    /* isolatedServiceErrorCode= */ 0,
                    new ClassNotFoundException(
                            "Expected: "
                                    + mService.getClassName()
                                    + " Found: "
                                    + config.getServiceName()));
            return false;
        }

        return true;
    }

    @Override
    public ComponentName getService() {
        return mService;
    }

    @Override
    public Bundle getServiceParams() {
        sLogger.d(TAG + ": getting service params.");

        DataAccessPermission localDataPermission = DataAccessPermission.READ_WRITE;
        if (!UserPrivacyStatus.getInstance().isMeasurementEnabled()) {
            localDataPermission = DataAccessPermission.READ_ONLY;
        }
        Bundle serviceParams = new Bundle();
        serviceParams.putParcelable(
                Constants.EXTRA_INPUT,
                new ExecuteInputParcel.Builder()
                        .setAppPackageName(mCallingPackageName)
                        .setSerializedAppParams(new ByteArrayParceledSlice(mSerializedAppParams))
                        .build());
        serviceParams.putBinder(
                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER,
                new DataAccessServiceImpl(
                        mService,
                        mContext,
                        /* localDataPermission */ localDataPermission,
                        /* eventDataPermission */ DataAccessPermission.READ_ONLY));
        serviceParams.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new FederatedComputeServiceImpl(mService, mContext));
        serviceParams.putParcelable(
                Constants.EXTRA_USER_DATA, new UserDataAccessor().getUserData());
        mModelServiceProvider.set(new IsolatedModelServiceProvider());
        IIsolatedModelService modelService = mModelServiceProvider.get().getModelService(mContext);
        serviceParams.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, modelService.asBinder());

        return serviceParams;
    }

    @Override
    public void uploadServiceFlowMetrics(ListenableFuture<Bundle> runServiceFuture) {
        sLogger.d(TAG + ": uploading service flow metrics.");
        var unused =
                FluentFuture.from(runServiceFuture)
                        .transform(
                                val -> {
                                    StatsUtils.writeServiceRequestMetrics(
                                            Constants.API_NAME_SERVICE_ON_EXECUTE,
                                            val,
                                            mInjector.getClock(),
                                            Constants.STATUS_SUCCESS,
                                            mStartServiceTimeMillis.get());
                                    return val;
                                },
                                mInjector.getExecutor())
                        .catchingAsync(
                                Exception.class,
                                e -> {
                                    StatsUtils.writeServiceRequestMetrics(
                                            Constants.API_NAME_SERVICE_ON_EXECUTE,
                                            /* result= */ null,
                                            mInjector.getClock(),
                                            Constants.STATUS_INTERNAL_ERROR,
                                            mStartServiceTimeMillis.get());
                                    return Futures.immediateFailedFuture(e);
                                },
                                mInjector.getExecutor());
    }

    @Override
    public ListenableFuture<Bundle> getServiceFlowResultFuture(
            ListenableFuture<Bundle> runServiceFuture) {
        ListenableFuture<ExecuteOutputParcel> executeResultFuture =
                FluentFuture.from(runServiceFuture)
                        .transform(
                                result -> result.getParcelable(
                                        Constants.EXTRA_RESULT, ExecuteOutputParcel.class),
                                mInjector.getExecutor()
                        );

        ListenableFuture<Long> queryIdFuture = FluentFuture.from(executeResultFuture)
                .transformAsync(this::validateExecuteOutput, mInjector.getExecutor())
                .transformAsync(this::logQuery, mInjector.getExecutor());

        return FluentFuture.from(
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
    }

    @Override
    public void returnResultThroughCallback(ListenableFuture<Bundle> serviceFlowResultFuture) {
        Futures.addCallback(
                serviceFlowResultFuture,
                new FutureCallback<Bundle>() {
                    @Override
                    public void onSuccess(Bundle bundle) {
                        sendSuccessResult(bundle);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.w(TAG + ": Request failed.", t);
                        if (t instanceof OdpServiceException) {
                            OdpServiceException e = (OdpServiceException) t;

                            sendErrorResult(
                                    e.getErrorCode(),
                                    DebugUtils.getIsolatedServiceExceptionCode(
                                            mContext, mService, e),
                                    t);
                        } else {
                            int errorCode =
                                    t instanceof TimeoutException
                                            ? Constants.STATUS_ISOLATED_SERVICE_TIMEOUT
                                            : Constants.STATUS_INTERNAL_ERROR;
                            sLogger.w(TAG + ": Failing with error code: " + errorCode);
                            sendErrorResult(errorCode, /* isolatedServiceErrorCode= */ 0, t);
                        }
                    }
                },
                mInjector.getExecutor());
    }

    @Override
    public void cleanUpServiceParams() {
        mModelServiceProvider.get().unBindFromModelService();
    }

    private ListenableFuture<ExecuteOutputParcel> validateExecuteOutput(
            ExecuteOutputParcel result) {
        sLogger.d(TAG + ": validateExecuteOutput() started.");
        if (!mInjector.shouldValidateExecuteOutput()) {
            sLogger.d(TAG + ": validateExecuteOutput() skipped.");
            return Futures.immediateFuture(result);
        }
        try {
                OnDevicePersonalizationVendorDataDao vendorDataDao =
                        OnDevicePersonalizationVendorDataDao.getInstance(mContext,
                                mService,
                                PackageUtils.getCertDigest(mContext, mService.getPackageName()));
                if (result.getRenderingConfig() != null) {
                    Set<String> keyset = vendorDataDao.readAllVendorDataKeys();
                    if (!keyset.containsAll(result.getRenderingConfig().getKeys())) {
                    return Futures.immediateFailedFuture(
                            new OdpServiceException(Constants.STATUS_SERVICE_FAILED));
                    }
                }
            } catch (Exception e) {
            return Futures.immediateFailedFuture(
                    new OdpServiceException(Constants.STATUS_SERVICE_FAILED));
            }
        sLogger.d(TAG + ": validateExecuteOutput() succeeded.");
        return Futures.immediateFuture(result);
    }

    private ListenableFuture<Long> logQuery(ExecuteOutputParcel result) {
        sLogger.d(TAG + ": logQuery() started.");
        if (!UserPrivacyStatus.getInstance().isMeasurementEnabled()) {
            sLogger.d(TAG + ": User control is not given for measurement,"
                            + "dropping request and event entries.");
            return Futures.immediateFuture(-1L);
        }
        return LogUtils.writeLogRecords(
                mContext,
                mCallingPackageName,
                mService,
                result.getRequestLogRecord(),
                result.getEventLogRecords());
    }

    private ListenableFuture<Bundle> createResultBundle(
            ListenableFuture<ExecuteOutputParcel> resultFuture,
            ListenableFuture<Long> queryIdFuture) {
        try {
            sLogger.d(TAG + ": createResultBundle() started.");

            if (!UserPrivacyStatus.getInstance().isProtectedAudienceEnabled()) {
                sLogger.d(TAG + ": user control is not given for targeting.");
                return Futures.immediateFuture(Bundle.EMPTY);
            }

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
            // bundle.getInt(key) returns 0 if the key is not found. It can be confused with the
            // real best value 0, so set it to -1 explicitly to indicate this field is unset.
            int bestValue = -1;
            if (isOutputDataAllowed()
                    && mOptions.getOutputType()
                            == ExecuteInIsolatedServiceRequest.OutputParams
                                    .OUTPUT_TYPE_BEST_VALUE) {
                bestValue =
                        mInjector
                                .getNoiseUtil()
                                .applyNoiseToBestValue(
                                        result.getBestValue(),
                                        mOptions.getMaxIntValue(),
                                        ThreadLocalRandom.current());
            }
            bundle.putInt(Constants.EXTRA_OUTPUT_BEST_VALUE, bestValue);

            return Futures.immediateFuture(bundle);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private boolean isOutputDataAllowed() {
        try {
            return AllowListUtils.isPairAllowListed(
                    mCallingPackageName,
                    PackageUtils.getCertDigest(mContext, mCallingPackageName),
                    mService.getPackageName(),
                    PackageUtils.getCertDigest(mContext, mService.getPackageName()),
                    mInjector.getFlags().getOutputDataAllowList());
        } catch (Exception e) {
            sLogger.d(TAG + ": allow list error", e);
            return false;
        }
    }

    private void sendSuccessResult(Bundle result) {
        try {
            mCallback.onSuccess(
                    result,
                    new CalleeMetadata.Builder()
                            .setServiceEntryTimeMillis(mServiceEntryTimeMillis)
                            .setCallbackInvokeTimeMillis(
                            SystemClock.elapsedRealtime()).build());
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        }
    }

    private void sendErrorResult(int errorCode, int isolatedServiceErrorCode, Throwable t) {
        try {
            mCallback.onError(
                    errorCode,
                    isolatedServiceErrorCode,
                    DebugUtils.serializeExceptionInfo(mService, t),
                    new CalleeMetadata.Builder()
                            .setServiceEntryTimeMillis(mServiceEntryTimeMillis)
                            .setCallbackInvokeTimeMillis(SystemClock.elapsedRealtime()).build());
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        }
    }
}
