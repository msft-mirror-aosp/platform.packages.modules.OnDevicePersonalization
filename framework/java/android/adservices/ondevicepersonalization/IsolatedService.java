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

package android.adservices.ondevicepersonalization;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

// TODO(b/289102463): Add a link to the public ODP developer documentation.
/**
 * Base class for services that are started by ODP on a call to {@link
 * OnDevicePersonalizationManager#execute} and run in an <a
 * href="https://developer.android.com/guide/topics/manifest/service-element#isolated">isolated
 * process</a>. The service can produce content to be displayed in a {@link SurfaceView} in a
 * calling app and write persistent results to on-device storage, which can be consumed by Federated
 * Analytics for cross-device statistical analysis or by Federated Learning for model training.
 * Client apps use {@link OnDevicePersonalizationManager} to interact with an {@link
 * IsolatedService}.
 */
public abstract class IsolatedService extends Service {
    private static final String TAG = "IsolatedService";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private IBinder mBinder;

    /** Creates a binder for an {@link IsolatedService}. */
    @Override
    public void onCreate() {
        mBinder = new ServiceBinder();
    }

    /**
     * Handles binding to the {@link IsolatedService}.
     *
     * @param intent The Intent that was used to bind to this service, as given to {@link
     *     android.content.Context#bindService Context.bindService}. Note that any extras that were
     *     included with the Intent at that point will <em>not</em> be seen here.
     */
    @Override
    @Nullable
    public IBinder onBind(@NonNull Intent intent) {
        return mBinder;
    }

    /**
     * Return an instance of an {@link IsolatedWorker} that handles client requests.
     *
     * @param requestToken an opaque token that identifies the current request to the service that
     *     must be passed to service methods that depend on per-request state.
     */
    @NonNull
    public abstract IsolatedWorker onRequest(@NonNull RequestToken requestToken);

    /**
     * Returns a Data Access Object for the REMOTE_DATA table. The REMOTE_DATA table is a read-only
     * key-value store that contains data that is periodically downloaded from an endpoint declared
     * in the <download> tag in the ODP manifest of the service, as shown in the following example.
     *
     * <pre>{@code
     * <!-- Contents of res/xml/OdpSettings.xml -->
     * <on-device-personalization>
     * <!-- Name of the service subclass -->
     * <service "com.example.odpsample.SampleService">
     *   <!-- If this tag is present, ODP will periodically poll this URL and
     *    download content to populate REMOTE_DATA. Adopters that do not need to
     *    download content from their servers can skip this tag. -->
     *   <download-settings url="https://example.com/get" />
     * </service>
     * </on-device-personalization>
     * }</pre>
     *
     * @param requestToken an opaque token that identifies the current request to the service.
     * @see #onRequest
     * @return A {@link KeyValueStore} object that provides access to the REMOTE_DATA table. The
     *     methods in the returned {@link KeyValueStore} are blocking operations and should be
     *     called from a worker thread and not the main thread or a binder thread.
     */
    @NonNull
    public final KeyValueStore getRemoteData(@NonNull RequestToken requestToken) {
        return new RemoteDataImpl(requestToken.getDataAccessService());
    }

    /**
     * Returns a Data Access Object for the LOCAL_DATA table. The LOCAL_DATA table is a persistent
     * key-value store that the service can use to store any data. The contents of this table are
     * visible only to the service running in an isolated process and cannot be sent outside the
     * device.
     *
     * @param requestToken an opaque token that identifies the current request to the service.
     * @see #onRequest
     * @return A {@link MutableKeyValueStore} object that provides access to the LOCAL_DATA table.
     *     The methods in the returned {@link MutableKeyValueStore} are blocking operations and
     *     should be called from a worker thread and not the main thread or a binder thread.
     */
    @NonNull
    public final MutableKeyValueStore getLocalData(@NonNull RequestToken requestToken) {
        return new LocalDataImpl(requestToken.getDataAccessService());
    }

    /**
     * Returns an {@link EventUrlProvider} for the current request. The {@link EventUrlProvider}
     * provides URLs that can be embedded in HTML. When the HTML is rendered in a {@link WebView},
     * the platform intercepts requests to these URLs and invokes {@link
     * IsolatedCmputationCallback#onWebViewEvent()}.
     *
     * @param requestToken an opaque token that identifies the current request to the service.
     * @see #onRequest
     * @return An {@link EventUrlProvider} that returns event tracking URLs.
     */
    @NonNull
    public final EventUrlProvider getEventUrlProvider(@NonNull RequestToken requestToken) {
        return new EventUrlProvider(requestToken.getDataAccessService());
    }

    /**
     * Returns the platform-provided {@link UserData} for the current request.
     *
     * @param requestToken an opaque token that identifies the current request to the service.
     * @see #onRequest
     * @return A {@link UserData} object.
     */
    @Nullable
    public final UserData getUserData(@NonNull RequestToken requestToken) {
        return requestToken.getUserData();
    }

    /**
     * Returns an {@link FederatedComputeScheduler} for the current request. The {@link
     * FederatedComputeScheduler} can be used to schedule and cancel federated computation jobs. The
     * federated computation includes federated learning and federated analytic jobs.
     *
     * @param requestToken an opaque token that identifies the current request to the service.
     * @see #onRequest
     * @return An {@link FederatedComputeScheduler} that returns a federated computation job
     *     scheduler.
     * @hide
     */
    @NonNull
    public final FederatedComputeScheduler getFederatedComputeScheduler(
            @NonNull RequestToken requestToken) {
        return new FederatedComputeScheduler(requestToken.getFederatedComputeService());
    }

