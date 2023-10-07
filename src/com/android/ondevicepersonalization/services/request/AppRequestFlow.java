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
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.UserData;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfig;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessUtils;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.CryptUtils;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles a surface package request from an app or SDK.
 */
public class AppRequestFlow {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "AppRequestFlow";
    private static final String TASK_NAME = "AppRequest";
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
    @NonNull
    private String mServiceClassName;

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
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
        var unused = Futures.submit(() -> this.processRequest(), mInjector.getExecutor());
    }

    private void processRequest() {
        try {
            AppManifestConfig config = null;
            try {
                config = Objects.requireNonNull(
                        AppManifestConfigHelper.getAppManifestConfig(
                        mContext, mService.getPackageName()));
            } catch (Exception e) {
                sLogger.d(TAG + ": Failed to read manifest.", e);
                sendErrorResult(Constants.STATUS_NAME_NOT_FOUND);
                return;
            }
            if (!mService.getClassName().equals(config.getServiceName())) {
                sLogger.d(TAG + "service class not found");
                sendErrorResult(Constants.STATUS_CLASS_NOT_FOUND);
                return;
            }
            mServiceClassName = Objects.requireNonNull(config.getServiceName());
            ListenableFuture<ExecuteOutput> resultFuture = FluentFuture.from(
                            ProcessUtils.loadIsolatedService(
                                    TASK_NAME, mService.getPackageName(), mContext))
                    .transformAsync(
                            result -> executeAppRequest(result),
                            mInjector.getExecutor()
                    )
                    .transform(
                            result -> {
                                return result.getParcelable(
                                        Constants.EXTRA_RESULT, ExecuteOutput.class);
                            },
                            mInjector.getExecutor()
                    );

            ListenableFuture<Long> queryIdFuture = FluentFuture.from(resultFuture)
                    .transformAsync(input -> logQuery(input), mInjector.getExecutor());

            ListenableFuture<List<String>> slotResultTokensFuture =
                    Futures.whenAllSucceed(resultFuture, queryIdFuture)
                            .callAsync(new AsyncCallable<List<String>>() {
                                @Override
                                public ListenableFuture<List<String>> call() {
                                    return createTokens(resultFuture, queryIdFuture);
                                }
                            }, mInjector.getExecutor());

            Futures.addCallback(
                    slotResultTokensFuture,
                    new FutureCallback<List<String>>() {
                        @Override
                        public void onSuccess(List<String> slotResultTokens) {
                            sendResult(slotResultTokens);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            sLogger.w(TAG + ": Request failed.", t);
                            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                        }
                    },
                    mInjector.getExecutor());
        } catch (Exception e) {
            sLogger.e(TAG + ": Could not process request.", e);
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
        }
    }

    private ListenableFuture<Bundle> executeAppRequest(IsolatedServiceInfo isolatedServiceInfo) {
        sLogger.d(TAG + ": executeAppRequest() started.");
        Bundle serviceParams = new Bundle();
        ExecuteInput input =
                new ExecuteInput.Builder()
                        .setAppPackageName(mCallingPackageName)
                        .setAppParams(mParams)
                        .build();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, input);
        DataAccessServiceImpl binder = new DataAccessServiceImpl(
                mService.getPackageName(), mContext, /* includeLocalData */ true,
                /* includeEventData */ true);
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        UserDataAccessor userDataAccessor = new UserDataAccessor();
        UserData userData = userDataAccessor.getUserData();
        serviceParams.putParcelable(Constants.EXTRA_USER_DATA, userData);
        return ProcessUtils.runIsolatedService(
                isolatedServiceInfo, mServiceClassName, Constants.OP_EXECUTE, serviceParams);
    }

    private ListenableFuture<Long> logQuery(ExecuteOutput result) {
        sLogger.d(TAG + ": logQuery() started.");
        EventsDao eventsDao = EventsDao.getInstance(mContext);
        // Insert query
        List<ContentValues> rows = null;
        if (result.getRequestLogRecord() != null) {
            rows = result.getRequestLogRecord().getRows();
        }
        byte[] queryData = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                mService.getPackageName(), null, rows);
        Query query = new Query.Builder()
                .setServicePackageName(mService.getPackageName())
                .setQueryData(queryData)
                .setTimeMillis(System.currentTimeMillis())
                .build();
        long queryId = eventsDao.insertQuery(query);
        if (queryId == -1) {
            return Futures.immediateFailedFuture(new RuntimeException("Failed to log query."));
        }
        // Insert events
        List<Event> events = new ArrayList<>();
        List<EventLogRecord> eventLogRecords = result.getEventLogRecords();
        for (EventLogRecord eventLogRecord : eventLogRecords) {
            RequestLogRecord requestLogRecord = eventLogRecord.getRequestLogRecord();
            // Verify requestLogRecord exists and has the corresponding rowIndex
            if (requestLogRecord == null || requestLogRecord.getRequestId() == 0
                    || eventLogRecord.getRowIndex() >= requestLogRecord.getRows().size()) {
                continue;
            }
            // Make sure query exists for package in QUERY table
            Query queryRow = eventsDao.readSingleQueryRow(requestLogRecord.getRequestId(),
                    mService.getPackageName());
            if (queryRow == null || eventLogRecord.getRowIndex()
                    >= OnDevicePersonalizationFlatbufferUtils.getContentValuesLengthFromQueryData(
                    queryRow.getQueryData())) {
                continue;
            }
            Event event = new Event.Builder()
                    .setEventData(OnDevicePersonalizationFlatbufferUtils.createEventData(
                            eventLogRecord.getData()))
                    .setQueryId(requestLogRecord.getRequestId())
                    .setRowIndex(eventLogRecord.getRowIndex())
                    .setServicePackageName(mService.getPackageName())
                    .setTimeMillis(System.currentTimeMillis())
                    .setType(eventLogRecord.getType())
                    .build();
            events.add(event);
        }
        if (!eventsDao.insertEvents(events)) {
            return Futures.immediateFailedFuture(new RuntimeException("Failed to log events."));
        }

        return Futures.immediateFuture(queryId);
    }

    private ListenableFuture<List<String>> createTokens(
            ListenableFuture<ExecuteOutput> resultFuture,
            ListenableFuture<Long> queryIdFuture) {
        try {
            sLogger.d(TAG + ": createTokens() started.");
            ExecuteOutput result = Futures.getDone(resultFuture);
            long queryId = Futures.getDone(queryIdFuture);
            List<RenderingConfig> renderingConfigs = result.getRenderingConfigs();
            Objects.requireNonNull(renderingConfigs);

            List<String> tokens = new ArrayList<String>();
            int slotIndex = 0;
            for (RenderingConfig renderingConfig : renderingConfigs) {
                if (renderingConfig == null) {
                    tokens.add(null);
                } else {
                    SlotWrapper wrapper = new SlotWrapper(
                            result.getRequestLogRecord(), slotIndex, renderingConfig,
                            mService.getPackageName(), queryId);
                    tokens.add(CryptUtils.encrypt(wrapper));
                }
                ++slotIndex;
            }

            return Futures.immediateFuture(tokens);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private void sendResult(List<String> slotResultTokens) {
        if (slotResultTokens != null && slotResultTokens.size() > 0) {
            sendSuccessResult(slotResultTokens);
        } else {
            sLogger.w(TAG + ": slotResultTokens is null or empty");
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
        }
    }

    private void sendSuccessResult(List<String> slotResultTokens) {
        int responseCode = Constants.STATUS_SUCCESS;
        try {
            mCallback.onSuccess(slotResultTokens);
        } catch (RemoteException e) {
            responseCode = Constants.STATUS_INTERNAL_ERROR;
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            writeMetrics(responseCode);
        }
    }

    private void sendErrorResult(int errorCode) {
        try {
            mCallback.onError(errorCode);
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            writeMetrics(errorCode);
        }
    }

    private void writeMetrics(int responseCode) {
        int latencyMillis = (int) (mInjector.getClock().elapsedRealtime() - mStartTimeMillis);
        ApiCallStats callStats = new ApiCallStats.Builder(ApiCallStats.API_EXECUTE)
                .setLatencyMillis(latencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }
}
