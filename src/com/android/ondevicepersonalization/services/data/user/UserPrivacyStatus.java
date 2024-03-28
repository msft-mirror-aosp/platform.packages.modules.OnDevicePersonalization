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

import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_USER_CONTROL_CACHE_IN_MILLIS;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStates;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.ondevicepersonalization.Constants;
import android.annotation.NonNull;
import android.content.Context;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemService;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.ondevicepersonalization.OnDevicePersonalizationSystemServiceManager;
import android.os.Bundle;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;

import com.google.common.util.concurrent.ListenableFuture;

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
    static UserPrivacyStatus sUserPrivacyStatus = null;
    private boolean mPersonalizationStatusEnabled;
    private boolean mProtectedAudienceEnabled;
    private boolean mMeasurementEnabled;
    private boolean mProtectedAudienceReset;
    private boolean mMeasurementReset;
    private long mLastUserControlCacheUpdate;

    private UserPrivacyStatus() {
        // Assume the more privacy-safe option until updated.
        mPersonalizationStatusEnabled = false;
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mProtectedAudienceReset = true;
        mMeasurementReset = true;
        mLastUserControlCacheUpdate = -1L;
    }

    /** Returns an instance of UserPrivacyStatus. */
    public static UserPrivacyStatus getInstance() {
        if (sUserPrivacyStatus == null) {
            synchronized (UserPrivacyStatus.class) {
                if (sUserPrivacyStatus == null) {
                    sUserPrivacyStatus = new UserPrivacyStatus();
                    // Restore personalization status from the system server on U+ devices.
                    if (SdkLevel.isAtLeastU()) {
                        sUserPrivacyStatus.restorePersonalizationStatus();
                    }
                }
            }
        }
        return sUserPrivacyStatus;
    }

    public void setPersonalizationStatusEnabled(boolean personalizationStatusEnabled) {
        Flags flags = FlagsFactory.getFlags();
        if (!(boolean) flags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)) {
            mPersonalizationStatusEnabled = personalizationStatusEnabled;
        }
    }

    public boolean isPersonalizationStatusEnabled() {
        Flags flags = FlagsFactory.getFlags();
        if ((boolean) flags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)) {
            return (boolean) flags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE);
        }
        return mPersonalizationStatusEnabled;
    }

    /**
     * Returns the user control status of Protected Audience (PA).
     */
    public boolean isProtectedAudienceEnabled() {
        Flags flags = FlagsFactory.getFlags();
        if ((boolean) flags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)) {
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
        if ((boolean) flags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)) {
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
    public boolean isProtectedAudienceReset() {
        if (isUserControlCacheValid()) {
            return mProtectedAudienceReset;
        }
        // make request to AdServices#getCommonStates API.
        fetchStateFromAdServices();
        return mProtectedAudienceReset;
    }

    /**
     * Returns true if the user requests a reset on measurement-related data.
     */
    public boolean isMeasurementReset() {
        if (isUserControlCacheValid()) {
            return mMeasurementReset;
        }
        // make request to AdServices#getCommonStates API.
        fetchStateFromAdServices();
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
        mProtectedAudienceEnabled = false;
        mMeasurementEnabled = false;
        mProtectedAudienceReset = true;
        mMeasurementReset = true;
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
        try {
            // IPC.
            AdServicesCommonManager adServicesCommonManager = getAdServicesCommonManager();
            AdServicesCommonStates commonStates =
                            getAdServicesCommonStates(adServicesCommonManager);

            // update cache.
            int updatedProtectedAudienceState = commonStates.getPaState();
            int updatedMeasurementState = commonStates.getMeasurementState();
            updateUserControlCache(updatedProtectedAudienceState, updatedMeasurementState);
        } catch (Exception e) {
            sLogger.e(TAG + ": fetchStateFromAdServices error", e);
        }
    }

    /**
     * Get AdServices common manager from ODP.
     */
    private static AdServicesCommonManager getAdServicesCommonManager() {
        Context odpContext = OnDevicePersonalizationApplication.getAppContext();
        try {
            return odpContext.getSystemService(AdServicesCommonManager.class);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("Cannot find AdServicesCommonManager.", e);
        }
    }

    /**
     * Get common states from AdServices, such as user control.
     */
    private AdServicesCommonStates getAdServicesCommonStates(
                    @NonNull AdServicesCommonManager adServicesCommonManager) {
        ListenableFuture<AdServicesCommonStatesResponse> response =
                        getAdServicesResponse(adServicesCommonManager);
        try {
            return response.get().getAdServicesCommonStates();
        } catch (Exception e) {
            throw new IllegalStateException("Failed when calling "
                    + "AdServicesCommonManager#getAdServicesCommonStates().", e);
        }
    }

    /**
     * IPC to AdServices API.
     */
    private ListenableFuture<AdServicesCommonStatesResponse> getAdServicesResponse(
                    @NonNull AdServicesCommonManager adServicesCommonManager) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    adServicesCommonManager.getAdservicesCommonStates(
                            OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                            new AdServicesOutcomeReceiver<AdServicesCommonStatesResponse,
                                    Exception>() {
                                @Override
                                public void onResult(AdServicesCommonStatesResponse result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // For debugging purpose only.
                    return "getAdServicesCommonStates";
                }
        );
    }

    // TODO (b/331684191): remove SecurityException after mocking all UserPrivacyStatus
    private void restorePersonalizationStatus() {
        Context odpContext = OnDevicePersonalizationApplication.getAppContext();
        OnDevicePersonalizationSystemServiceManager systemServiceManager =
                odpContext.getSystemService(OnDevicePersonalizationSystemServiceManager.class);
        if (systemServiceManager != null) {
            IOnDevicePersonalizationSystemService systemService =
                    systemServiceManager.getService();
            if (systemService != null) {
                try {
                    systemService.readPersonalizationStatus(
                            new IOnDevicePersonalizationSystemServiceCallback.Stub() {
                                @Override
                                public void onResult(Bundle bundle) {
                                    boolean personalizationStatus =
                                            bundle.getBoolean(PERSONALIZATION_STATUS_KEY);
                                    setPersonalizationStatusEnabled(personalizationStatus);
                                }

                                @Override
                                public void onError(int errorCode) {
                                    if (errorCode == Constants.STATUS_KEY_NOT_FOUND) {
                                        sLogger.d(
                                                TAG
                                                        + ": Personalization status "
                                                        + "not found in the system server");
                                    }
                                }
                            });
                } catch (Exception e) {
                    sLogger.e(TAG + ": Error when reading personalization status.", e);
                }
            } else {
                sLogger.w(TAG + ": System service is not ready.");
            }
        } else {
            sLogger.w(TAG + ": Cannot find system server on U+ devices.");
        }
    }
}
