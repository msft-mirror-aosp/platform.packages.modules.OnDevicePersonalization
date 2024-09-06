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

package com.android.ondevicepersonalization.services;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.DeviceUtils;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.user.UserDataCollectionJobService;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJob;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/** BroadcastReceiver used to schedule OnDevicePersonalization jobs/workers. */
public class OnDevicePersonalizationBroadcastReceiver extends BroadcastReceiver {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationBroadcastReceiver";
    private final Executor mExecutor;

    public OnDevicePersonalizationBroadcastReceiver() {
        this.mExecutor = OnDevicePersonalizationExecutors.getLightweightExecutor();
    }

    @VisibleForTesting
    public OnDevicePersonalizationBroadcastReceiver(Executor executor) {
        this.mExecutor = executor;
    }

    /** Enable the OnDevicePersonalizationBroadcastReceiver */
    public static boolean enableReceiver(Context context) {
        try {
            context.getPackageManager()
                    .setComponentEnabledSetting(
                            new ComponentName(
                                    context, OnDevicePersonalizationBroadcastReceiver.class),
                            COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
        } catch (IllegalArgumentException e) {
            sLogger.e(TAG + ": enableService failed for " + context.getPackageName(), e);
            return false;
        }
        return true;
    }

    /** Called when the broadcast is received. OnDevicePersonalization jobs will be started here. */
    public void onReceive(Context context, Intent intent) {
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            sLogger.d(TAG + ": GlobalKillSwitch on, skipped broadcast.");
            return;
        }

        if (!DeviceUtils.isOdpSupported(context)) {
            sLogger.d(TAG + ": Unsupported device, skipped broadcast.");
            return;
        }

        sLogger.d(TAG + ": onReceive() with intent + " + intent.getAction());

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            sLogger.d(TAG + ": Received unexpected intent " + intent.getAction());
            return;
        }
        final PendingResult pendingResult = goAsync();
        // Schedule MDD to download scripts periodically.
        Futures.addCallback(
                restoreOdpJobs(context, mExecutor),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.d(TAG + ": Successfully scheduled MDD tasks.");
                        pendingResult.finish();
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(TAG + ": Failed to schedule MDD tasks.", t);
                        pendingResult.finish();
                    }
                },
                mExecutor);
    }

    /**
     * Restores periodic jobs scheduling.
     */
    public static ListenableFuture<Void> restoreOdpJobs(Context context, Executor executor) {
        if (FlagsFactory.getFlags().getGlobalKillSwitch() || !DeviceUtils.isOdpSupported(context)) {
            sLogger.d(TAG + ": ODP disabled or unsupported device");
            return null;
        }

        var unused =
                Futures.submit(
                        () -> {
                            // Schedule maintenance task
                            OnDevicePersonalizationMaintenanceJob.schedule(context);
                            // Schedule user data collection task
                            UserDataCollectionJobService.schedule(context);
                        },
                        executor);

        // Schedule MDD to download scripts periodically.
        return MobileDataDownloadFactory.getMdd(context).schedulePeriodicBackgroundTasks();
    }
}
