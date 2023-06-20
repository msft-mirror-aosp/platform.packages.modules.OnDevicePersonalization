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
import android.ondevicepersonalization.aidl.IIsolatedComputationService;
import android.ondevicepersonalization.aidl.IIsolatedComputationServiceCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base class for services that produce personalized content based on User data. These platform
 * runs the service in an isolated process and manages its access to user data.
 *
 * @hide
 */
public abstract class IsolatedComputationService extends Service {
    private static final String TAG = "IsolatedComputationService";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private IBinder mBinder;

    @Override public void onCreate() {
        mBinder = new ServiceBinder();
    }

    @Override public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Return an instance of {@link IsolatedComputationCallback} that handles client requests.
     */
    @NonNull public abstract IsolatedComputationCallback createCallback(RequestToken requestToken);

    /**
     * Returns a DAO for the REMOTE_DATA table.
     * @return A {@link KeyValueStore} object that provides access to the REMOTE_DATA table.
     */
    @NonNull public KeyValueStore getRemoteData(RequestToken requestToken) {
        return new RemoteDataImpl(requestToken.getDataAccessService());
    }

    /**
     * Returns a DAO for the LOCAL_DATA table.
     * @return A {@link MutableKeyValueStore} object that provides access to the LOCAL_DATA table.
     */
    @NonNull public MutableKeyValueStore getLocalData(RequestToken requestToken) {
        return new LocalDataImpl(requestToken.getDataAccessService());
    }

    /** Returns an {@link EventUrlProvider} for the current request. */
    @NonNull public EventUrlProvider getEventUrlProvider(RequestToken requestToken) {
        return new EventUrlProvider(requestToken.getDataAccessService());
    }

    /** Returns the most recent {@link UserData}. */
    @Nullable public UserData getUserData(RequestToken requestToken) {
        return requestToken.getUserData();
    }

    // TODO(b/228200518): Add onBidRequest()/onBidResponse() methods.

    class ServiceBinder extends IIsolatedComputationService.Stub {
        @Override public void onRequest(
                int operationCode,
                @NonNull Bundle params,
                @NonNull IIsolatedComputationServiceCallback resultCallback) {
            Objects.requireNonNull(params);
            Objects.requireNonNull(resultCallback);
            // TODO(b/228200518): Ensure that caller is ODP Service.

            if (operationCode == Constants.OP_EXECUTE) {

                ExecuteInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, ExecuteInput.class));
                Objects.requireNonNull(input.getAppPackageName());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                UserData userData = params.getParcelable(
                        Constants.EXTRA_USER_DATA, UserData.class);
                RequestToken requestToken = new RequestToken(binder, userData);
                IsolatedComputationCallback implCallback = createCallback(requestToken);
                implCallback.onExecute(
                        input, new WrappedCallback<ExecuteOutput>(resultCallback));

            } else if (operationCode == Constants.OP_DOWNLOAD) {

                DownloadInputParcel input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, DownloadInputParcel.class));

                List<String> keys = Objects.requireNonNull(input.getDownloadedKeys()).getList();
                List<byte[]> values = Objects.requireNonNull(input.getDownloadedValues()).getList();
                if (keys.size() != values.size()) {
                    throw new IllegalArgumentException(
                            "Mismatching key and value list sizes of "
                                    + keys.size() + " and " + values.size());
                }

                HashMap<String, byte[]> downloadData = new HashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    downloadData.put(keys.get(i), values.get(i));
                }
                DownloadInput downloadInput = new DownloadInput.Builder()
                        .setData(downloadData)
                        .build();

                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                UserData userData = params.getParcelable(
                        Constants.EXTRA_USER_DATA, UserData.class);
                RequestToken requestToken = new RequestToken(binder, userData);
                IsolatedComputationCallback implCallback = createCallback(requestToken);
                implCallback.onDownload(
                        downloadInput, new WrappedCallback<DownloadOutput>(resultCallback));

            } else if (operationCode == Constants.OP_RENDER) {

                RenderInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, RenderInput.class));
                Objects.requireNonNull(input.getRenderingData());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                RequestToken requestToken = new RequestToken(binder, null);
                IsolatedComputationCallback implCallback = createCallback(requestToken);
                implCallback.onRender(
                        input, new WrappedCallback<RenderOutput>(resultCallback));

            } else if (operationCode == Constants.OP_WEB_VIEW_EVENT) {

                WebViewEventInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, WebViewEventInput.class));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                UserData userData = params.getParcelable(
                        Constants.EXTRA_USER_DATA, UserData.class);
                RequestToken requestToken = new RequestToken(binder, userData);
                IsolatedComputationCallback implCallback = createCallback(requestToken);
                implCallback.onWebViewEvent(
                        input, new WrappedCallback<WebViewEventOutput>(resultCallback));

            } else {
                throw new IllegalArgumentException("Invalid op code: " + operationCode);
            }
        }
    }

    private static class WrappedCallback<T extends Parcelable> implements Consumer<T> {
        @NonNull private final IIsolatedComputationServiceCallback mCallback;
        WrappedCallback(IIsolatedComputationServiceCallback callback) {
            mCallback = Objects.requireNonNull(callback);
        }

        @Override public void accept(T result) {
            if (result == null) {
                try {
                    mCallback.onError(Constants.STATUS_INTERNAL_ERROR);
                } catch (RemoteException e) {
                    sLogger.w(TAG + ": Callback failed.", e);
                }
            } else {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.EXTRA_RESULT, result);
                try {
                    mCallback.onSuccess(bundle);
                } catch (RemoteException e) {
                    sLogger.w(TAG + ": Callback failed.", e);
                }
            }
        }
    }
}
