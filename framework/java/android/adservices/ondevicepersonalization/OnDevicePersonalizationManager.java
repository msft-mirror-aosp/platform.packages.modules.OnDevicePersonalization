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


import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.compat.CompatChanges;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.view.SurfaceControlViewHost;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.ExceptionInfo;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

// TODO(b/289102463): Add a link to the public ODP developer documentation.
/**
 * OnDevicePersonalizationManager provides APIs for apps to load an
 * {@link IsolatedService} in an isolated process and interact with it.
 *
 * An app can request an {@link IsolatedService} to generate content for display
 * within an {@link android.view.SurfaceView} within the app's view hierarchy, and also write
 * persistent results to on-device storage which can be consumed by Federated Analytics for
 * cross-device statistical analysis or by Federated Learning for model training. The displayed
 * content and the persistent output are both not directly accessible by the calling app.
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
public class OnDevicePersonalizationManager {
    /** @hide */
    public static final String ON_DEVICE_PERSONALIZATION_SERVICE =
            "on_device_personalization_service";

    /**
     * The {@link OnDevicePersonalizationException} returned by the {@link
     * OnDevicePersonalizationManager} class has additional, more granular error codes.
     *
     * <p>{@link OnDevicePersonalizationException#getErrorCode()} can now return additional granular
     * error codes. Developers should also account for potential future additions to the number of
     * error codes via appropriate defaults in any error code processing logic.
     *
     * @hide
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static final long GRANULAR_EXCEPTION_ERROR_CODES = 342672147;

    private static final String INTENT_FILTER_ACTION = "android.OnDevicePersonalizationService";
    private static final String ODP_MANAGING_SERVICE_PACKAGE_SUFFIX =
            "com.android.ondevicepersonalization.services";

    private static final String ALT_ODP_MANAGING_SERVICE_PACKAGE_SUFFIX =
            "com.google.android.ondevicepersonalization.services";

    private static final String ODP_INTERNAL_ERROR_MESSAGE =
            "Internal error in the OnDevicePersonalizationService.";

    private static final String ISOLATED_SERVICE_ERROR_MESSAGE = "Error in the IsolatedService.";

    private static final String ODP_DISABLED_ERROR_MESSAGE =
            "Personalization disabled by device configuration.";

    private static final String ODP_MANIFEST_ERROR_MESSAGE =
            "OnDevicePersonalization manifest invalid.";
    private static final String TAG = OnDevicePersonalizationManager.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private final AbstractServiceBinder<IOnDevicePersonalizationManagingService> mServiceBinder;
    private final Context mContext;

    /**
     * The result of a call to {@link OnDevicePersonalizationManager#execute(ComponentName,
     * PersistableBundle, Executor, OutcomeReceiver)}
     */
    public static class ExecuteResult {
        @Nullable private final SurfacePackageToken mSurfacePackageToken;
        @Nullable private final byte[] mOutputData;

        /** @hide */
        ExecuteResult(
                @Nullable SurfacePackageToken surfacePackageToken,
                @Nullable byte[] outputData) {
            mSurfacePackageToken = surfacePackageToken;
            mOutputData = outputData;
        }

        /**
         * Returns a {@link SurfacePackageToken}, which is an opaque reference to content that
         * can be displayed in a {@link android.view.SurfaceView}. This may be null if the
         * {@link IsolatedService} has not generated any content to be displayed within the
         * calling app.
         */
        @Nullable public SurfacePackageToken getSurfacePackageToken() {
            return mSurfacePackageToken;
        }

        /**
         * Returns the output data that was returned by the {@link IsolatedService}. This will be
         * non-null if the {@link IsolatedService} returns any results to the caller, and the
         * egress of data from the {@link IsolatedService} to the specific calling app is allowed
         * by policy as well as an allowlist.
         */
        @Nullable public byte[] getOutputData() {
            return mOutputData;
        }
    }

    /** @hide */
    public OnDevicePersonalizationManager(Context context) {
        this(
                context,
                AbstractServiceBinder.getServiceBinderByIntent(
                        context,
                        INTENT_FILTER_ACTION,
                        List.of(
                                ODP_MANAGING_SERVICE_PACKAGE_SUFFIX,
                                ALT_ODP_MANAGING_SERVICE_PACKAGE_SUFFIX),
                        SdkLevel.isAtLeastU() ? Context.BIND_ALLOW_ACTIVITY_STARTS : 0,
                        IOnDevicePersonalizationManagingService.Stub::asInterface));
    }

    /** @hide */
    @VisibleForTesting
    public OnDevicePersonalizationManager(
            Context context,
            AbstractServiceBinder<IOnDevicePersonalizationManagingService> serviceBinder) {
        mContext = context;
        mServiceBinder = serviceBinder;
    }

    /**
     * Executes an {@link IsolatedService} in the OnDevicePersonalization sandbox. The
     * platform binds to the specified {@link IsolatedService} in an isolated process
     * and calls {@link IsolatedWorker#onExecute(ExecuteInput, android.os.OutcomeReceiver)}
     * with the caller-provided parameters. When the {@link IsolatedService} finishes execution,
     * the platform returns tokens that refer to the results from the service to the caller.
     * These tokens can be subsequently used to display results in a
     * {@link android.view.SurfaceView} within the calling app.
     *
     * @param service The {@link ComponentName} of the {@link IsolatedService}.
     * @param params a {@link PersistableBundle} that is passed from the calling app to the
     *     {@link IsolatedService}. The expected contents of this parameter are defined
     *     by the{@link IsolatedService}. The platform does not interpret this parameter.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param receiver This returns a {@link ExecuteResult} object on success or an
     *     {@link Exception} on failure. If the
     *     {@link IsolatedService} returned a {@link RenderingConfig} to be displayed,
     *     {@link ExecuteResult#getSurfacePackageToken()} will return a non-null
     *     {@link SurfacePackageToken}.
     *     The {@link SurfacePackageToken} object can be used in a subsequent
     *     {@link #requestSurfacePackage(SurfacePackageToken, IBinder, int, int, int, Executor,
     *     OutcomeReceiver)} call to display the result in a view. The returned
     *     {@link SurfacePackageToken} may be null to indicate that no output is expected to be
     *     displayed for this request. If the {@link IsolatedService} has returned any output data
     *     and the calling app is allowlisted to receive data from this service, the
     *     {@link ExecuteResult#getOutputData()} will return a non-null byte array.
     *
     *     In case of an error, the receiver returns one of the following exceptions:
     *     Returns a {@link android.content.pm.PackageManager.NameNotFoundException} if the handler
     *     package is not installed or does not have a valid ODP manifest.
     *     Returns {@link ClassNotFoundException} if the handler class is not found.
     *     Returns an {@link OnDevicePersonalizationException} if execution of the handler fails.
     */
    public void execute(
            @NonNull ComponentName service,
            @NonNull PersistableBundle params,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<ExecuteResult, Exception> receiver
    ) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(params);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        Objects.requireNonNull(service.getPackageName());
        Objects.requireNonNull(service.getClassName());
        if (service.getPackageName().isEmpty()) {
            throw new IllegalArgumentException("missing service package name");
        }
        if (service.getClassName().isEmpty()) {
            throw new IllegalArgumentException("missing service class name");
        }
        long startTimeMillis = SystemClock.elapsedRealtime();

        try {
            final IOnDevicePersonalizationManagingService odpService =
                    mServiceBinder.getService(executor);

            try {
                IExecuteCallback callbackWrapper =
                        new IExecuteCallback.Stub() {
                            @Override
                            public void onSuccess(
                                    Bundle callbackResult, CalleeMetadata calleeMetadata) {
                                final long token = Binder.clearCallingIdentity();
                                try {
                                    executor.execute(
                                            () -> {
                                                try {
                                                    SurfacePackageToken surfacePackageToken = null;
                                                    if (callbackResult != null) {
                                                        String tokenString =
                                                                callbackResult.getString(
                                                                        Constants
                                                                                .EXTRA_SURFACE_PACKAGE_TOKEN_STRING);
                                                        if (tokenString != null
                                                                && !tokenString.isBlank()) {
                                                            surfacePackageToken =
                                                                    new SurfacePackageToken(
                                                                            tokenString);
                                                        }
                                                    }
                                                    byte[] data =
                                                            callbackResult.getByteArray(
                                                                    Constants.EXTRA_OUTPUT_DATA);
                                                    receiver.onResult(
                                                            new ExecuteResult(
                                                                    surfacePackageToken, data));
                                                } catch (Exception e) {
                                                    receiver.onError(e);
                                                }
                                            });
                                } finally {
                                    Binder.restoreCallingIdentity(token);
                                    logApiCallStats(
                                            odpService,
                                            service.getPackageName(),
                                            Constants.API_NAME_EXECUTE,
                                            SystemClock.elapsedRealtime() - startTimeMillis,
                                            calleeMetadata.getServiceEntryTimeMillis()
                                                    - startTimeMillis,
                                            SystemClock.elapsedRealtime()
                                                    - calleeMetadata.getCallbackInvokeTimeMillis(),
                                            Constants.STATUS_SUCCESS);
                                }
                            }

                            @Override
                            public void onError(
                                    int errorCode,
                                    int isolatedServiceErrorCode,
                                    byte[] serializedExceptionInfo,
                                    CalleeMetadata calleeMetadata) {
                                final long token = Binder.clearCallingIdentity();
                                try {
                                    executor.execute(
                                            () -> {
                                                receiver.onError(
                                                        createException(
                                                                errorCode, isolatedServiceErrorCode,
                                                                serializedExceptionInfo, mContext));
                                            });
                                } finally {
                                    Binder.restoreCallingIdentity(token);
                                    logApiCallStats(
                                            odpService,
                                            service.getPackageName(),
                                            Constants.API_NAME_EXECUTE,
                                            SystemClock.elapsedRealtime() - startTimeMillis,
                                            calleeMetadata.getServiceEntryTimeMillis()
                                                    - startTimeMillis,
                                            SystemClock.elapsedRealtime()
                                                    - calleeMetadata.getCallbackInvokeTimeMillis(),
                                            errorCode);
                                }
                            }
                        };

                Bundle wrappedParams = new Bundle();
                wrappedParams.putParcelable(
                        Constants.EXTRA_APP_PARAMS_SERIALIZED,
                        new ByteArrayParceledSlice(PersistableBundleUtils.toByteArray(params)));
                odpService.execute(
                        mContext.getPackageName(),
                        service,
                        wrappedParams,
                        new CallerMetadata.Builder().setStartTimeMillis(startTimeMillis).build(),
                        callbackWrapper);
            } catch (Exception e) {
                logApiCallStats(
                        odpService,
                        service.getPackageName(),
                        Constants.API_NAME_EXECUTE,
                        SystemClock.elapsedRealtime() - startTimeMillis,
                        0,
                        0,
                        Constants.STATUS_INTERNAL_ERROR);
                receiver.onError(e);
            }

        } catch (Exception e) {
            receiver.onError(e);
        }
    }

    /**
     * Requests a {@link android.view.SurfaceControlViewHost.SurfacePackage} to be inserted into a
     * {@link android.view.SurfaceView} inside the calling app. The surface package will contain an
     * {@link android.view.View} with the content from a result of a prior call to
     * {@code #execute(ComponentName, PersistableBundle, Executor, OutcomeReceiver)} running in
     * the OnDevicePersonalization sandbox.
     *
     * @param surfacePackageToken a reference to a {@link SurfacePackageToken} returned by a prior
     *     call to {@code #execute(ComponentName, PersistableBundle, Executor, OutcomeReceiver)}.
     * @param surfaceViewHostToken the hostToken of the {@link android.view.SurfaceView}, which is
     *     returned by {@link android.view.SurfaceView#getHostToken()} after the
     *     {@link android.view.SurfaceView} has been added to the view hierarchy.
     * @param displayId the integer ID of the logical display on which to display the
     *     {@link android.view.SurfaceControlViewHost.SurfacePackage}, returned by
     *     {@code Context.getDisplay().getDisplayId()}.
     * @param width the width of the {@link android.view.SurfaceControlViewHost.SurfacePackage}
     *     in pixels.
     * @param height the height of the {@link android.view.SurfaceControlViewHost.SurfacePackage}
     *     in pixels.
     * @param executor the {@link Executor} on which to invoke the callback
     * @param receiver This either returns a
     *     {@link android.view.SurfaceControlViewHost.SurfacePackage} on success, or
     *     {@link Exception} on failure. The exception type is
     *     {@link OnDevicePersonalizationException} if execution of the handler fails.
     */
    public void requestSurfacePackage(
            @NonNull SurfacePackageToken surfacePackageToken,
            @NonNull IBinder surfaceViewHostToken,
            int displayId,
            int width,
            int height,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<SurfaceControlViewHost.SurfacePackage, Exception> receiver
    ) {
        Objects.requireNonNull(surfacePackageToken);
        Objects.requireNonNull(surfaceViewHostToken);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }

        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }

        if (displayId < 0) {
            throw new IllegalArgumentException("displayId must be >= 0");
        }
        long startTimeMillis = SystemClock.elapsedRealtime();

        try {
            final IOnDevicePersonalizationManagingService service =
                    Objects.requireNonNull(mServiceBinder.getService(executor));
            long serviceInvokedTimeMillis = SystemClock.elapsedRealtime();

            try {
                IRequestSurfacePackageCallback callbackWrapper =
                        new IRequestSurfacePackageCallback.Stub() {
                            @Override
                            public void onSuccess(
                                    SurfaceControlViewHost.SurfacePackage surfacePackage,
                                    CalleeMetadata calleeMetadata) {
                                final long token = Binder.clearCallingIdentity();
                                try {
                                    executor.execute(
                                            () -> {
                                                receiver.onResult(surfacePackage);
                                            });
                                } finally {
                                    Binder.restoreCallingIdentity(token);
                                    logApiCallStats(
                                            service,
                                            "",
                                            Constants.API_NAME_REQUEST_SURFACE_PACKAGE,
                                            SystemClock.elapsedRealtime() - startTimeMillis,
                                            0,
                                            SystemClock.elapsedRealtime()
                                                    - calleeMetadata.getCallbackInvokeTimeMillis(),
                                            Constants.STATUS_SUCCESS);
                                }
                            }

                            @Override
                            public void onError(
                                    int errorCode,
                                    int isolatedServiceErrorCode,
                                    byte[] serializedExceptionInfo,
                                    CalleeMetadata calleeMetadata) {
                                final long token = Binder.clearCallingIdentity();
                                try {
                                    executor.execute(
                                            () ->
                                                    receiver.onError(
                                                            createException(
                                                                    errorCode,
                                                                    isolatedServiceErrorCode,
                                                                    serializedExceptionInfo,
                                                                    mContext)));
                                } finally {
                                    Binder.restoreCallingIdentity(token);
                                    logApiCallStats(
                                            service,
                                            "",
                                            Constants.API_NAME_REQUEST_SURFACE_PACKAGE,
                                            SystemClock.elapsedRealtime() - startTimeMillis,
                                            0,
                                            SystemClock.elapsedRealtime()
                                                    - calleeMetadata.getCallbackInvokeTimeMillis(),
                                            errorCode);
                                }
                            }
                        };

                service.requestSurfacePackage(
                        surfacePackageToken.getTokenString(),
                        surfaceViewHostToken,
                        displayId,
                        width,
                        height,
                        new CallerMetadata.Builder().setStartTimeMillis(startTimeMillis).build(),
                        callbackWrapper);
                logApiCallStats(
                        service,
                        "",
                        Constants.API_NAME_REQUEST_SURFACE_PACKAGE,
                        SystemClock.elapsedRealtime() - startTimeMillis,
                        SystemClock.elapsedRealtime() - serviceInvokedTimeMillis,
                        0,
                        Constants.STATUS_SUCCESS);

            } catch (Exception e) {
                logApiCallStats(
                        service,
                        "",
                        Constants.API_NAME_REQUEST_SURFACE_PACKAGE,
                        SystemClock.elapsedRealtime() - startTimeMillis,
                        0,
                        0,
                        Constants.STATUS_INTERNAL_ERROR);
                receiver.onError(e);
            }

        } catch (Exception e) {
            receiver.onError(e);
        }
    }

    private static String convertMessage(int errorCode) {
        switch (errorCode) {
            case Constants.STATUS_INTERNAL_ERROR:
                return ODP_INTERNAL_ERROR_MESSAGE;
            case Constants.STATUS_SERVICE_FAILED:
                return ISOLATED_SERVICE_ERROR_MESSAGE;
            case Constants.STATUS_PERSONALIZATION_DISABLED:
                return ODP_DISABLED_ERROR_MESSAGE;
            case Constants.STATUS_MANIFEST_PARSING_FAILED: // Intentional fallthrough
            case Constants.STATUS_MANIFEST_MISCONFIGURED:
                return ODP_MANIFEST_ERROR_MESSAGE;
            default:
                sLogger.w(TAG + "Unexpected error code while creating exception: " + errorCode);
                return "";
        }
    }

    /**
     * Convert error codes returned by the ODP Service to match calling package's target sdk
     * version.
     */
    private static int translateErrorCode(int errorCode, Context context) {
        if (errorCode < Constants.STATUS_MANIFEST_PARSING_FAILED
                || CompatChanges.isChangeEnabled(GRANULAR_EXCEPTION_ERROR_CODES)) {
            // Return code unchanged since either the error code does not require translation
            // (by virtue of being an old/original error code) or the package is targeted to the
            // appropriate sdk version.
            return errorCode;
        }
        // Translate to appropriate older error code if required.
        sLogger.d(TAG, "Package " + context.getPackageName() + " has old target sdk version.");
        // TODO (b/342672147): add translation for newer error codes
        int translatedCode = Constants.STATUS_INTERNAL_ERROR;
        switch (errorCode) {
            case Constants.STATUS_MANIFEST_PARSING_FAILED ->
                    translatedCode = Constants.STATUS_NAME_NOT_FOUND;
            case Constants.STATUS_MANIFEST_MISCONFIGURED ->
                    translatedCode = Constants.STATUS_CLASS_NOT_FOUND;
        }
        return translatedCode;
    }

    private static Exception createException(
            int errorCode,
            int isolatedServiceErrorCode,
            byte[] serializedExceptionInfo,
            Context context) {
        errorCode = translateErrorCode(errorCode, context);
        Exception cause = ExceptionInfo.fromByteArray(serializedExceptionInfo);
        switch (errorCode) {
            case Constants.STATUS_NAME_NOT_FOUND:
                Exception e = new PackageManager.NameNotFoundException();
                try {
                    // NameNotFoundException does not have a constructor that takes a Throwable.
                    if (cause != null) {
                        e.initCause(cause);
                    }
                } catch (Exception e2) {
                    sLogger.i(TAG + ": could not update cause", e2);
                }
                return e;
            case Constants.STATUS_CLASS_NOT_FOUND:
                return new ClassNotFoundException("", cause);
            case Constants.STATUS_SERVICE_FAILED:
                return (isolatedServiceErrorCode > 0 && isolatedServiceErrorCode < 128)
                        ? new OnDevicePersonalizationException(
                                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED,
                                new IsolatedServiceException(isolatedServiceErrorCode))
                        : new OnDevicePersonalizationException(
                                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED,
                                convertMessage(errorCode),
                                cause);
            case Constants.STATUS_PERSONALIZATION_DISABLED:
                return new OnDevicePersonalizationException(
                        OnDevicePersonalizationException.ERROR_PERSONALIZATION_DISABLED,
                        convertMessage(errorCode),
                        cause);
            case Constants.STATUS_MANIFEST_PARSING_FAILED:
                return new OnDevicePersonalizationException(
                        OnDevicePersonalizationException
                                .ERROR_ISOLATED_SERVICE_MANIFEST_PARSING_FAILED,
                        convertMessage(errorCode),
                        cause);
            case Constants.STATUS_MANIFEST_MISCONFIGURED:
                return new OnDevicePersonalizationException(
                        OnDevicePersonalizationException
                                .ERROR_ISOLATED_SERVICE_MANIFEST_PARSING_FAILED,
                        convertMessage(errorCode),
                        cause);
            default:
                return new IllegalStateException(convertMessage(errorCode), cause);
        }
    }

    private static void logApiCallStats(
            IOnDevicePersonalizationManagingService service,
            String sdkPackageName,
            int apiName,
            long latencyMillis,
            long rpcCallLatencyMillis,
            long rpcReturnLatencyMillis,
            int responseCode) {
        try {
            if (service != null) {
                service.logApiCallStats(sdkPackageName, apiName, latencyMillis,
                        rpcCallLatencyMillis, rpcReturnLatencyMillis, responseCode);
            }
        } catch (Exception e) {
            sLogger.e(e, TAG + ": Error logging API call stats");
        }
    }
}
