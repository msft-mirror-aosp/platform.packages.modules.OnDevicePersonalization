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
import static android.adservices.ondevicepersonalization.Constants.STATUS_CLASS_NOT_FOUND;
import static android.adservices.ondevicepersonalization.Constants.STATUS_EXECUTION_INTERRUPTED;
import static android.adservices.ondevicepersonalization.Constants.STATUS_INTERNAL_ERROR;
import static android.adservices.ondevicepersonalization.Constants.STATUS_METHOD_NOT_FOUND;
import static android.adservices.ondevicepersonalization.Constants.STATUS_NULL_ADSERVICES_COMMON_MANAGER;
import static android.adservices.ondevicepersonalization.Constants.STATUS_REMOTE_EXCEPTION;
import static android.adservices.ondevicepersonalization.Constants.STATUS_SUCCESS;
import static android.adservices.ondevicepersonalization.Constants.STATUS_TIMEOUT;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_USER_CONTROL_CACHE_IN_MILLIS;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.StableFlags;
import com.android.ondevicepersonalization.services.reset.ResetDataJobService;
import com.android.ondevicepersonalization.services.util.DebugUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * A singleton class that stores all user privacy statuses in memory.
 */
public final class UserPrivacyStatus {
    private static final String TAG = "UserPrivacyStatus";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String PERSONALIZATION_STATUS_KEY = "PERSONALIZATION_STATUS";
    @VisibleForTesting
    static final int CONTROL_GIVEN_STATUS_CODE = 3;
    @VisibleForTesting
    static final int CONTROL_REVOKED_STATUS_CODE = 2;
    private static final Object sLock = new Object();
    private static volatile UserPrivacyStatus sUserPrivacyStatus = null;
    private boolean mProtectedAudienceEnabled;
    private boolean mMeasurementEnabled;
    private boolean mProtectedAudienceReset;
    private boolean mMeasurementReset;
    private long mLastUserControlCacheUpdate;
    private final Clock mClock;
    private final AdServicesCommonStatesWrapper mAdServicesCommonStatesWrapper;

    @VisibleForTesting
    UserPrivacyStatus(
            AdServicesCommonStatesWrapper wrapper,
            Clock clock) {
        // Assume the more privacy-safe option until updated.
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mProtectedAudienceReset = false;
        mMeasurementReset = false;
        mLastUserControlCacheUpdate = -1L;
        mAdServicesCommonStatesWrapper = Objects.requireNonNull(wrapper);
        mClock = Objects.requireNonNull(clock);
    }

    /** Returns an instance of UserPrivacyStatus. */
    public static UserPrivacyStatus getInstance() {
        if (sUserPrivacyStatus == null) {
            synchronized (sLock) {
                if (sUserPrivacyStatus == null) {
                    sUserPrivacyStatus = new UserPrivacyStatus(
                            new AdServicesCommonStatesWrapperImpl(
                                    OnDevicePersonalizationApplication.getAppContext()),
                            MonotonicClock.getInstance());
                }
            }
        }
        return sUserPrivacyStatus;
    }

    private static boolean isOverrideEnabled() {
        return DebugUtils.isDeveloperModeEnabled(
                OnDevicePersonalizationApplication.getAppContext())
                && (boolean) StableFlags.get(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE);
    }

    /**
     * Return if both Protected Audience (PA) and Measurement consent status are disabled
     */
    public boolean isProtectedAudienceAndMeasurementBothDisabled() {
        if (isOverrideEnabled()) {
            boolean overrideToBothEnabled =
                    (boolean) StableFlags.get(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
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
        if (isOverrideEnabled()) {
            return (boolean) StableFlags.get(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
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
        if (isOverrideEnabled()) {
            return (boolean) StableFlags.get(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
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
        mLastUserControlCacheUpdate = mClock.currentTimeMillis();
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
        long cacheDuration = mClock.currentTimeMillis() - mLastUserControlCacheUpdate;
        return cacheDuration >= 0
                && cacheDuration < (long) StableFlags.get(KEY_USER_CONTROL_CACHE_IN_MILLIS);
    }

    /**
     * Reset user control info for testing.
     */
    @VisibleForTesting
    void resetUserControlForTesting() {
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
        mLastUserControlCacheUpdate = mClock.currentTimeMillis()
                        - 2 * (long) StableFlags.get(KEY_USER_CONTROL_CACHE_IN_MILLIS);
    }

    private void fetchStateFromAdServices() {
        long startTime = mClock.elapsedRealtime();
        String packageName = OnDevicePersonalizationApplication.getAppContext().getPackageName();
        try {
            // IPC.
            AdServicesCommonStatesWrapper.CommonStatesResult commonStates =
                    mAdServicesCommonStatesWrapper.getCommonStates().get();
            StatsUtils.writeServiceRequestMetrics(
                    API_NAME_ADSERVICES_GET_COMMON_STATES,
                    packageName,
                    null,
                    mClock,
                    STATUS_SUCCESS,
                    startTime);
            // update cache.
            int updatedProtectedAudienceState = commonStates.getPaState();
            int updatedMeasurementState = commonStates.getMeasurementState();
            updateUserControlCache(updatedProtectedAudienceState, updatedMeasurementState);
        } catch (Exception e) {
            int statusCode = getExceptionStatus(e);
            sLogger.e(e, TAG + ": fetchStateFromAdServices error, status code %d", statusCode);
            StatsUtils.writeServiceRequestMetrics(
                    API_NAME_ADSERVICES_GET_COMMON_STATES,
                    packageName,
                    null,
                    mClock,
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
        if (e instanceof InterruptedException) {
            return STATUS_EXECUTION_INTERRUPTED;
        }

        Throwable cause = e;
        if (e instanceof ExecutionException) {
            cause = e.getCause(); // Unwrap the cause
        }
        if (cause instanceof TimeoutException) {
            return STATUS_TIMEOUT;
        }
        if (cause instanceof NoSuchMethodException) {
            return STATUS_METHOD_NOT_FOUND;
        }
        if (cause instanceof SecurityException) {
            return STATUS_CALLER_NOT_ALLOWED;
        }
        if (cause instanceof IllegalStateException) {
            return STATUS_INTERNAL_ERROR;
        }
        if (cause instanceof IllegalArgumentException) {
            return STATUS_INTERNAL_ERROR;
        }
        if (cause instanceof NoClassDefFoundError) {
            return STATUS_CLASS_NOT_FOUND;
        }
        if (cause instanceof AdServicesCommonStatesWrapper.NullAdServiceCommonManagerException) {
            return STATUS_NULL_ADSERVICES_COMMON_MANAGER;
        }
        if (cause instanceof InterruptedException) {
            return STATUS_EXECUTION_INTERRUPTED;
        }
        return STATUS_REMOTE_EXCEPTION;
    }
}
