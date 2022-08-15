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

package com.android.ondevicepersonalization.services;

import android.annotation.NonNull;
import android.app.Service;
import android.content.Intent;
import android.ondevicepersonalization.OnDevicePersonalizationManager;
import android.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

/** Implementation of OnDevicePersonalization Service */
public class OnDevicePersonalizationManagingServiceImpl extends Service {
    private IOnDevicePersonalizationManagingService.Stub mBinder;

    @Override
    public void onCreate() {
        mBinder = new OnDevicePersonalizationManagingServiceDelegate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    String getVersion() {
        return "1.0";
    }

    void requestSurfacePackage(
            @NonNull String callingPackageName,
            @NonNull String exchangePackageName,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull Bundle params,
            @NonNull IRequestSurfacePackageCallback callback) {
        try {
            callback.onError(OnDevicePersonalizationManager.STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
    }

    final class OnDevicePersonalizationManagingServiceDelegate
            extends IOnDevicePersonalizationManagingService.Stub {
        @Override
        public String getVersion() {
            return OnDevicePersonalizationManagingServiceImpl.this.getVersion();
        }

        @Override
        public void requestSurfacePackage(
                @NonNull String callingPackageName,
                @NonNull String exchangePackageName,
                @NonNull IBinder hostToken,
                int displayId,
                int width,
                int height,
                @NonNull Bundle params,
                @NonNull IRequestSurfacePackageCallback callback) {
            OnDevicePersonalizationManagingServiceImpl.this
                    .requestSurfacePackage(
                            callingPackageName,
                            exchangePackageName,
                            hostToken,
                            displayId,
                            width,
                            height,
                            params,
                            callback);
        }
    }
}
