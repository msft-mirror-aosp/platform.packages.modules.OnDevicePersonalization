/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.federatedcompute.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.DeviceUtils;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.encryption.BackgroundKeyFetchJobService;
import com.android.federatedcompute.services.scheduling.DeleteExpiredJobService;

/** BroadcastReceiver used to restore FederatedCompute training jobs. */
public class FederatedComputeBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = FederatedComputeBroadcastReceiver.class.getSimpleName();

    private Flags mFlags;

    public FederatedComputeBroadcastReceiver() {
        mFlags = FlagsFactory.getFlags();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
            LogUtil.d(TAG, "GlobalKillSwitch on, skipped broadcast.");
            return;
        }

        if (!DeviceUtils.isFcpSupported(context)) {
            LogUtil.d(TAG, "Unsupported device, skipped broadcast.");
            return;
        }

        LogUtil.d(TAG, "onReceive() with intent %s ", intent.getAction());

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            LogUtil.d(TAG, "Received unexpected intent %s", intent.getAction());
            return;
        }

        BackgroundKeyFetchJobService.scheduleJobIfNeeded(context, mFlags);
        DeleteExpiredJobService.scheduleJobIfNeeded(context, mFlags);
    }
}
