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

package com.android.ondevicepersonalization.services;

import android.adservices.ondevicepersonalization.CalleeMetadata;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.aidl.IIsFeatureEnabledCallback;
import android.os.Binder;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class FeatureStatusManager {
    private static final Object sLock = new Object();

    private static final String TAG = FeatureStatusManager.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static volatile FeatureStatusManager sFeatureStatusManager = null;

    private final Map<String, Supplier<Boolean>> mFlaggedFeaturesMap = new HashMap<>();

    private final Set<String> mNonFlaggedFeaturesSet = new HashSet<>();

    private Flags mFlags;

    /** Returns the status of the feature. */
    public static void getFeatureStatusAndSendResult(
            String featureName,
            long serviceEntryTime,
            IIsFeatureEnabledCallback callback) {
        int result = getInstance().isFeatureEnabled(featureName);
        try {
            callback.onResult(
                    result,
                    new CalleeMetadata.Builder()
                            .setServiceEntryTimeMillis(serviceEntryTime)
                            .setCallbackInvokeTimeMillis(
                                    SystemClock.elapsedRealtime()).build());
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        }
    }

    /** Returns the singleton instance of FeatureManager. */
    public static FeatureStatusManager getInstance() {
        if (sFeatureStatusManager == null) {
            synchronized (sLock) {
                if (sFeatureStatusManager == null) {
                    long origId = Binder.clearCallingIdentity();
                    sFeatureStatusManager = new FeatureStatusManager(FlagsFactory.getFlags());
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }
        return sFeatureStatusManager;
    }

    @VisibleForTesting
    FeatureStatusManager(Flags flags) {
        mFlags = flags;
        // Add flagged features here, for example:
        // mFlaggedFeaturesMap.put("featureName", mFlags::isFeatureEnabled);

        // Add non-flagged features here, for example:
        // mNonFlaggedFeaturesSet.add("featureName");
    }

    @VisibleForTesting
    FeatureStatusManager(Flags flags,
            Map<String, Supplier<Boolean>> flaggedFeaturesMap,
            Set<String> nonFlaggedFeaturesSet) {
        mFlags = flags;

        // Add flagged features here
        mFlaggedFeaturesMap.putAll(flaggedFeaturesMap);

        // Add non-flagged features here
        mNonFlaggedFeaturesSet.addAll(nonFlaggedFeaturesSet);
    }

    @VisibleForTesting
    int isFeatureEnabled(String featureName) {
        if (mNonFlaggedFeaturesSet.contains(featureName)) {
            return OnDevicePersonalizationManager.FEATURE_ENABLED;
        }

        if (mFlaggedFeaturesMap.containsKey(featureName)) {
            boolean flagValue = mFlaggedFeaturesMap.get(featureName).get();
            return flagValue ? OnDevicePersonalizationManager.FEATURE_ENABLED
                    : OnDevicePersonalizationManager.FEATURE_DISABLED;
        }

        return OnDevicePersonalizationManager.FEATURE_UNSUPPORTED;
    }
}
