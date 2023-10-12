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

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.MODIFY_ONDEVICEPERSONALIZATION_STATE;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigService;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigServiceCallback;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.user.RawUserData;
import com.android.ondevicepersonalization.services.data.user.UserDataCollector;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * ODP service that modifies and persists ODP enablement status
 */
public class OnDevicePersonalizationConfigServiceDelegate
        extends IOnDevicePersonalizationConfigService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationConfigServiceDelegate";
    private final Context mContext;
    private static final Executor sBackgroundExecutor =
            OnDevicePersonalizationExecutors.getBackgroundExecutor();

    public OnDevicePersonalizationConfigServiceDelegate(Context context) {
        mContext = context;
    }

    @Override
    @RequiresPermission(MODIFY_ONDEVICEPERSONALIZATION_STATE)
    public void setPersonalizationStatus(boolean enabled,
                                     @NonNull IOnDevicePersonalizationConfigServiceCallback
                                             callback) {
        if (!isOnDevicePersonalizationApisEnabled()) {
            throw new IllegalStateException("Service skipped as the API flag is turned off.");
        }
        // Verify caller's permission
        OnDevicePersonalizationPermissions.enforceCallingPermission(mContext,
                MODIFY_ONDEVICEPERSONALIZATION_STATE);
        Objects.requireNonNull(callback);
        // TODO(b/270468742): Call system server for U+ devices
        sBackgroundExecutor.execute(
                () -> {
                    try {
                        UserPrivacyStatus userPrivacyStatus = UserPrivacyStatus.getInstance();

                        boolean oldStatus = userPrivacyStatus.isPersonalizationStatusEnabled();
                        userPrivacyStatus.setPersonalizationStatusEnabled(enabled);
                        boolean newStatus = userPrivacyStatus.isPersonalizationStatusEnabled();

                        if (oldStatus == newStatus) {
                            callback.onSuccess();
                            return;
                        }

                        // Rollback all user data if personalization status changes
                        RawUserData userData = RawUserData.getInstance();
                        UserDataCollector userDataCollector =
                                UserDataCollector.getInstance(mContext);
                        userDataCollector.clearUserData(userData);
                        userDataCollector.clearMetadata();
                        userDataCollector.clearDatabase();

                        callback.onSuccess();
                    } catch (RemoteException re) {
                        sLogger.e(TAG + ": Unable to send result to the callback.", re);
                    }
                }
        );
    }

    private boolean isOnDevicePersonalizationApisEnabled() {
        long origId = Binder.clearCallingIdentity();
        boolean isOnDevicePersonalizationApisEnabled =
                        FlagsFactory.getFlags().isOnDevicePersonalizationApisEnabled();
        Binder.restoreCallingIdentity(origId);
        return isOnDevicePersonalizationApisEnabled;
    }
}
