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

package com.android.ondevicepersonalization.services.request;

import android.annotation.NonNull;
import android.ondevicepersonalization.Constants;
import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;

/**
 * Handles a surface package request from an app or SDK.
 */
public class AppRequestFlow {
    public static final String TAG = "OdpService";

    @NonNull private final String mCallingPackageName;
    @NonNull private final String mExchangePackageName;
    @NonNull private final IBinder mHostToken;
    @NonNull private final int mDisplayId;
    @NonNull private final int mWidth;
    @NonNull private final int mHeight;
    @NonNull private final Bundle mParams;
    private IRequestSurfacePackageCallback mCallback;
    @NonNull private final ListeningExecutorService mExecutorService;

    public AppRequestFlow(
            @NonNull String callingPackageName,
            @NonNull String exchangePackageName,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull Bundle params,
            @NonNull IRequestSurfacePackageCallback callback,
            @NonNull ListeningExecutorService executorService) {
        mCallingPackageName = Objects.requireNonNull(callingPackageName);
        mExchangePackageName = Objects.requireNonNull(exchangePackageName);
        mHostToken = Objects.requireNonNull(hostToken);
        mDisplayId = displayId;
        mWidth = width;
        mHeight = height;
        mParams = Objects.requireNonNull(params);
        mCallback = Objects.requireNonNull(callback);
        mExecutorService = Objects.requireNonNull(executorService);
    }

    /** Runs the request processing flow. */
    public void run() {
        var unused = Futures.submit(() -> this.processRequest(), mExecutorService);
    }

    private void processRequest() {
        // TODO(b/228200518): Implement request processing and rendering logic.
        sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
    }

    private void sendErrorResult(int errorCode) {
        try {
            IRequestSurfacePackageCallback callbackCopy = null;
            synchronized (this) {
                if (mCallback != null) {
                    callbackCopy = mCallback;
                    mCallback = null;
                }
            }
            if (callbackCopy != null) {
                callbackCopy.onError(errorCode);
            } else {
                Log.w(TAG, "Callback null. Result already sent.");
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Callback error: " + e.toString());
        }
    }
}