    // TODO(b/228200518): Add onBidRequest()/onBidResponse() methods.

    class ServiceBinder extends IIsolatedService.Stub {
        @Override
        public void onRequest(
                int operationCode,
                @NonNull Bundle params,
                @NonNull IIsolatedServiceCallback resultCallback) {
            Objects.requireNonNull(params);
            Objects.requireNonNull(resultCallback);
            // TODO(b/228200518): Ensure that caller is ODP Service.

            if (operationCode == Constants.OP_EXECUTE) {

                ExecuteInput input =
                        Objects.requireNonNull(
                                params.getParcelable(Constants.EXTRA_INPUT, ExecuteInput.class));
                Objects.requireNonNull(input.getAppPackageName());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(
                                Objects.requireNonNull(
                                        params.getBinder(
                                                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                UserData userData = params.getParcelable(Constants.EXTRA_USER_DATA, UserData.class);
                RequestToken requestToken = new RequestToken(binder, null, userData);
                IsolatedWorker implCallback = IsolatedService.this.onRequest(requestToken);
                implCallback.onExecute(input, new WrappedCallback<ExecuteOutput>(resultCallback));

            } else if (operationCode == Constants.OP_DOWNLOAD) {

                DownloadInputParcel inputParcel =
                        Objects.requireNonNull(
                                params.getParcelable(
                                        Constants.EXTRA_INPUT, DownloadInputParcel.class));

                List<String> keys =
                        Objects.requireNonNull(inputParcel.getDownloadedKeys()).getList();
                List<byte[]> values =
                        Objects.requireNonNull(inputParcel.getDownloadedValues()).getList();
                if (keys.size() != values.size()) {
                    throw new IllegalArgumentException(
                            "Mismatching key and value list sizes of "
                                    + keys.size()
                                    + " and "
                                    + values.size());
                }

                HashMap<String, byte[]> downloadData = new HashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    downloadData.put(keys.get(i), values.get(i));
                }
                DownloadCompletedInput input =
                        new DownloadCompletedInput.Builder().setData(downloadData).build();

                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(
                                Objects.requireNonNull(
                                        params.getBinder(
                                                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                UserData userData = params.getParcelable(Constants.EXTRA_USER_DATA, UserData.class);
                RequestToken requestToken = new RequestToken(binder, null, userData);
                IsolatedWorker implCallback = IsolatedService.this.onRequest(requestToken);
                implCallback.onDownloadCompleted(
                        input, new WrappedCallback<DownloadCompletedOutput>(resultCallback));

            } else if (operationCode == Constants.OP_RENDER) {

                RenderInput input =
                        Objects.requireNonNull(
                                params.getParcelable(Constants.EXTRA_INPUT, RenderInput.class));
                Objects.requireNonNull(input.getRenderingConfig());
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(
                                Objects.requireNonNull(
                                        params.getBinder(
                                                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                RequestToken requestToken = new RequestToken(binder, null, null);
                IsolatedWorker implCallback = IsolatedService.this.onRequest(requestToken);
                implCallback.onRender(input, new WrappedCallback<RenderOutput>(resultCallback));

            } else if (operationCode == Constants.OP_WEB_VIEW_EVENT) {

                WebViewEventInput input =
                        Objects.requireNonNull(
                                params.getParcelable(
                                        Constants.EXTRA_INPUT, WebViewEventInput.class));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(
                                Objects.requireNonNull(
                                        params.getBinder(
                                                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                UserData userData = params.getParcelable(Constants.EXTRA_USER_DATA, UserData.class);
                RequestToken requestToken = new RequestToken(binder, null, userData);
                IsolatedWorker implCallback = IsolatedService.this.onRequest(requestToken);
                implCallback.onWebViewEvent(
                        input, new WrappedCallback<WebViewEventOutput>(resultCallback));

            } else if (operationCode == Constants.OP_TRAINING_EXAMPLE) {
                TrainingExampleInput input =
                        Objects.requireNonNull(
                                params.getParcelable(
                                        Constants.EXTRA_INPUT, TrainingExampleInput.class));
                IDataAccessService binder =
                        IDataAccessService.Stub.asInterface(
                                Objects.requireNonNull(
                                        params.getBinder(
                                                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
                Objects.requireNonNull(binder);
                UserData userData = params.getParcelable(Constants.EXTRA_USER_DATA, UserData.class);
                RequestToken requestToken = new RequestToken(binder, null, userData);
                IsolatedWorker implCallback = IsolatedService.this.onRequest(requestToken);
                implCallback.onTrainingExample(
                        input, new WrappedCallback<TrainingExampleOutput>(resultCallback));
            } else {
                throw new IllegalArgumentException("Invalid op code: " + operationCode);
            }
        }
    }

    private static class WrappedCallback<T extends Parcelable> implements Consumer<T> {
        @NonNull private final IIsolatedServiceCallback mCallback;

        WrappedCallback(IIsolatedServiceCallback callback) {
            mCallback = Objects.requireNonNull(callback);
        }

        @Override
        public void accept(T result) {
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
