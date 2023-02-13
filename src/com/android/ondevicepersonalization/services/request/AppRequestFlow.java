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

import android.annotation.NonNull;
import android.content.Context;
import android.ondevicepersonalization.Constants;
import android.ondevicepersonalization.OnDevicePersonalizationManager;
import android.ondevicepersonalization.RenderContentInput;
import android.ondevicepersonalization.RenderContentResult;
import android.ondevicepersonalization.ScoredBid;
import android.ondevicepersonalization.SelectContentInput;
import android.ondevicepersonalization.SelectContentResult;
import android.ondevicepersonalization.SlotInfo;
import android.ondevicepersonalization.SlotResult;
import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.display.DisplayHelper;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessUtils;

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
    private static final String TAG = "AppRequestFlow";
    private static final String TASK_NAME = "AppRequest";
    @NonNull
    private final String mCallingPackageName;
    @NonNull
    private final String mServicePackageName;
    @NonNull
    private final Bundle mParams;
    @NonNull
    private final IRequestSurfacePackageCallback mCallback;
    @NonNull
    private final ListeningExecutorService mExecutorService;
    @NonNull
    private final Context mContext;
    @NonNull
    private final DisplayHelper mDisplayHelper;
    @NonNull
    private final List<SurfaceInfo> mSurfaceInfos;
    @NonNull
    private String mServiceClassName;
    public AppRequestFlow(
            @NonNull String callingPackageName,
            @NonNull String servicePackageName,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull Bundle params,
            @NonNull IRequestSurfacePackageCallback callback,
            @NonNull ListeningExecutorService executorService,
            @NonNull Context context) {
        this(callingPackageName, servicePackageName, hostToken, displayId, width, height, params,
                callback, executorService, context, new DisplayHelper(context));
    }
    @VisibleForTesting
    AppRequestFlow(
            @NonNull String callingPackageName,
            @NonNull String servicePackageName,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull Bundle params,
            @NonNull IRequestSurfacePackageCallback callback,
            @NonNull ListeningExecutorService executorService,
            @NonNull Context context,
            @NonNull DisplayHelper displayHelper) {
        mCallingPackageName = Objects.requireNonNull(callingPackageName);
        mServicePackageName = Objects.requireNonNull(servicePackageName);
        SurfaceInfo surfaceInfo = new SurfaceInfo(
                Objects.requireNonNull(hostToken), displayId, width, height);
        mSurfaceInfos = new ArrayList<SurfaceInfo>();
        mSurfaceInfos.add(surfaceInfo);
        // TODO(b/228200518): Support multiple slots.
        mParams = Objects.requireNonNull(params);
        mCallback = Objects.requireNonNull(callback);
        mExecutorService = Objects.requireNonNull(executorService);
        mContext = Objects.requireNonNull(context);
        mDisplayHelper = Objects.requireNonNull(displayHelper);

    }

    /** Runs the request processing flow. */
    public void run() {
        var unused = Futures.submit(() -> this.processRequest(), mExecutorService);
    }

    private void processRequest() {
        try {
            mServiceClassName = Objects.requireNonNull(
                    AppManifestConfigHelper.getServiceNameFromOdpSettings(
                            mContext, mServicePackageName));
            ListenableFuture<SelectContentResult> resultFuture = FluentFuture.from(
                            ProcessUtils.loadIsolatedService(
                                    TASK_NAME, mServicePackageName, mContext))
                    .transformAsync(
                            result -> executeAppRequest(result),
                            mExecutorService
                    )
                    .transform(
                            result -> {
                                return result.getParcelable(
                                        Constants.EXTRA_RESULT, SelectContentResult.class);
                            },
                            mExecutorService
                    );

            ListenableFuture<Long> queryIdFuture = FluentFuture.from(resultFuture)
                    .transformAsync(input -> logQuery(input), mExecutorService);

            ListenableFuture<List<SurfacePackage>> surfacePackagesFuture =
                    Futures.whenAllSucceed(resultFuture, queryIdFuture)
                            .callAsync(new AsyncCallable<List<SurfacePackage>>() {
                                @Override
                                public ListenableFuture<List<SurfacePackage>> call() {
                                    try {
                                        return renderContent(Futures.getDone(resultFuture),
                                                Futures.getDone(queryIdFuture));
                                    } catch (Exception e) {
                                        return Futures.immediateFailedFuture(e);
                                    }
                                }
                            }, mExecutorService);

            Futures.addCallback(
                    surfacePackagesFuture,
                    new FutureCallback<List<SurfacePackage>>() {
                        @Override
                        public void onSuccess(List<SurfacePackage> surfacePackages) {
                            sendDisplayResult(surfacePackages);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            Log.w(TAG, "Request failed.", t);
                            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                        }
                    },
                    mExecutorService);
        } catch (Exception e) {
            Log.e(TAG, "Could not process request.", e);
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
        }
    }

    private ListenableFuture<Bundle> executeAppRequest(IsolatedServiceInfo isolatedServiceInfo) {
        Log.d(TAG, "executeAppRequest() started.");
        Bundle serviceParams = new Bundle();
        ArrayList<SlotInfo> slotInfos = new ArrayList<>();
        for (SurfaceInfo surfaceInfo : mSurfaceInfos) {
            slotInfos.add(
                    new SlotInfo.Builder()
                            .setWidth(surfaceInfo.mWidth)
                            .setHeight(surfaceInfo.mHeight)
                            .build());
        }
        PersistableBundle appParams = mParams.getParcelable(
                OnDevicePersonalizationManager.EXTRA_APP_PARAMS, PersistableBundle.class);
        SelectContentInput input =
                new SelectContentInput.Builder()
                        .setAppPackageName(mCallingPackageName)
                        .setSlotInfos(slotInfos)
                        .setAppParams(appParams)
                        .build();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, input);
        DataAccessServiceImpl binder = new DataAccessServiceImpl(
                mCallingPackageName, mServicePackageName, mContext, true, null);
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        return ProcessUtils.runIsolatedService(
                isolatedServiceInfo, mServiceClassName, Constants.OP_SELECT_CONTENT, serviceParams);
    }

    private ListenableFuture<Long> logQuery(SelectContentResult result) {
        Log.d(TAG, "logQuery() started.");
        // TODO(b/228200518): Validate that slotIds and bidIds are present in REMOTE_DATA.
        // TODO(b/228200518): Populate queryData
        byte[] queryData = new byte[1];
        Query query = new Query.Builder()
                .setQuery(queryData)
                .setTimeMillis(System.currentTimeMillis())
                .build();
        long queryId = EventsDao.getInstance(mContext).insertQuery(query);
        if (queryId == -1) {
            return Futures.immediateFailedFuture(new RuntimeException("Failed to log query."));
        }
        return Futures.immediateFuture(queryId);
    }

    private ListenableFuture<List<SurfacePackage>> renderContent(
            SelectContentResult selectContentResult,
            long queryId) {
        Log.d(TAG, "renderContent() started.");
        List<SlotResult> slotResults = selectContentResult.getSlotResults();
        if (slotResults == null) {
            Log.w(TAG, "Missing input: SelectContentResult.slotResults is null.");
            return Futures.immediateFuture(null);
        }

        List<ListenableFuture<SurfacePackage>> surfacePackageFutures =
                new ArrayList<ListenableFuture<SurfacePackage>>();
        for (int i = 0; i < Math.min(mSurfaceInfos.size(), slotResults.size()); ++i) {
            SurfaceInfo surfaceInfo = mSurfaceInfos.get(i);
            SlotResult slotResult = slotResults.get(i);
            surfacePackageFutures.add(renderContentForSlot(surfaceInfo, slotResult, queryId));
        }

        return Futures.allAsList(surfacePackageFutures);
    }

    private ListenableFuture<SurfacePackage> renderContentForSlot(
            SurfaceInfo surfaceInfo, SlotResult slotResult, long queryId
    ) {
        Log.d(TAG, "renderContentForSlot() started.");
        if (surfaceInfo == null || slotResult == null) {
            return Futures.immediateFuture(null);
        }
        SlotInfo slotInfo =
                new SlotInfo.Builder()
                        .setHeight(surfaceInfo.mHeight)
                        .setWidth(surfaceInfo.mWidth).build();
        List<String> bidIds = new ArrayList<String>();
        for (ScoredBid bid : slotResult.getWinningBids()) {
            bidIds.add(bid.getBidId());
        }

        // TODO(b/228200518) Support multiple bidders.
        return FluentFuture.from(ProcessUtils.loadIsolatedService(
                        TASK_NAME, mServicePackageName, mContext))
                .transformAsync(
                        loadResult -> executeRenderContentRequest(
                                loadResult, slotInfo, slotResult, queryId, bidIds),
                        mExecutorService)
                .transform(result -> {
                    return result.getParcelable(
                            Constants.EXTRA_RESULT, RenderContentResult.class);
                }, mExecutorService)
                .transform(result -> mDisplayHelper.generateHtml(result), mExecutorService)
                .transformAsync(
                        result -> mDisplayHelper.displayHtml(
                                result,
                                surfaceInfo.mHostToken,
                                surfaceInfo.mDisplayId,
                                surfaceInfo.mWidth,
                                surfaceInfo.mHeight),
                        mExecutorService);
    }

    private ListenableFuture<Bundle> executeRenderContentRequest(
            IsolatedServiceInfo isolatedServiceInfo, SlotInfo slotInfo, SlotResult slotResult,
            long queryId, List<String> bidIds) {
        Log.d(TAG, "executeRenderContentRequest() started.");
        Bundle serviceParams = new Bundle();
        RenderContentInput input =
                new RenderContentInput.Builder().setSlotInfo(slotInfo).setBidIds(bidIds).build();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, input);
        DataAccessServiceImpl binder = new DataAccessServiceImpl(
                mCallingPackageName, mServicePackageName, mContext, false,
                new DataAccessServiceImpl.EventUrlQueryData(
                    queryId, slotResult.getSlotId(), bidIds));
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        // TODO(b/228200518): Create event handling URLs.
        return ProcessUtils.runIsolatedService(
                isolatedServiceInfo, mServiceClassName, Constants.OP_RENDER_CONTENT,
                serviceParams);
    }

    private void sendDisplayResult(List<SurfacePackage> surfacePackages) {
        try {
            if (surfacePackages != null && surfacePackages.size() > 0) {
                // TODO(b/228200518): Support multiple slots.
                SurfacePackage surfacePackage = surfacePackages.get(0);
                mCallback.onSuccess(surfacePackage);
            } else {
                Log.w(TAG, "surfacePackages is null or empty");
                sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Callback error", e);
        }
    }

    private void sendErrorResult(int errorCode) {
        try {
            mCallback.onError(errorCode);
        } catch (RemoteException e) {
            Log.w(TAG, "Callback error", e);
        }
    }

    static class SurfaceInfo {
        @NonNull
        public final IBinder mHostToken;
        public final int mDisplayId;
        public final int mWidth;
        public final int mHeight;

        SurfaceInfo(IBinder hostToken, int displayId, int width, int height) {
            mHostToken = hostToken;
            mDisplayId = displayId;
            mWidth = width;
            mHeight = height;
        }
    }
}
