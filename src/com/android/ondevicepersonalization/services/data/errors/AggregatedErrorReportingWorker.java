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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.util.DebugUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the various subtasks in reporting the aggregate error data for each vendor.
 *
 * <p>Called into by the {@link AggregateErrorDataReportingService} to offload the details of
 * accumulating and reporting the error counts in the per vendor tables.
 */
class AggregatedErrorReportingWorker {
    private static final String TAG = AggregatedErrorReportingWorker.class.getSimpleName();

    private static volatile AggregatedErrorReportingWorker sWorker;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final Object sLock = new Object();
    private final Injector mInjector;

    @GuardedBy("this")
    private ListenableFuture<Void> mCurrentFuture = null;

    /** Helper class to allow injection of mocks/test-objects in test. */
    static class Injector {
        ListeningExecutorService getLightweightExecutor() {
            return OnDevicePersonalizationExecutors.getLightweightExecutor();
        }

        ListeningExecutorService getBackgroundExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        ReportingProtocol getAggregatedErrorReportingProtocol(
                ImmutableList<ErrorData> errorData, String requestBaseUri, Context context) {
            return AggregatedErrorReportingProtocol.createAggregatedErrorReportingProtocol(
                    errorData, requestBaseUri, context);
        }

        String getServerUrl(Context context, String packageName) {
            return AggregatedErrorReportingWorker.getFcRemoteServerUrl(context, packageName);
        }
    }

    private AggregatedErrorReportingWorker(Injector injector) {
        this.mInjector = injector;
    }

    public static AggregatedErrorReportingWorker getInstance() {
        // Telescope into test-only method and provide default injector instance.
        return getInstance(new Injector());
    }

    @VisibleForTesting
    static AggregatedErrorReportingWorker getInstance(Injector injector) {
        if (sWorker == null) {
            synchronized (sLock) {
                if (sWorker == null) {
                    sWorker = new AggregatedErrorReportingWorker(injector);
                }
            }
        }

        return sWorker;
    }

    @VisibleForTesting
    static void resetForTesting() {
        synchronized (sLock) {
            sWorker = null;
        }
    }

    public synchronized ListenableFuture<Void> reportAggregateErrors(Context context) {
        if (mCurrentFuture != null) {
            sLogger.e(TAG + ": aggregate reporting is already ongoing.");
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Duplicate report request"));
        }

        sLogger.d(TAG + ": beginning aggregate error reporting.");
        mCurrentFuture =
                Futures.submitAsync(
                        () -> reportAggregateErrorsHelper(context),
                        mInjector.getBackgroundExecutor());
        return mCurrentFuture;
    }

    @VisibleForTesting
    ListenableFuture<Void> reportAggregateErrorsHelper(Context context) {
        List<ComponentName> odpServices =
                AppManifestConfigHelper.getOdpServices(context, /* enrolledOnly= */ true);
        if (odpServices.isEmpty()) {
            sLogger.d(TAG + ": No odp services installed on device, skipping reporting");
            cleanup();
            return Futures.immediateVoidFuture();
        }

        List<ListenableFuture<Boolean>> futureList = new ArrayList<>();
        for (ComponentName componentName : odpServices) {
            String certDigest = getCertDigest(context, componentName.getPackageName());
            if (certDigest.isEmpty()) {
                sLogger.d(
                        TAG
                                + ": Skipping reporting for package :"
                                + componentName.getPackageName());
                continue;
            }

            String fcServerUrl = mInjector.getServerUrl(context, componentName.getPackageName());
            if (fcServerUrl.isEmpty()) {
                sLogger.d(
                        TAG
                                + ": Skipping reporting for package, missing server url : "
                                + componentName.getPackageName());
                continue;
            }

            OnDevicePersonalizationAggregatedErrorDataDao errorDataDao =
                    OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                            context, componentName, certDigest);
            if (errorDataDao == null) {
                sLogger.d(
                        TAG
                                + ": Skipping reporting no table found for component :"
                                + componentName);
                continue;
            }

            ImmutableList<ErrorData> errorDataList = errorDataDao.getExceptionData();
            if (errorDataList.isEmpty()) {
                sLogger.d(
                        TAG + ": Skipping reporting no data found for component :" + componentName);
                continue;
            }

            ReportingProtocol errorReportingProtocol =
                    mInjector.getAggregatedErrorReportingProtocol(
                            errorDataList, fcServerUrl, context);
            ListenableFuture<Boolean> reportingFuture =
                    errorReportingProtocol.reportExceptionData();
            Futures.addCallback(
                    reportingFuture,
                    new FutureCallback<Boolean>() {
                        // TODO(b/367773359): add WW logging for success/failure etc.
                        @Override
                        public void onSuccess(Boolean result) {
                            if (result) {
                                sLogger.d(
                                        TAG
                                                + ": reporting successful for component : "
                                                + componentName);
                            } else {
                                sLogger.d(
                                        TAG
                                                + ": reporting failed for component : "
                                                + componentName);
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            sLogger.e(
                                    TAG + ": reporting failed for component :" + componentName, t);
                        }
                    },
                    mInjector.getLightweightExecutor());

            futureList.add(reportingFuture);
        }

        sLogger.d(TAG + " :waiting for " + futureList.size() + " futures to complete.");
        // Wait for all the futures to complete or time-out, logging of successful/failure etc.
        // is performed in the callback of each individual future.
        return Futures.whenAllComplete(futureList)
                .call(
                        () -> {
                            cleanup();
                            return null;
                        },
                        mInjector.getLightweightExecutor());
    }

    @VisibleForTesting
    void cleanup() {
        // Helper method to clean-up at the end of reporting.
        synchronized (this) {
            mCurrentFuture = null;
        }
    }

    private static String getCertDigest(Context context, String packageName) {
        // Helper method that catches the exception and returns an empty cert digest
        try {
            return PackageUtils.getCertDigest(context, packageName);
        } catch (PackageManager.NameNotFoundException nne) {
            sLogger.e(TAG + " : failed to query cert digest for package : " + packageName, nne);
        }
        return "";
    }

    private static String getFcRemoteServerUrl(Context context, String packageName) {
        // Helper method that catches any runtime exceptions thrown by parsing failures and returns
        // an empty URL
        try {
            String manifestUrl =
                    AppManifestConfigHelper.getFcRemoteServerUrlFromOdpSettings(
                            context, packageName);
            String overrideUrl = DebugUtils.getFcServerOverrideUrl(context, packageName);
            return overrideUrl.isEmpty() ? manifestUrl : overrideUrl;
        } catch (Exception e) {
            sLogger.e(TAG + " : failed to extract server URL for package : " + packageName, e);
        }
        return "";
    }
}
