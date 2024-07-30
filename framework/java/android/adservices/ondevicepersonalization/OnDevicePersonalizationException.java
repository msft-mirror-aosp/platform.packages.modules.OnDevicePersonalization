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

package android.adservices.ondevicepersonalization;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;

import com.android.adservices.ondevicepersonalization.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Exception thrown by OnDevicePersonalization APIs.
 *
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
public class OnDevicePersonalizationException extends Exception {
    /**
     * The {@link IsolatedService} that was invoked failed to run.
     */
    public static final int ERROR_ISOLATED_SERVICE_FAILED = 1;

    /**
     * The {@link IsolatedService} was not started because personalization is disabled by
     * device configuration.
     */
    public static final int ERROR_PERSONALIZATION_DISABLED = 2;

    /**
     * The ODP module was unable to load the {@link IsolatedService}.
     * @hide
     */
    public static final int  ERROR_ISOLATED_SERVICE_LOADING_FAILED = 3;

    /**
     * The ODP specific manifest settings for the {@link IsolatedService} are either missing or
     * misconfigured.
     * @hide
     */
    public static final int ERROR_ISOLATED_SERVICE_MANIFEST_PARSING_FAILED = 4;

    /**
     * The {@link IsolatedService} was invoked but timed out before returning successfully.
     * @hide
     */
    public static final int ERROR_ISOLATED_SERVICE_TIMEOUT = 5;

    /**
     * The {@link IsolatedService}'s call to {@link FederatedComputeScheduler#schedule} failed.
     *
     * @hide
     */
    public static final int ERROR_ISOLATED_SERVICE_FAILED_TRAINING = 6;

    /** @hide */
    @IntDef(prefix = "ERROR_", value = {
            ERROR_ISOLATED_SERVICE_FAILED,
            ERROR_PERSONALIZATION_DISABLED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorCode {}

    private final @ErrorCode int mErrorCode;

    /** @hide */
    public OnDevicePersonalizationException(@ErrorCode int errorCode) {
        mErrorCode = errorCode;
    }

    /** @hide */
    public OnDevicePersonalizationException(
            @ErrorCode int errorCode, String message) {
        super(message);
        mErrorCode = errorCode;
    }

    /** @hide */
    public OnDevicePersonalizationException(
            @ErrorCode int errorCode, Throwable cause) {
        super(cause);
        mErrorCode = errorCode;
    }

    /** @hide */
    public OnDevicePersonalizationException(
            @ErrorCode int errorCode, String message, Throwable cause) {
        super(message, cause);
        mErrorCode = errorCode;
    }

    /** Returns the error code for this exception. */
    public @ErrorCode int getErrorCode() {
        return mErrorCode;
    }
}
