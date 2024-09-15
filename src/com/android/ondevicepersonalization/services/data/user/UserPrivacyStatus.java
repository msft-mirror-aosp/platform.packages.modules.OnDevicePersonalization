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

import static android.adservices.ondevicepersonalization.Constants.API_NAME_ADSERVICES_GET_COMMON_STATES;
import static android.adservices.ondevicepersonalization.Constants.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.ondevicepersonalization.Constants.STATUS_INTERNAL_ERROR;
import static android.adservices.ondevicepersonalization.Constants.STATUS_METHOD_NOT_FOUND;
import static android.adservices.ondevicepersonalization.Constants.STATUS_REMOTE_EXCEPTION;
import static android.adservices.ondevicepersonalization.Constants.STATUS_SUCCESS;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_USER_CONTROL_CACHE_IN_MILLIS;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.reset.ResetDataJobService;
import com.android.ondevicepersonalization.services.util.DebugUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import java.util.Objects;

/**
 * A singleton class that stores all user privacy statuses in memory.
 */
public final class UserPrivacyStatus {
    private static final String TAG = "UserPrivacyStatus";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final Clock sClock = MonotonicClock.getInstance();
    private static final String PERSONALIZATION_STATUS_KEY = "PERSONALIZATION_STATUS";
    @VisibleForTesting
    static final int CONTROL_GIVEN_STATUS_CODE = 3;
    @VisibleForTesting
    static final int CONTROL_REVOKED_STATUS_CODE = 2;
    static volatile UserPrivacyStatus sUserPrivacyStatus = null;
    private boolean mPersonalizationStatusEnabled;
    private boolean mProtectedAudienceEnabled;
    private boolean mMeasurementEnabled;
    private boolean mProtectedAudienceReset;
    private boolean mMeasurementReset;
    private long mLastUserControlCacheUpdate;
    private final AdServicesCommonStatesWrapper mAdServicesCommonStatesWrapper;

    private UserPrivacyStatus(AdServicesCommonStatesWrapper wrapper) {
        // Assume the more privacy-safe option until updated.
        mPersonalizationStatusEnabled = false;
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mProtectedAudienceReset = false;
        mMeasurementReset = false;
        mLastUserControlCacheUpdate = -1L;
        mAdServicesCommonStatesWrapper = Objects.requireNonNull(wrapper);
    }

    /** Returns an instance of UserPrivacyStatus. */
    public static UserPrivacyStatus getInstance() {
        if (sUserPrivacyStatus == null) {
            synchronized (UserPrivacyStatus.class) {
                if (sUserPrivacyStatus == null) {
                    sUserPrivacyStatus = new UserPrivacyStatus(
                            new AdServicesCommonStatesWrapperImpl(
                                    OnDevicePersonalizationApplication.getAppContext()));
                }
            }
        }
        return sUserPrivacyStatus;
    }

