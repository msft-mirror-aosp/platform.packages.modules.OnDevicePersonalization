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

import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public final class AggregatedErrorCodesLogger {
    private static final String TAG = AggregatedErrorCodesLogger.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    /**
     * Adds the given isolatedServiceError code into the package specific DB via the {@link
     * OnDevicePersonalizationAggregatedErrorDataDao}.
     *
     * <p>No-op if the aggregate error reporting flag is disabled.
     *
     * @param isolatedServiceErrorCode the error code returned from the isolated service.
     * @param componentName the name of the component hosting the isolated service.
     * @param context calling service context.
     * @return {@link ListenableFuture} that resolves successfully when the error code is
     *     successfully logged via the Dao.
     */
    public static ListenableFuture<Void> logIsolatedServiceErrorCode(
            int isolatedServiceErrorCode, ComponentName componentName, Context context) {
        if (!FlagsFactory.getFlags().getAggregatedErrorReportingEnabled()) {
            sLogger.e(TAG + ": Skipping logging, aggregated error code logging disabled");
            return Futures.immediateVoidFuture();
        }

        return (ListenableFuture<Void>)
                OnDevicePersonalizationExecutors.getBackgroundExecutor()
                        .submit(() -> logError(isolatedServiceErrorCode, componentName, context));
    }

    private static void logError(
            int isolatedServiceErrorCode, ComponentName componentName, Context context) {
        String certDigest = "";
        try {
            certDigest = PackageUtils.getCertDigest(context, componentName.getPackageName());
        } catch (PackageManager.NameNotFoundException nne) {
            sLogger.e(TAG + ": failed to get cert digest.", nne);
            return;
        }

        OnDevicePersonalizationAggregatedErrorDataDao dao =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        context, componentName, certDigest);
        dao.addExceptionCount(isolatedServiceErrorCode, /* exceptionCount= */ 1);
    }

    /**
     * Deletes any aggregate error data tables on device except for those ODP services that are
     * still installed and enrolled.
     *
     * <p>No-op if the aggregate error reporting flag is disabled.
     *
     * <p>Can use the {@link #cleanupErrorData(Context)} if already calling on an {@code executor}.
     *
     * @param context calling service context.
     * @return {@link ListenableFuture} that resolves successfully when the deletion is successful.
     */
    public static ListenableFuture<Void> cleanupAggregatedErrorData(Context context) {
        if (!FlagsFactory.getFlags().getAggregatedErrorReportingEnabled()) {
            sLogger.e(TAG + ": Skipping cleanup, aggregated error code logging disabled");
            return Futures.immediateVoidFuture();
        }

        return (ListenableFuture<Void>)
                OnDevicePersonalizationExecutors.getBackgroundExecutor()
                        .submit(() -> cleanupErrorData(context));
    }

    /**
     * Deletes any aggregate error data tables on device except for those ODP services that are
     * still installed and enrolled.
     *
     * <p>No-op if the aggregate error reporting flag is disabled.
     *
     * <p>Should be called on an appropriate {@link OnDevicePersonalizationExecutors}.
     *
     * @param context calling service context.
     */
    public static void cleanupErrorData(Context context) {
        // Delete all error data for any services that are no longer installed
        ImmutableList<ComponentName> odpServices =
                AppManifestConfigHelper.getOdpServices(context, /* enrolledOnly= */ true);
        OnDevicePersonalizationAggregatedErrorDataDao.cleanupErrorData(context, odpServices);
    }

    /**
     * Test only method that returns count of error data tables on device.
     *
     * @param context calling service context.
     * @return the number of error data tables on device.
     */
    @VisibleForTesting
    public static int getErrorDataTableCount(Context context) {
        return OnDevicePersonalizationAggregatedErrorDataDao.getErrorDataTableNames(context).size();
    }

    private AggregatedErrorCodesLogger() {}
}
