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

package com.android.ondevicepersonalization.services.federatedcompute;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.TrainingExampleRecord;
import android.adservices.ondevicepersonalization.TrainingExamplesInputParcel;
import android.adservices.ondevicepersonalization.TrainingExamplesOutputParcel;
import android.adservices.ondevicepersonalization.UserData;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.ExampleStoreService;
import android.federatedcompute.FederatedComputeManager;
import android.federatedcompute.common.ClientConstants;
import android.os.Bundle;
import android.os.OutcomeReceiver;

import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.OdpParceledListSlice;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessPermission;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.PluginProcessRunner;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner;
import com.android.ondevicepersonalization.services.util.AllowListUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Implementation of ExampleStoreService for OnDevicePersonalization */
public final class OdpExampleStoreService extends ExampleStoreService {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = OdpExampleStoreService.class.getSimpleName();
    private static final String TASK_NAME = "ExampleStore";

    static class Injector {
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
            return FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled()
                    ? SharedIsolatedProcessRunner.getInstance()
                    : PluginProcessRunner.getInstance();
        }
    }

    private final Injector mInjector = new Injector();

    /** Generates a unique task identifier from the given strings */
    public static String getTaskIdentifier(String populationName, String taskId) {
        return populationName + "_" + taskId;
    }

    /** Generates a unique task identifier from the given strings */
    public static String getTaskIdentifier(
            String populationName, String taskId, String collectionUri) {
        return populationName + "_" + taskId + "_" + collectionUri;
    }

    private static boolean isCollectionUriPresent(String collectionUri) {
        return collectionUri != null && !collectionUri.isEmpty();
    }

    @Override
    public void startQuery(@NonNull Bundle params, @NonNull QueryCallback callback) {
        try {
            long startTime = mInjector.getClock().currentTimeMillis();
            ContextData contextData =
                    ContextData.fromByteArray(
                            Objects.requireNonNull(
                                    params.getByteArray(ClientConstants.EXTRA_CONTEXT_DATA)));
            String packageName = contextData.getPackageName();
            String ownerClassName = contextData.getClassName();
            String populationName =
                    Objects.requireNonNull(params.getString(ClientConstants.EXTRA_POPULATION_NAME));
            String taskId = Objects.requireNonNull(params.getString(ClientConstants.EXTRA_TASK_ID));
            String collectionUri = params.getString(ClientConstants.EXTRA_COLLECTION_URI);
            int eligibilityMinExample =
                    params.getInt(ClientConstants.EXTRA_ELIGIBILITY_MIN_EXAMPLE);

            EventsDao eventDao = EventsDao.getInstance(getContext());

            boolean privacyStatusEligible = true;

            if (!UserPrivacyStatus.getInstance().isMeasurementEnabled()) {
                privacyStatusEligible = false;
                sLogger.w(TAG + ": Measurement control is not given.");
                StatsUtils.writeServiceRequestMetrics(
                        Constants.API_NAME_SERVICE_ON_TRAINING_EXAMPLE,
                        packageName,
                        null,
                        mInjector.getClock(),
                        Constants.STATUS_PERSONALIZATION_DISABLED,
                        startTime);
            }

            // Cancel job if on longer valid. This is written to the table during scheduling
            // via {@link FederatedComputeServiceImpl} and deleted either during cancel or
            // during maintenance for uninstalled packages.
            ComponentName owner = ComponentName.createRelative(packageName, ownerClassName);
            EventState eventStatePopulation = eventDao.getEventState(populationName, owner);
            if (eventStatePopulation == null) {
                StatsUtils.writeServiceRequestMetrics(
                        Constants.API_NAME_SERVICE_ON_TRAINING_EXAMPLE,
                        packageName,
                        null,
                        mInjector.getClock(),
                        Constants.STATUS_KEY_NOT_FOUND,
                        startTime);
            }
            if (!privacyStatusEligible || eventStatePopulation == null) {
                sLogger.w("Job was either cancelled or package was uninstalled");
                // Cancel job.
                FederatedComputeManager FCManager =
                        getContext().getSystemService(FederatedComputeManager.class);
                if (FCManager == null) {
                    sLogger.e(TAG + ": Failed to get FederatedCompute Service");
                    callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
                    return;
                }
                FCManager.cancel(
                        owner,
                        populationName,
                        OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                        new OutcomeReceiver<Object, Exception>() {
                            @Override
                            public void onResult(Object result) {
                                sLogger.d(TAG + ": Successfully canceled job");
                                callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
                            }

                            @Override
                            public void onError(Exception error) {
                                sLogger.e(TAG + ": Error while cancelling job", error);
                                OutcomeReceiver.super.onError(error);
                                callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
                            }
                        });
                return;
            }

            // Get resumptionToken
            EventState eventState =
                    eventDao.getEventState(
                            isCollectionUriPresent(collectionUri)
                                    ? getTaskIdentifier(populationName, taskId, collectionUri)
                                    : getTaskIdentifier(populationName, taskId),
                            owner);
            byte[] resumptionToken = null;
            if (eventState != null) {
                resumptionToken = eventState.getToken();
            }

            TrainingExamplesInputParcel.Builder input =
                    new TrainingExamplesInputParcel.Builder()
                            .setResumptionToken(resumptionToken)
                            .setPopulationName(populationName)
                            .setTaskName(taskId);
            if (isCollectionUriPresent(collectionUri)) {
                input.setCollectionName(collectionUri);
            }

            String className =
                    AppManifestConfigHelper.getServiceNameFromOdpSettings(
                            getContext(), packageName);
            ListenableFuture<IsolatedServiceInfo> loadFuture =
                    mInjector
                            .getProcessRunner()
                            .loadIsolatedService(
                                    TASK_NAME,
                                    ComponentName.createRelative(packageName, className));
            ListenableFuture<Bundle> resultFuture =
                    FluentFuture.from(loadFuture)
                            .transformAsync(
                                    result ->
                                            executeOnTrainingExamples(
                                                    result, input.build(), packageName),
                                    OnDevicePersonalizationExecutors.getBackgroundExecutor())
                            .withTimeout(
                                    mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                                    TimeUnit.SECONDS,
                                    mInjector.getScheduledExecutor());

            Futures.addCallback(
                    resultFuture,
                    new FutureCallback<Bundle>() {
                        @Override
                        public void onSuccess(Bundle result) {
                            int status = Constants.STATUS_SUCCESS;
                            try {
                                TrainingExamplesOutputParcel trainingExamplesOutputParcel =
                                        result.getParcelable(
                                                Constants.EXTRA_RESULT,
                                                TrainingExamplesOutputParcel.class);
                                if (trainingExamplesOutputParcel == null) {
                                    status = Constants.STATUS_NAME_NOT_FOUND;
                                    callback.onStartQueryFailure(
                                            ClientConstants.STATUS_INTERNAL_ERROR);
                                    return;
                                }
                                OdpParceledListSlice<TrainingExampleRecord>
                                        trainingExampleRecordList =
                                                trainingExamplesOutputParcel
                                                        .getTrainingExampleRecords();
                                if (trainingExampleRecordList == null
                                        || trainingExampleRecordList.getList().isEmpty()
                                        || trainingExampleRecordList.getList().size()
                                                < eligibilityMinExample) {
                                    status = Constants.STATUS_SUCCESS_EMPTY_RESULT;
                                    callback.onStartQueryFailure(
                                            ClientConstants.STATUS_NOT_ENOUGH_DATA);
                                } else {
                                    callback.onStartQuerySuccess(
                                            OdpExampleStoreIteratorFactory.getInstance()
                                                    .createIterator(
                                                            trainingExampleRecordList.getList()));
                                }
                            } finally {
                                StatsUtils.writeServiceRequestMetrics(
                                        Constants.API_NAME_SERVICE_ON_TRAINING_EXAMPLE,
                                        packageName,
                                        result,
                                        mInjector.getClock(),
                                        status,
                                        startTime);
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            int status = Constants.STATUS_INTERNAL_ERROR;
                            if (t instanceof TimeoutException) {
                                status = Constants.STATUS_TIMEOUT;
                            } else if (t instanceof OdpServiceException exp) {
                                if (exp.getCause() instanceof IsolatedServiceException
                                        && isLogIsolatedServiceErrorCodeNonAggregatedAllowed(
                                                packageName)) {
                                    status = ((IsolatedServiceException) exp.getCause())
                                            .getErrorCode();
                                } else {
                                    status = exp.getErrorCode();
                                }
                            }
                            sLogger.w(t, "%s : Request failed.", TAG);
                            StatsUtils.writeServiceRequestMetrics(
                                    Constants.API_NAME_SERVICE_ON_TRAINING_EXAMPLE,
                                    packageName,
                                    null,
                                    mInjector.getClock(),
                                    status,
                                    startTime);
                            callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
                        }
                    },
                    OnDevicePersonalizationExecutors.getBackgroundExecutor());

            var unused =
                    Futures.whenAllComplete(loadFuture, resultFuture)
                            .callAsync(
                                    () ->
                                            mInjector
                                                    .getProcessRunner()
                                                    .unloadIsolatedService(loadFuture.get()),
                                    OnDevicePersonalizationExecutors.getBackgroundExecutor());
        } catch (Exception e) {
            sLogger.w(e, "%s : Start query failed.", TAG);
            StatsUtils.writeServiceRequestMetrics(
                    Constants.API_NAME_SERVICE_ON_TRAINING_EXAMPLE,
                    Constants.STATUS_INTERNAL_ERROR);
            callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
        }
    }

    private ListenableFuture<Bundle> executeOnTrainingExamples(
            IsolatedServiceInfo isolatedServiceInfo,
            TrainingExamplesInputParcel exampleInput,
            String packageName) {
        sLogger.d(TAG + ": executeOnTrainingExamples() started.");
        Bundle serviceParams = new Bundle();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, exampleInput);
        String serviceClass =
                AppManifestConfigHelper.getServiceNameFromOdpSettings(getContext(), packageName);
        DataAccessServiceImpl binder =
                new DataAccessServiceImpl(
                        ComponentName.createRelative(packageName, serviceClass),
                        getContext(),
                        // ODP provides accurate user signal in training flow, so we disable write
                        // access of databases to prevent leak.
                        /* localDataPermission */ DataAccessPermission.READ_ONLY,
                        /* eventDataPermission */ DataAccessPermission.READ_ONLY);
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        UserDataAccessor userDataAccessor = new UserDataAccessor();
        UserData userData;
        // By default, we don't provide platform data for federated learning flow.
        if (isPlatformDataProvided(packageName)) {
            userData = userDataAccessor.getUserDataWithAppInstall();
        } else {
            userData = userDataAccessor.getUserData();
        }
        serviceParams.putParcelable(Constants.EXTRA_USER_DATA, userData);
        return mInjector
                .getProcessRunner()
                .runIsolatedService(
                        isolatedServiceInfo, Constants.OP_TRAINING_EXAMPLE, serviceParams);
    }

    // used for tests to provide mock/real implementation of context.
    private Context getContext() {
        return this.getApplicationContext();
    }

    private boolean isPlatformDataProvided(String packageName) {
        try {
            return AllowListUtils.isAllowListed(
                    packageName,
                    PackageUtils.getCertDigest(getContext(), packageName),
                    mInjector.getFlags().getDefaultPlatformDataForExecuteAllowlist());
        } catch (Exception e) {
            sLogger.d(TAG + ": allow list error", e);
            return false;
        }
    }

    private boolean isLogIsolatedServiceErrorCodeNonAggregatedAllowed(String packageName) {
        try {
            return AllowListUtils.isAllowListed(
                    packageName,
                    null,
                    mInjector.getFlags().getLogIsolatedServiceErrorCodeNonAggregatedAllowlist());
        } catch (Exception e) {
            sLogger.d(e, TAG + ": check isLogIsolatedServiceErrorCodeNonAggregatedAllowed error");
            return false;
        }
    }
}
