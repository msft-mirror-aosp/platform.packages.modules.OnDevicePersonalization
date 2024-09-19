/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.os.Binder;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

/**
 * Container of process-stable flags.
 */
public class StableFlags {
    private static final Object sLock = new Object();
    private static volatile StableFlags sStableFlags = null;

    private final Map<String, Object> mStableFlagsMap = new HashMap<>();

    /** Returns the value of the named stable flag. */
    public static Object get(String flagName) {
        return getInstance().getStableFlag(flagName);

    }

    /** Returns the singleton instance of StableFlags. */
    @VisibleForTesting
    public static StableFlags getInstance() {
        if (sStableFlags == null) {
            synchronized (sLock) {
                if (sStableFlags == null) {
                    long origId = Binder.clearCallingIdentity();
                    sStableFlags = new StableFlags(FlagsFactory.getFlags());
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }
        return sStableFlags;
    }

    @VisibleForTesting
    StableFlags(Flags flags) {
        mStableFlagsMap.put(PhFlags.KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS,
                flags.getAppRequestFlowDeadlineSeconds());
        mStableFlagsMap.put(PhFlags.KEY_RENDER_FLOW_DEADLINE_SECONDS,
                flags.getRenderFlowDeadlineSeconds());
        mStableFlagsMap.put(PhFlags.KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS,
                flags.getWebTriggerFlowDeadlineSeconds());
        mStableFlagsMap.put(PhFlags.KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS,
                flags.getWebViewFlowDeadlineSeconds());
        mStableFlagsMap.put(PhFlags.KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS,
                flags.getExampleStoreFlowDeadlineSeconds());
        mStableFlagsMap.put(PhFlags.KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS,
                flags.getDownloadFlowDeadlineSeconds());
        mStableFlagsMap.put(PhFlags.KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED,
                flags.isSharedIsolatedProcessFeatureEnabled());
        mStableFlagsMap.put(PhFlags.KEY_TRUSTED_PARTNER_APPS_LIST,
                flags.getTrustedPartnerAppsList());
        mStableFlagsMap.put(PhFlags.KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED,
                flags.isArtImageLoadingOptimizationEnabled());
        mStableFlagsMap.put(PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                flags.isPersonalizationStatusOverrideEnabled());
        mStableFlagsMap.put(PhFlags.KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE,
                flags.getPersonalizationStatusOverrideValue());
        mStableFlagsMap.put(PhFlags.KEY_USER_CONTROL_CACHE_IN_MILLIS,
                flags.getUserControlCacheInMillis());
    }

    private Object getStableFlag(String flagName) {
        if (!mStableFlagsMap.containsKey(flagName)) {
            throw new IllegalArgumentException("Flag " + flagName + " is not stable.");
        }
        return mStableFlagsMap.get(flagName);
    }
}
