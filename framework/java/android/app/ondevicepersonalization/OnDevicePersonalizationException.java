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

package android.app.ondevicepersonalization;

import android.annotation.NonNull;

/**
 * Exception thrown by OnDevicePersonalization APIs.
 *
 * @hide
 */
public class OnDevicePersonalizationException extends Exception {
    private final int mErrorCode;

    public OnDevicePersonalizationException(int errorCode) {
        this(errorCode, "");
    }

    public OnDevicePersonalizationException(int errorCode, @NonNull String errorMessage) {
        super("Error code: " + errorCode + " message: " + errorMessage);
        mErrorCode = errorCode;
    }

    public OnDevicePersonalizationException(int errorCode, @NonNull Throwable cause) {
        this(errorCode, "", cause);
    }

    public OnDevicePersonalizationException(
            int errorCode, @NonNull String errorMessage, @NonNull Throwable cause) {
        super("Error code: " + errorCode + " message: " + errorMessage, cause);
        mErrorCode = errorCode;
    }

    public int getErrorCode() {
        return mErrorCode;
    }
}
