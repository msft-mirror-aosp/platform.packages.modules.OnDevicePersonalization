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
import android.app.Service;
import android.content.Intent;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IPersonalizationService;
import android.ondevicepersonalization.aidl.IPersonalizationServiceCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

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
public abstract class PersonalizationService extends Service {
    private static final String TAG = "PersonalizationService";
    private IBinder mBinder;
    @NonNull private final PersonalizationHandler mHandler = getHandler();

    @Override public void onCreate() {
        mBinder = new ServiceBinder();
    }

    @Override public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Return an instance of {@link PersonalizationHandler} that handles client requests.
     */
    @NonNull public abstract PersonalizationHandler getHandler();

    // TODO(b/228200518): Add onBidRequest()/onBidResponse() methods.

    class ServiceBinder extends IPersonalizationService.Stub {
        @Override public void onRequest(
                int operationCode,
                @NonNull Bundle params,
                @NonNull IPersonalizationServiceCallback callback) {
            Objects.requireNonNull(params);
            Objects.requireNonNull(callback);
            // TODO(b/228200518): Ensure that caller is ODP Service.

            if (operationCode == Constants.OP_SELECT_CONTENT) {

                SelectContentInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, SelectContentInput.class));
                Objects.requireNonNull(input.getAppPackageName());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                mHandler.selectContent(
                        input, odpContext, new WrappedCallback<SelectContentResult>(callback));

            } else if (operationCode == Constants.OP_DOWNLOAD_FINISHED) {

                DownloadInputParcel input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, DownloadInputParcel.class));
                ParcelFileDescriptor fd = Objects.requireNonNull(input.getParcelFileDescriptor());

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
                        .setParcelFileDescriptor(fd)
                        .build();

                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                mHandler.onDownload(
                        downloadInput, odpContext, new WrappedCallback<DownloadResult>(callback));

            } else if (operationCode == Constants.OP_RENDER_CONTENT) {

                RenderContentInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, RenderContentInput.class));
                Objects.requireNonNull(input.getSlotInfo());
                Objects.requireNonNull(input.getBidIds());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                mHandler.renderContent(
                        input, odpContext, new WrappedCallback<RenderContentResult>(callback));

            } else if (operationCode == Constants.OP_COMPUTE_EVENT_METRICS) {

                EventMetricsInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, EventMetricsInput.class));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                mHandler.computeEventMetrics(
                        input, odpContext, new WrappedCallback<EventMetricsResult>(callback));

            } else {
                throw new IllegalArgumentException("Invalid op code: " + operationCode);
            }
        }
    }

    private static class WrappedCallback<T extends Parcelable> implements Consumer<T> {
        @NonNull private final IPersonalizationServiceCallback mCallback;
        WrappedCallback(IPersonalizationServiceCallback callback) {
            mCallback = Objects.requireNonNull(callback);
        }

        @Override public void accept(T result) {
            if (result == null) {
                try {
                    mCallback.onError(Constants.STATUS_INTERNAL_ERROR);
                } catch (RemoteException e) {
                    Log.w(TAG, "Callback failed.", e);
                }
            } else {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.EXTRA_RESULT, result);
                try {
                    mCallback.onSuccess(bundle);
                } catch (RemoteException e) {
                    Log.w(TAG, "Callback failed.", e);
                }
            }
        }
    }
}
