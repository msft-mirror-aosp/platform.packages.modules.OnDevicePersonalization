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

package com.android.ondevicepersonalization.services.download;

import static android.content.pm.PackageManager.GET_META_DATA;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * Worker to handle the processing of the downloaded vendor data
 */
public class OnDevicePersonalizationDownloadProcessingWorker extends ListenableWorker {
    public static final String TAG = "OnDevicePersonalizationDownloadProcessingWorker";
    private final PackageManager mPackageManager;
    private final Context mContext;
    private List<ListenableFuture<Void>> mFutures;

    public OnDevicePersonalizationDownloadProcessingWorker(
            Context context,
            WorkerParameters params) {
        super(context, params);
        this.mContext = context;
        this.mPackageManager = context.getPackageManager();
    }

    /**
     * Schedules a unique instance of OnDevicePersonalizationDownloadProcessingWorker to be run.
     */
    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresDeviceIdle(true)
                .build();

        OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(
                        OnDevicePersonalizationDownloadProcessingWorker.class)
                        .setConstraints(constraints)
                        .addTag(TAG)
                        .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, workRequest);
        Log.d(TAG, "Successfully enqueued job.");
    }

    @Override
    public ListenableFuture<Result> startWork() {
        Log.d(TAG, "doWork()");
        mFutures = new ArrayList<>();
        for (PackageInfo packageInfo : mPackageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(GET_META_DATA))) {
            if (isStopped()) {
                break;
            }
            if (AppManifestConfigHelper.manifestContainsOdpSettings(
                    mContext, packageInfo.packageName)) {
                mFutures.add(Futures.submitAsync(
                        new OnDevicePersonalizationDataProcessingAsyncCallable(packageInfo,
                                mContext),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor()));
            }
        }
        return Futures.whenAllComplete(mFutures).call(() -> Result.success(),
                OnDevicePersonalizationExecutors.getLightweightExecutor());
    }

    @Override
    public void onStopped() {
        for (ListenableFuture<Void> f : mFutures) {
            f.cancel(true);
        }
    }

    private boolean manifestContainsOdpDownloadSettings(PackageInfo packageInfo) {
        // TODO(b/239479120): Implement this method
        Log.d(TAG, "Checking manifestContainsOdpDownloadSettings for "
                + packageInfo.packageName);
        return false;
    }
}
