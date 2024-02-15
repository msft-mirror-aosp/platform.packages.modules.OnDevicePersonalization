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

package com.android.ondevicepersonalization.services.data.user;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.services.PhFlags;

/**
 * A singleton class that stores all user privacy statuses in memory.
 */
public final class UserPrivacyStatus {
    public static UserPrivacyStatus sUserPrivacyStatus = null;
    private boolean mPersonalizationStatusEnabled;
    private boolean mProtectedAudienceEnabled;
    private boolean mMeasurementEnabled;
    private long mLastUserConsentCacheUpdate;

    private UserPrivacyStatus() {
        // Assume the more privacy-safe option until updated.
        mPersonalizationStatusEnabled = false;
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mLastUserConsentCacheUpdate = -1L;
    }

    /** Returns an instance of UserPrivacyStatus. */
    public static UserPrivacyStatus getInstance() {
        synchronized (UserPrivacyStatus.class) {
            if (sUserPrivacyStatus == null) {
                sUserPrivacyStatus = new UserPrivacyStatus();
            }
            return sUserPrivacyStatus;
        }
    }

    public void setPersonalizationStatusEnabled(boolean personalizationStatusEnabled) {
        PhFlags phFlags = PhFlags.getInstance();
        if (!phFlags.isPersonalizationStatusOverrideEnabled()) {
            mPersonalizationStatusEnabled = personalizationStatusEnabled;
        }
    }

    public boolean isPersonalizationStatusEnabled() {
        PhFlags phFlags = PhFlags.getInstance();
        if (phFlags.isPersonalizationStatusOverrideEnabled()) {
            return phFlags.getPersonalizationStatusOverrideValue();
        }
        return mPersonalizationStatusEnabled;
    }

    /**
     * Returns the user consent status of Protected Audience (PA).
     */
    public boolean isProtectedAudienceEnabled() {
        UserPrivacyStatus userPrivacyStatus = UserPrivacyStatus.getInstance();
        if (isUserConsentCacheValid()) {
            return mProtectedAudienceEnabled;
        }
        // TODO(b/318391297): make request to AdServices#getCommonStates API and update cache.
        return false;
    }

    /**
     * Returns the user consent status of Measurement.
     */
    public boolean isMeasurementEnabled() {
        UserPrivacyStatus userPrivacyStatus = UserPrivacyStatus.getInstance();
        if (isUserConsentCacheValid()) {
            return mMeasurementEnabled;
        }
        // TODO(b/318391297): make request to AdServices#getCommonStates API and update cache.
        return false;
    }

    /**
     * Update user consent cache and timestamp metadata.
     */
    @VisibleForTesting
    void updateUserConsentCache(boolean protectedAudienceEnabled,
            boolean measurementEnabled) {
        mProtectedAudienceEnabled = protectedAudienceEnabled;
        mMeasurementEnabled = measurementEnabled;
        mLastUserConsentCacheUpdate = System.currentTimeMillis();
    }

    /**
     * Returns whether the user consent cache remains valid.
     */
    @VisibleForTesting
    boolean isUserConsentCacheValid() {
        if (mLastUserConsentCacheUpdate == -1L) {
            return false;
        }
        long cacheDuration = System.currentTimeMillis() - mLastUserConsentCacheUpdate;
        return cacheDuration >= 0
                        && cacheDuration < PhFlags.getInstance().getUserConsentCacheInMillis();
    }

    /**
     * Reset user consent info for testing.
     */
    @VisibleForTesting
    void resetUserConsentForTesting() {
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mLastUserConsentCacheUpdate = -1L;
    }

    /**
     * Invalidate the user consent cache for testing.
     */
    @VisibleForTesting
    void invalidateUserConsentCacheForTesting() {
        mLastUserConsentCacheUpdate = System.currentTimeMillis()
                        - 2 * PhFlags.getInstance().getUserConsentCacheInMillis();
    }
}
