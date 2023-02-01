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
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

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
     * @param input App Request Parameters.
     * @param odpContext The per-request state for this request.
     * @param consumer Callback to be invoked on completion.
     */
    public void onAppRequest(
            @NonNull AppRequestInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<AppRequestResult> consumer
    ) {
        consumer.accept(null);
    }

    /**
     * Handle a completed download. The platform downloads content using the
     * parameters defined in the package manifest of the {@link PersonalizationService}
     * and calls this function after the download is complete.
     *
     * @param input Download handler parameters.
     * @param odpContext The per-request state for this request.
     * @param consumer Callback to be invoked on completion.
     */
    public void onDownload(
            @NonNull DownloadInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<DownloadResult> consumer
    ) {
        consumer.accept(null);
    }

    /**
     * Generate HTML for the winning bids that returned as a result of {@link onAppRequest}.
     * The platform will render this HTML in a WebView inside a fenced frame.
     *
     * @param input Parameters for the renderContent request.
     * @param bidIds A List of Bid Ids to be rendered
     * @param odpContext The per-request state for this request.
     * @param consumer Callback to be invoked on completion.
     */
    public void renderContent(
            @NonNull RenderContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<RenderContentResult> consumer
    ) {
        consumer.accept(null);
    }

    /**
     * Compute a list of metrics to be logged in the events table with this event.
     *
     * @param input The query-time data required to compute the event metrics.
     * @param odpContext The per-request state for this request.
     * @param consumer Callback to be invoked on completion.
     */
    // TODO(b/259950177): Also provide the Query event from the Query table.
    public void computeEventMetrics(
            @NonNull EventMetricsInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<EventMetricsResult> consumer
    ) {
        consumer.accept(null);
    }

    // TODO(b/228200518): Add onBidRequest()/onBidResponse() methods.

    class ServiceBinder extends IPersonalizationService.Stub {
        @Override public void onRequest(
                int operationCode,
                @NonNull Bundle params,
                @NonNull IPersonalizationServiceCallback callback) {
            Objects.requireNonNull(params);
            Objects.requireNonNull(callback);
            // TODO(b/228200518): Ensure that caller is ODP Service.

            if (operationCode == Constants.OP_APP_REQUEST) {

                AppRequestInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, AppRequestInput.class));
                Objects.requireNonNull(input.getAppPackageName());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                PersonalizationService.this.onAppRequest(
                        input, odpContext, new WrappedCallback<AppRequestResult>(callback));

            } else if (operationCode == Constants.OP_DOWNLOAD_FINISHED) {

                DownloadInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, DownloadInput.class));
                Objects.requireNonNull(input.getParcelFileDescriptor());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                PersonalizationService.this.onDownload(
                        input, odpContext, new WrappedCallback<DownloadResult>(callback));

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
                PersonalizationService.this.renderContent(
                        input, odpContext, new WrappedCallback<RenderContentResult>(callback));

            } else if (operationCode == Constants.OP_COMPUTE_EVENT_METRICS) {

                EventMetricsInput input = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_INPUT, EventMetricsInput.class));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                PersonalizationService.this.computeEventMetrics(
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
