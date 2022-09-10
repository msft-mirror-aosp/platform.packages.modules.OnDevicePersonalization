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
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.libraries.mobiledatadownload.TaskScheduler;

import java.util.concurrent.TimeUnit;

/**
 * MddTaskScheduler that uses WorkManager to schedule MDD background tasks
 */
public class MddTaskScheduler implements TaskScheduler {
    private static final String MDD_TASK_SHARED_PREFS = "mdd_worker_task_periods";
    private final Context mContext;

    public MddTaskScheduler(Context context) {
        this.mContext = context;
    }

    private static Constraints getConstraints(NetworkState networkState) {
        return new Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(getNetworkType(networkState))
                .build();
    }

    private static NetworkType getNetworkType(NetworkState networkState) {
        switch (networkState) {
            case NETWORK_STATE_ANY:
                return androidx.work.NetworkType.NOT_REQUIRED;
            case NETWORK_STATE_CONNECTED:
                return androidx.work.NetworkType.CONNECTED;
            case NETWORK_STATE_UNMETERED:
                return androidx.work.NetworkType.UNMETERED;
        }
        return androidx.work.NetworkType.NOT_REQUIRED;
    }

    private static ExistingPeriodicWorkPolicy getExistingPeriodicWorkPolicy(boolean updateCurrent) {
        // When updateCurrent == true, use ExistingWorkPolicy.REPLACE to cancel and delete any
        // existing
        // pending (uncompleted) work with the same unique name. Then, insert the newly-specified
        // work.
        return updateCurrent ? ExistingPeriodicWorkPolicy.REPLACE : ExistingPeriodicWorkPolicy.KEEP;
    }

    @Override
    public void schedulePeriodicTask(String mddTaskTag, long period, NetworkState networkState) {
        SharedPreferences prefs =
                mContext.getSharedPreferences(MDD_TASK_SHARED_PREFS, Context.MODE_PRIVATE);

        // When the period changes, update the existing workers
        if (prefs.getLong(mddTaskTag, 0) != period) {
            schedulePeriodicTaskWithUpdate(mddTaskTag, period, networkState, true);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(mddTaskTag, period);
            editor.apply();
        } else {
            schedulePeriodicTaskWithUpdate(mddTaskTag, period, networkState, false);
        }
    }

    private void schedulePeriodicTaskWithUpdate(String mddTaskTag, long period,
            NetworkState networkState, boolean updateCurrent) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                PeriodicWorker.class, period, TimeUnit.SECONDS)
                .addTag(mddTaskTag)
                .setConstraints(getConstraints(networkState))
                .setInputData(
                        new Data.Builder().putString(PeriodicWorker.MDD_TASK_TAG_KEY,
                                mddTaskTag).build())
                .build();

        WorkManager.getInstance(mContext).enqueueUniquePeriodicWork(
                mddTaskTag, getExistingPeriodicWorkPolicy(updateCurrent), workRequest);
    }
}