    private static boolean isOverrideEnabled() {
        Flags flags = FlagsFactory.getFlags();
        return DebugUtils.isDeveloperModeEnabled(
                OnDevicePersonalizationApplication.getAppContext())
                && (boolean) flags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE);
    }

    public void setPersonalizationStatusEnabled(boolean personalizationStatusEnabled) {
        Flags flags = FlagsFactory.getFlags();
        if (!isOverrideEnabled()) {
            mPersonalizationStatusEnabled = personalizationStatusEnabled;
        }
    }

    public boolean isPersonalizationStatusEnabled() {
        Flags flags = FlagsFactory.getFlags();
        if (isOverrideEnabled()) {
            return (boolean) flags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
        }
        return mPersonalizationStatusEnabled;
    }

    /**
     * Return if both Protected Audience (PA) and Measurement consent status are disabled
     */
    public boolean isProtectedAudienceAndMeasurementBothDisabled() {
        Flags flags = FlagsFactory.getFlags();
        if (isOverrideEnabled()) {
            boolean overrideToBothEnabled =
                    (boolean) flags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
            return !overrideToBothEnabled;
        }
        if (isUserControlCacheValid()) {
            return !mProtectedAudienceEnabled && !mMeasurementEnabled;
        }
        // make request to AdServices#getCommonStates API once
        fetchStateFromAdServices();
        return !mProtectedAudienceEnabled && !mMeasurementEnabled;
    }

    /**
     * Returns the user control status of Protected Audience (PA).
     */
    public boolean isProtectedAudienceEnabled() {
        Flags flags = FlagsFactory.getFlags();
        if (isOverrideEnabled()) {
            return (boolean) flags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
        }
        if (isUserControlCacheValid()) {
            return mProtectedAudienceEnabled;
        }
        // make request to AdServices#getCommonStates API.
        fetchStateFromAdServices();
        return mProtectedAudienceEnabled;
    }

    /**
     * Returns the user control status of Measurement.
     */
    public boolean isMeasurementEnabled() {
        Flags flags = FlagsFactory.getFlags();
        if (isOverrideEnabled()) {
            return (boolean) flags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
        }
        if (isUserControlCacheValid()) {
            return mMeasurementEnabled;
        }
        // make request to AdServices#getCommonStates API.
        fetchStateFromAdServices();
        return mMeasurementEnabled;
    }

    /**
     * Returns true if the user requests a reset on PA-related data.
     */
    private boolean isProtectedAudienceReset() {
        return mProtectedAudienceReset;
    }

    /**
     * Returns true if the user requests a reset on measurement-related data.
     */
    private boolean isMeasurementReset() {
        return mMeasurementReset;
    }

    /**
     * Update user control cache and timestamp metadata.
     */
    @VisibleForTesting
    void updateUserControlCache(int protectedAudienceState,
            int measurementState) {
        mProtectedAudienceEnabled = (protectedAudienceState != CONTROL_REVOKED_STATUS_CODE);
        mMeasurementEnabled = (measurementState != CONTROL_REVOKED_STATUS_CODE);
        mProtectedAudienceReset = (protectedAudienceState != CONTROL_GIVEN_STATUS_CODE);
        mMeasurementReset = (measurementState != CONTROL_GIVEN_STATUS_CODE);
        mLastUserControlCacheUpdate = sClock.currentTimeMillis();
        handleResetIfNeeded();
    }

    /**
     * Returns whether the user control cache remains valid.
     */
    @VisibleForTesting
    boolean isUserControlCacheValid() {
        if (mLastUserControlCacheUpdate == -1L) {
            return false;
        }
        long cacheDuration = sClock.currentTimeMillis() - mLastUserControlCacheUpdate;
        return cacheDuration >= 0
                        && cacheDuration < (long) FlagsFactory.getFlags().getStableFlag(
                                        KEY_USER_CONTROL_CACHE_IN_MILLIS);
    }

    /**
     * Reset user control info for testing.
     */
    @VisibleForTesting
    void resetUserControlForTesting() {
        mPersonalizationStatusEnabled = false;
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mProtectedAudienceReset = false;
        mMeasurementReset = false;
        mLastUserControlCacheUpdate = -1L;
    }

    /**
     * Invalidate the user control cache for testing.
     */
    @VisibleForTesting
    void invalidateUserControlCacheForTesting() {
        mLastUserControlCacheUpdate = sClock.currentTimeMillis()
                        - 2 * (long) FlagsFactory.getFlags().getStableFlag(
                                        KEY_USER_CONTROL_CACHE_IN_MILLIS);
    }

    private void fetchStateFromAdServices() {
        long startTime = sClock.elapsedRealtime();
        String packageName = OnDevicePersonalizationApplication.getAppContext().getPackageName();
        try {
            // IPC.
            AdServicesCommonStatesWrapper.CommonStatesResult commonStates =
                    mAdServicesCommonStatesWrapper.getCommonStates().get();
            StatsUtils.writeServiceRequestMetrics(
                    API_NAME_ADSERVICES_GET_COMMON_STATES,
                    packageName,
                    null,
                    sClock,
                    STATUS_SUCCESS,
                    startTime);
            // update cache.
            int updatedProtectedAudienceState = commonStates.getPaState();
            int updatedMeasurementState = commonStates.getMeasurementState();
            updateUserControlCache(updatedProtectedAudienceState, updatedMeasurementState);
        } catch (Exception e) {
            sLogger.e(TAG + ": fetchStateFromAdServices error", e);
            int statusCode = getExceptionStatus(e);
            StatsUtils.writeServiceRequestMetrics(
                    API_NAME_ADSERVICES_GET_COMMON_STATES,
                    packageName,
                    null,
                    sClock,
                    statusCode,
                    startTime);
        }
    }

    private void handleResetIfNeeded() {
        if (isMeasurementReset() || isProtectedAudienceReset()) {
            ResetDataJobService.schedule();
        }
    }

    @VisibleForTesting
    int getExceptionStatus(Exception e) {
        if (e instanceof NoSuchMethodException) {
            return STATUS_METHOD_NOT_FOUND;
        }
        if (e instanceof SecurityException) {
            return STATUS_CALLER_NOT_ALLOWED;
        }
        if (e instanceof IllegalArgumentException) {
            return STATUS_INTERNAL_ERROR;
        }
        return STATUS_REMOTE_EXCEPTION;
    }
}
