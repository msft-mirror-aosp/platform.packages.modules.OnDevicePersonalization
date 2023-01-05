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

package com.android.ondevicepersonalization.services.download.mdd;

import android.content.Context;
import android.util.Log;

import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingWorker;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * MDD Worker. This will download MDD files in background tasks.
 */
public class PeriodicWorker extends ListenableWorker {
    public static final String MDD_TASK_TAG_KEY = "MDD_TASK_TAG_KEY";
    private static final String TAG = "MddPeriodicWorker";

    private final Context mContext;

    public PeriodicWorker(
            Context context,
            WorkerParameters params) {
        super(context, params);
        this.mContext = context;
    }

    @Override
    public ListenableFuture<ListenableWorker.Result> startWork() {
        Log.d(TAG, "PeriodicWorker: startWork");

        // Get the mddTaskTag from input.
        Data input = getInputData();
        String mddTaskTag = input.getString(MDD_TASK_TAG_KEY);
        if (mddTaskTag == null) {
            Log.e(TAG, "can't find MDD task tag");
            return Futures.immediateFuture(ListenableWorker.Result.failure());
        }

        ListenableFuture<Void> handleTaskFuture =
                PropagatedFutures.submitAsync(
                        () -> MobileDataDownloadFactory.getMdd(mContext).handleTask(mddTaskTag),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());

        return PropagatedFutures.transform(
                handleTaskFuture, (unusedVoid) -> {
                    OnDevicePersonalizationDownloadProcessingWorker.enqueue(mContext);
                    return ListenableWorker.Result.success();
                },
                OnDevicePersonalizationExecutors.getBackgroundExecutor());
    }

    @Override
    public void onStopped() {
        // Attempt to process any data downloaded before the worker was stopped.
        OnDevicePersonalizationDownloadProcessingWorker.enqueue(mContext);
    }
}
