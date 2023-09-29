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
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Implementation of ExampleStoreService for OnDevicePersonalization
 */
public class OdpExampleStoreService extends ExampleStoreService {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OdpExampleStoreService";
    private static final String TASK_NAME = "ExampleStore";
    private final Context mContext = this;

    /**
     * Generates a unique task identifier from the given strings
     */
    public static String getTaskIdentifier(String collectionName, String populationName,
            String taskName) {
        return collectionName + "_" + populationName + "_" + taskName;
    }

    @Override
    public void startQuery(@NonNull Bundle params, @NonNull QueryCallback callback) {
        try {
            ContextData contextData = ContextData.fromByteArray(Objects.requireNonNull(
                    params.getByteArray(ClientConstants.EXTRA_CONTEXT_DATA)));
            String packageName = contextData.getPackageName();
            String collectionName = Objects.requireNonNull(
                    params.getString(ClientConstants.EXTRA_COLLECTION_NAME));
            String populationName = Objects.requireNonNull(
                    params.getString(ClientConstants.EXTRA_POPULATION_NAME));
            String taskName = Objects.requireNonNull(
                    params.getString(ClientConstants.EXTRA_TASK_NAME));

            EventsDao eventDao = EventsDao.getInstance(mContext);
            // Cancel job if on longer valid. This is written to the table during scheduling
            // via {@link FederatedComputeServiceImpl} and deleted either during cancel or
            // during maintenance for uninstalled packages.
            EventState eventStatePopulation = eventDao.getEventState(populationName, packageName);
            if (eventStatePopulation == null) {
                sLogger.w("Job was either cancelled or package was uninstalled");
                // Cancel job.
                FederatedComputeManager FCManager =
                        mContext.getSystemService(FederatedComputeManager.class);
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
            EventState eventState = eventDao.getEventState(
                    getTaskIdentifier(collectionName, populationName, taskName), packageName);
            byte[] resumptionToken = null;
            if (eventState != null) {
                resumptionToken = eventState.getToken();
            }

            TrainingExampleInput input = new TrainingExampleInput.Builder()
                    .setResumptionToken(resumptionToken)
                    .setCollectionName(collectionName)
                    .setPopulationName(populationName)
                    .setTaskName(taskName)
                    .build();


            ListenableFuture<TrainingExampleOutputParcel> resultFuture = FluentFuture.from(
                            ProcessUtils.loadIsolatedService(
                                    TASK_NAME, packageName, mContext))
                    .transformAsync(
                            result -> executeOnTrainingExample(result, input, packageName),
                            OnDevicePersonalizationExecutors.getBackgroundExecutor()
                    )
                    .transform(
                            result -> {
                                return result.getParcelable(
                                        Constants.EXTRA_RESULT, TrainingExampleOutputParcel.class);
                            },
                            OnDevicePersonalizationExecutors.getBackgroundExecutor()
                    );

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
                                        OdpExampleStoreIteratorFactory.getInstance().createIterator(
                                                new ArrayList<>(), new ArrayList<>()
                                        )
                                );
                            } else {
                                callback.onStartQuerySuccess(
                                        OdpExampleStoreIteratorFactory.getInstance().createIterator(
                                                trainingExamplesListSlice.getList(),
                                                resumptionTokensListSlice.getList()
                                        )
                                );
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            sLogger.w(TAG + ": Request failed.", t);
                            callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
                        }
                    },
                    OnDevicePersonalizationExecutors.getBackgroundExecutor());

        } catch (Exception e) {
            sLogger.w(TAG + ": Start query failed.", e);
            callback.onStartQueryFailure(ClientConstants.STATUS_INTERNAL_ERROR);
        }
    }

    private ListenableFuture<Bundle> executeOnTrainingExample(
            IsolatedServiceInfo isolatedServiceInfo, TrainingExampleInput exampleInput,
            String packageName) {
        sLogger.d(TAG + ": executeOnTrainingExample() started.");
        Bundle serviceParams = new Bundle();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, exampleInput);
        DataAccessServiceImpl binder = new DataAccessServiceImpl(
                packageName, mContext, /* includeLocalData */ true,
                /* includeEventData */ true);
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        UserDataAccessor userDataAccessor = new UserDataAccessor();
        UserData userData = userDataAccessor.getUserData();
        serviceParams.putParcelable(Constants.EXTRA_USER_DATA, userData);
        return ProcessUtils.runIsolatedService(
                isolatedServiceInfo,
                AppManifestConfigHelper.getServiceNameFromOdpSettings(mContext, packageName),
                Constants.OP_TRAINING_EXAMPLE, serviceParams);
    }
}
