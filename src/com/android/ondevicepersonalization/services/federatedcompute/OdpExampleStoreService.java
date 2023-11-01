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
import android.adservices.ondevicepersonalization.TrainingExampleInput;
import android.adservices.ondevicepersonalization.TrainingExampleOutputParcel;
import android.adservices.ondevicepersonalization.UserData;
import android.annotation.NonNull;
import android.content.Context;
import android.federatedcompute.ExampleStoreService;
import android.federatedcompute.FederatedComputeManager;
import android.federatedcompute.common.ClientConstants;
import android.os.Bundle;
import android.os.OutcomeReceiver;

import com.android.ondevicepersonalization.internal.util.ByteArrayParceledListSlice;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
            return ProcessRunner.getInstance();
        }
    }

    private final Injector mInjector = new Injector();

    /** Generates a unique task identifier from the given strings */
    public static String getTaskIdentifier(String populationName, String taskName) {
        return populationName + "_" + taskName;
    }

    @Override
    public void startQuery(@NonNull Bundle params, @NonNull QueryCallback callback) {
        try {
            ContextData contextData =
                    ContextData.fromByteArray(
                            Objects.requireNonNull(
                                    params.getByteArray(ClientConstants.EXTRA_CONTEXT_DATA)));
            String packageName = contextData.getPackageName();
            String populationName =
                    Objects.requireNonNull(params.getString(ClientConstants.EXTRA_POPULATION_NAME));
            String taskName =
                    Objects.requireNonNull(params.getString(ClientConstants.EXTRA_TASK_NAME));

            EventsDao eventDao = EventsDao.getInstance(getContext());

            // Cancel job if on longer valid. This is written to the table during scheduling
            // via {@link FederatedComputeServiceImpl} and deleted either during cancel or
            // during maintenance for uninstalled packages.
            EventState eventStatePopulation = eventDao.getEventState(populationName, packageName);
            if (eventStatePopulation == null) {
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
                            getTaskIdentifier(populationName, taskName), packageName);
            byte[] resumptionToken = null;
            if (eventState != null) {
                resumptionToken = eventState.getToken();
            }

            TrainingExampleInput input =
                    new TrainingExampleInput.Builder()
                            .setResumptionToken(resumptionToken)
                            .setPopulationName(populationName)
                            .setTaskName(taskName)
                            .build();

            ListenableFuture<IsolatedServiceInfo> loadFuture =
                    mInjector.getProcessRunner().loadIsolatedService(TASK_NAME, packageName);
            ListenableFuture<TrainingExampleOutputParcel> resultFuture =
                    FluentFuture.from(loadFuture)
                            .transformAsync(
                                    result -> executeOnTrainingExample(result, input, packageName),
                                    OnDevicePersonalizationExecutors.getBackgroundExecutor())
                            .transform(
                                    result -> {
                                        return result.getParcelable(
                                                Constants.EXTRA_RESULT,
                                                TrainingExampleOutputParcel.class);
                                    },
                                    OnDevicePersonalizationExecutors.getBackgroundExecutor())
                            .withTimeout(
                                    mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                                    TimeUnit.SECONDS,
                                    mInjector.getScheduledExecutor());

            Futures.addCallback(
                    resultFuture,
                    new FutureCallback<TrainingExampleOutputParcel>() {
                        @Override
                        public void onSuccess(
                                TrainingExampleOutputParcel trainingExampleOutputParcel) {
                            ByteArrayParceledListSlice trainingExamplesListSlice =
                                    trainingExampleOutputParcel.getTrainingExamples();
                            ByteArrayParceledListSlice resumptionTokensListSlice =
                                    trainingExampleOutputParcel.getResumptionTokens();
                            if (trainingExamplesListSlice == null
                                    || resumptionTokensListSlice == null) {
                                callback.onStartQuerySuccess(
                                        OdpExampleStoreIteratorFactory.getInstance()
                                                .createIterator(
                                                        new ArrayList<>(), new ArrayList<>()));
                            } else {
                                callback.onStartQuerySuccess(
                                        OdpExampleStoreIteratorFactory.getInstance()
                                                .createIterator(
                                                        trainingExamplesListSlice.getList(),
                                                        resumptionTokensListSlice.getList()));
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            sLogger.w(t, "%s : Request failed.", TAG);
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
            callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
        }
    }

    private ListenableFuture<Bundle> executeOnTrainingExample(
            IsolatedServiceInfo isolatedServiceInfo,
            TrainingExampleInput exampleInput,
            String packageName) {
        sLogger.d(TAG + ": executeOnTrainingExample() started.");
        Bundle serviceParams = new Bundle();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, exampleInput);
        DataAccessServiceImpl binder =
                new DataAccessServiceImpl(
                        packageName,
                        getContext(), /* includeLocalData */
                        true,
                        /* includeEventData */ true);
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        UserDataAccessor userDataAccessor = new UserDataAccessor();
        UserData userData = userDataAccessor.getUserData();
        serviceParams.putParcelable(Constants.EXTRA_USER_DATA, userData);
        ListenableFuture<Bundle> result =
                mInjector
                        .getProcessRunner()
                        .runIsolatedService(
                                isolatedServiceInfo,
                                AppManifestConfigHelper.getServiceNameFromOdpSettings(
                                        getContext(), packageName),
                                Constants.OP_TRAINING_EXAMPLE,
                                serviceParams);
        return FluentFuture.from(result)
                .transform(
                        val -> {
                            writeServiceRequestMetrics(
                                    val,
                                    isolatedServiceInfo.getStartTimeMillis(),
                                    Constants.STATUS_SUCCESS);
                            return val;
                        },
                        OnDevicePersonalizationExecutors.getBackgroundExecutor())
                .catchingAsync(
                        Exception.class,
                        e -> {
                            writeServiceRequestMetrics(
                                    null,
                                    isolatedServiceInfo.getStartTimeMillis(),
                                    Constants.STATUS_INTERNAL_ERROR);
                            return Futures.immediateFailedFuture(e);
                        },
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());
    }

    private void writeServiceRequestMetrics(Bundle result, long startTimeMillis, int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - startTimeMillis);
        int overheadLatencyMillis =
                (int) StatsUtils.getOverheadLatencyMillis(latencyMillis, result);
        ApiCallStats callStats =
                new ApiCallStats.Builder(ApiCallStats.API_SERVICE_ON_TRAINING_EXAMPLE)
                        .setLatencyMillis(latencyMillis)
                        .setOverheadLatencyMillis(overheadLatencyMillis)
                        .setResponseCode(responseCode)
                        .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }

    // used for tests to provide mock/real implementation of context.
    private Context getContext() {
        return this.getApplicationContext();
    }
}
