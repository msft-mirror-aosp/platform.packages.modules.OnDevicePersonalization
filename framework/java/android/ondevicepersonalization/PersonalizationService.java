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

package android.ondevicepersonalization;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IPersonalizationService;
import android.ondevicepersonalization.aidl.IPersonalizationServiceCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Base class for services that produce personalized content based on User data. These platform
 * runs the service in an isolated process and manages its access to user data.
 *
 * @hide
 */
public abstract class PersonalizationService extends Service {
    private static final String TAG = "PersonalizationService";
    private IBinder mBinder;

    /**
     * Callback to return results of incoming requests.
     *
     * @hide
     */
    public static class AppRequestCallback {
        IPersonalizationServiceCallback mWrappedCallback;

        /** @hide */
        AppRequestCallback(IPersonalizationServiceCallback wrappedCallback) {
            mWrappedCallback = Objects.requireNonNull(wrappedCallback);
        }

        // TODO(b/228200518): Replace Parcelable with strongly typed params.
        /** Return the result of a successful request. */
        public void onSuccess(Parcelable result) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.EXTRA_RESULT, result);
            try {
                mWrappedCallback.onSuccess(bundle);
            } catch (RemoteException e) {
                Log.w(TAG, "Callback failed.", e);
            }
        }

        /** Error */
        public void onError() {
            try {
                mWrappedCallback.onError(Constants.STATUS_INTERNAL_ERROR);
            } catch (RemoteException e) {
                Log.w(TAG, "Callback failed.", e);
            }
        }
    }

    /**
     * Callback to signal completion of download post-processing.
     *
     * @hide
     */
    public static class DownloadCallback {
        IPersonalizationServiceCallback mWrappedCallback;

        /** @hide */
        DownloadCallback(IPersonalizationServiceCallback wrappedCallback) {
            mWrappedCallback = Objects.requireNonNull(wrappedCallback);
        }

        /** Retains the provided keys */
        public void onSuccess(ArrayList<String> keysToRetain) {
            Bundle bundle = new Bundle();
            bundle.putStringArrayList(Constants.EXTRA_RESULT, keysToRetain);
            try {
                mWrappedCallback.onSuccess(bundle);
            } catch (RemoteException e) {
                Log.w(TAG, "Callback failed.", e);
            }
        }

        /** Error in download processing. The platform will retry the download. */
        public void onError() {
            try {
                mWrappedCallback.onError(Constants.STATUS_INTERNAL_ERROR);
            } catch (RemoteException e) {
                Log.w(TAG, "Callback failed.", e);
            }
        }
    }

    @Override public void onCreate() {
        mBinder = new ServiceBinder();
    }

    @Override public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Handle a request from an app. A {@link PersonalizationService} that
     * processes requests from apps must override this method.
     *
     * @param appPackageName Package name of the calling app.
     * @param appParams Parameters provided by the calling app.
     * @param odpContext The per-request state for this request.
     * @param callback Callback to be invoked on completion.
     */
    public void onAppRequest(
            @NonNull String appPackageName,
            @Nullable PersistableBundle appParams,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull AppRequestCallback callback
    ) {
        callback.onError();
    }

    /**
     * Handle a completed download. The platform downloads content using the
     * parameters defined in the package manifest of the {@link PersonalizationService}
     * and calls this function after the download is complete.
     *
     * @param fd A file descriptor to read the downloaded content.
     * @param odpContext The per-request state for this request.
     * @param callback Callback to be invoked on completion.
     */
    public void onDownload(
            @NonNull ParcelFileDescriptor fd,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull DownloadCallback callback
    ) {
        callback.onError();
    }

    // TODO(b/228200518): Add onBidRequest() and render() methods.

    class ServiceBinder extends IPersonalizationService.Stub {
        @Override public void onRequest(
                int operationCode,
                @NonNull Bundle params,
                @NonNull IPersonalizationServiceCallback callback) {
            Objects.requireNonNull(params);
            Objects.requireNonNull(callback);
            // TODO(b/228200518): Ensure that caller is ODP Service.

            if (operationCode == Constants.OP_APP_REQUEST) {

                String appPackageName = Objects.requireNonNull(
                        params.getString(Constants.EXTRA_APP_NAME));
                PersistableBundle appParams =
                        (PersistableBundle) params.getParcelable(Constants.EXTRA_APP_PARAMS);
                IDataAccessService binder =
                        (IDataAccessService) Objects.requireNonNull(
                                params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                PersonalizationService.this.onAppRequest(
                        appPackageName, appParams, odpContext, new AppRequestCallback(callback));

            } else if (operationCode == Constants.OP_DOWNLOAD_FINISHED) {
                ParcelFileDescriptor fd = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_PARCEL_FD));
                IDataAccessService binder = (IDataAccessService) Objects.requireNonNull(
                        params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                PersonalizationService.this.onDownload(
                        fd, odpContext, new DownloadCallback(callback));
            } else {
                throw new IllegalArgumentException("Invalid op code: " + operationCode);
            }
        }
    }
}
