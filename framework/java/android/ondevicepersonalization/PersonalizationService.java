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
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
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
    public interface AppRequestCallback {
        /** Return the result of a successful request. */
        void onSuccess(AppRequestResult result);

        /** Error */
        void onError();
    }

    /**
     * Callback to signal completion of download post-processing.
     *
     * @hide
     */
    public interface DownloadCallback {
        /** Retains the provided keys */
        void onSuccess(DownloadResult downloadResult);

        /** Error in download processing. The platform will retry the download. */
        void onError();
    }

    /**
     * Callback to signal the completion of a render request.
     */
    public interface RenderContentCallback {
        /** Provides the result of a successful render request. */
        void onSuccess(RenderContentResult result);

        /** Error in rendering. */
        void onError();
    }

    /**
     * Callback to signal the completion of event metrics computation.
     */
    public interface EventMetricsCallback {
        /** Provides the computed event metrics */
        void onSuccess(EventMetricsResult result);

        /** Error in computing event metrics. */
        void onError();
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

    /**
     * Generate HTML for the winning bids that returned as a result of {@link onAppRequest}.
     * The platform will render this HTML in a WebView inside a fenced frame.
     *
     * @param slotInfo Properties of the slot to be rendered in.
     * @param bidIds A List of Bid Ids to be rendered
     * @param odpContext The per-request state for this request.
     * @param callback Callback to be invoked on completion.
     */
    public void renderContent(
            @NonNull SlotInfo slotInfo,
            @NonNull List<String> bidIds,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull RenderContentCallback callback
    ) {
        callback.onError();
    }

    /**
     * Compute a list of metrics to be logged in the events table with this event.
     *
     * @param input The query-time data required to compute the event metrics.
     * @param odpContext The per-request state for this request.
     * @param callback Callback to be invoked on completion.
     */
    // TODO(b/259950177): Also provide the Query event from the Query table.
    public void computeEventMetrics(
            @NonNull EventMetricsInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull EventMetricsCallback callback
    ) {
        callback.onError();
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

                String appPackageName = Objects.requireNonNull(
                        params.getString(Constants.EXTRA_APP_NAME));
                PersistableBundle appParams = params.getParcelable(
                        Constants.EXTRA_APP_PARAMS, PersistableBundle.class);
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                var wrappedCallback = new AppRequestCallback() {
                    @Override public void onSuccess(AppRequestResult result) {
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(Constants.EXTRA_RESULT, result);
                        try {
                            callback.onSuccess(bundle);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }

                    @Override public void onError() {
                        try {
                            callback.onError(Constants.STATUS_INTERNAL_ERROR);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }
                };
                PersonalizationService.this.onAppRequest(
                        appPackageName, appParams, odpContext, wrappedCallback);

            } else if (operationCode == Constants.OP_DOWNLOAD_FINISHED) {

                ParcelFileDescriptor fd = Objects.requireNonNull(
                        params.getParcelable(
                            Constants.EXTRA_PARCEL_FD, ParcelFileDescriptor.class));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                var wrappedCallback = new DownloadCallback() {
                    @Override public void onSuccess(DownloadResult result) {
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(Constants.EXTRA_RESULT, result);
                        try {
                            callback.onSuccess(bundle);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }

                    @Override public void onError() {
                        try {
                            callback.onError(Constants.STATUS_INTERNAL_ERROR);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }
                };
                PersonalizationService.this.onDownload(
                        fd, odpContext, wrappedCallback);

            } else if (operationCode == Constants.OP_RENDER_CONTENT) {

                SlotInfo slotInfo = Objects.requireNonNull(
                        params.getParcelable(Constants.EXTRA_SLOT_INFO, SlotInfo.class));
                List<String> bidIds = Arrays.asList(Objects.requireNonNull(
                        params.getStringArray(Constants.EXTRA_BID_IDS)));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                var wrappedCallback = new RenderContentCallback() {
                    @Override public void onSuccess(RenderContentResult result) {
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(Constants.EXTRA_RESULT, result);
                        try {
                            callback.onSuccess(bundle);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }

                    @Override public void onError() {
                        try {
                            callback.onError(Constants.STATUS_INTERNAL_ERROR);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }
                };
                PersonalizationService.this.renderContent(
                        slotInfo, bidIds, odpContext, wrappedCallback);

            } else if (operationCode == Constants.OP_COMPUTE_EVENT_METRICS) {

                EventMetricsInput eventMetricsInput = Objects.requireNonNull(
                        params.getParcelable(
                            Constants.EXTRA_EVENT_METRICS_INPUT, EventMetricsInput.class));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(Objects.requireNonNull(
                            params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                OnDevicePersonalizationContext odpContext =
                        new OnDevicePersonalizationContextImpl(binder);
                var wrappedCallback = new EventMetricsCallback() {
                    @Override public void onSuccess(EventMetricsResult result) {
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(Constants.EXTRA_RESULT, result);
                        try {
                            callback.onSuccess(bundle);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }

                    @Override public void onError() {
                        try {
                            callback.onError(Constants.STATUS_INTERNAL_ERROR);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Callback failed.", e);
                        }
                    }
                };
                PersonalizationService.this.computeEventMetrics(
                        eventMetricsInput, odpContext, wrappedCallback);

            } else {
                throw new IllegalArgumentException("Invalid op code: " + operationCode);
            }
        }
    }
}
