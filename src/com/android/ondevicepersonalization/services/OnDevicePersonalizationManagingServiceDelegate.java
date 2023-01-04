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
import android.content.Context;
import android.content.pm.PackageManager;
import android.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import com.android.ondevicepersonalization.services.request.AppRequestFlow;

import java.util.Objects;

/** Implementation of OnDevicePersonalizationManagingService */
public class OnDevicePersonalizationManagingServiceDelegate
        extends IOnDevicePersonalizationManagingService.Stub {
    @NonNull private final Context mContext;

    public OnDevicePersonalizationManagingServiceDelegate(@NonNull Context context) {
        mContext = context;
    }

    @Override
    public String getVersion() {
        return "1.0";
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
        Objects.requireNonNull(callingPackageName);
        Objects.requireNonNull(exchangePackageName);
        Objects.requireNonNull(hostToken);
        Objects.requireNonNull(params);
        Objects.requireNonNull(callback);

        final int uid = Binder.getCallingUid();
        enforceCallingPackageBelongsToUid(callingPackageName, uid);

        AppRequestFlow flow = new AppRequestFlow(
                callingPackageName,
                exchangePackageName,
                hostToken,
                displayId,
                width,
                height,
                params,
                callback,
                OnDevicePersonalizationExecutors.getBackgroundExecutor(),
                mContext);
        flow.run();
    }

    private void enforceCallingPackageBelongsToUid(@NonNull String packageName, int uid) {
        int packageUid;
        PackageManager pm = mContext.getPackageManager();
        try {
            packageUid = pm.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(packageName + " not found");
        }
        if (packageUid != uid) {
            throw new SecurityException(packageName + " does not belong to uid " + uid);
        }
        //TODO(b/242792629): Handle requests from the SDK sandbox.
    }
}
