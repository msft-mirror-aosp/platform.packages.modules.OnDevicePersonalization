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
package android.adservices.ondevicepersonalization;

import android.annotation.Nullable;

/**
 * The response of {@link OnDevicePersonalizationManager#executeInIsolatedService}.
 *
 * @hide
 */
public class ExecuteInIsolatedServiceResponse {
    /**
     * An opaque reference to content that can be displayed in a {@link android.view.SurfaceView}.
     * This may be null if the {@link IsolatedService} has not generated any content to be displayed
     * within the calling app.
     */
    @Nullable private final SurfacePackageToken mSurfacePackageToken;

    /**
     * The int value that was returned by the {@link IsolatedService} and applied noise. If {@link
     * IsolatedService} didn't return any content, the default value is -1. If {@link
     * IsolatedService} returns an integer value, we will apply the noise to the value and the range
     * of this value is between 0 and {@link
     * ExecuteInIsolatedServiceRequest.OutputParams#getMaxIntValue()}.
     */
    private int mBestValue = -1;

    public ExecuteInIsolatedServiceResponse(
            @Nullable SurfacePackageToken surfacePackageToken, int bestValue) {
        mSurfacePackageToken = surfacePackageToken;
        mBestValue = bestValue;
    }

    /** @hide */
    public ExecuteInIsolatedServiceResponse(@Nullable SurfacePackageToken surfacePackageToken) {
        mSurfacePackageToken = surfacePackageToken;
    }

    /**
     * Returns a {@link SurfacePackageToken}, which is an opaque reference to content that can be
     * displayed in a {@link android.view.SurfaceView}. This may be null if the {@link
     * IsolatedService} has not generated any content to be displayed within the calling app.
     */
    @Nullable
    public SurfacePackageToken getSurfacePackageToken() {
        return mSurfacePackageToken;
    }

    /**
     * Returns the int value that was returned by the {@link IsolatedService} and applied noise. If
     * {@link IsolatedService} didn't return any content, the default value is -1. If {@link
     * IsolatedService} returns an integer value, we will apply the noise to the value and the range
     * of this value is between 0 and {@link
     * ExecuteInIsolatedServiceRequest.OutputParams#getMaxIntValue()}.
     */
    public int getBestValue() {
        return mBestValue;
    }
}
