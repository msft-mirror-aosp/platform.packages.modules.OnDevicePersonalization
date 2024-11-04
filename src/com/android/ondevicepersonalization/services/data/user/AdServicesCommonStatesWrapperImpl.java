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

package com.android.ondevicepersonalization.services.data.user;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStates;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Binder;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper for the AdServicesCommonStates API. Used by UserPrivacyStatus to
 * fetch common states from AdServices.
 */
class AdServicesCommonStatesWrapperImpl implements AdServicesCommonStatesWrapper {
    private static final String TAG = AdServicesCommonStatesWrapperImpl.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private final Context mContext;

    AdServicesCommonStatesWrapperImpl(Context context) {
        mContext = Objects.requireNonNull(context);
    }

    @Override public ListenableFuture<CommonStatesResult> getCommonStates() {
        try {
            AdServicesCommonManager manager = getAdServicesCommonManager();
            if (manager == null) {
                throw new NullAdServiceCommonManagerException();
            }
            sLogger.d(TAG + ": IPC getAdServicesCommonStates() started");
            long origId = Binder.clearCallingIdentity();
            long timeoutInMillis = FlagsFactory.getFlags().getAdservicesIpcCallTimeoutInMillis();
            Binder.restoreCallingIdentity(origId);
            ListenableFuture<AdServicesCommonStatesResponse> futureWithTimeout =
                    Futures.withTimeout(
                            getAdServicesResponse(manager),
                            timeoutInMillis,
                            TimeUnit.MILLISECONDS,
                            OnDevicePersonalizationExecutors.getScheduledExecutor());

            return FluentFuture.from(futureWithTimeout)
                    .transform(
                            v -> getResultFromResponse(v),
                            MoreExecutors.newDirectExecutorService());
        } catch (Throwable e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private AdServicesCommonManager getAdServicesCommonManager() throws NoClassDefFoundError {
        return mContext.getSystemService(AdServicesCommonManager.class);
    }

    private static CommonStatesResult getResultFromResponse(
            AdServicesCommonStatesResponse response) {
        AdServicesCommonStates commonStates = response.getAdServicesCommonStates();
        return new CommonStatesResult(
                commonStates.getPaState(), commonStates.getMeasurementState());
    }

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
                                    sLogger.d(
                                            TAG + ": IPC getAdServicesCommonStates() success");
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    sLogger.e(error,
                                            TAG + ": IPC getAdServicesCommonStates() error");
                                    completer.setException(error);
                                }
                            });
                    // For debugging purpose only.
                    return "getAdServicesCommonStates";
                }
        );
    }
}
