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

package com.android.ondevicepersonalization.services.process;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_TRUSTED_PARTNER_APPS_LIST;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.modules.utils.build.SdkLevel;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.ExceptionInfo;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.StableFlags;
import com.android.ondevicepersonalization.services.data.errors.AggregatedErrorCodesLogger;
import com.android.ondevicepersonalization.services.util.AllowListUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * A process runner that runs an isolated service by binding to it. It runs the service in a shared
 * isolated process if the shared_isolated_process_feature_enabled flag is enabled and the selected
 * isolated service opts in to running in a shared isolated process.
 */
public class IsolatedServiceBindingRunner implements ProcessRunner  {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static final String TAG = IsolatedServiceBindingRunner.class.getSimpleName();

    // SIP that hosts services from all trusted partners, as well as internal isolated services.
    public static final String TRUSTED_PARTNER_APPS_SIP = "trusted_partner_apps_sip";

    // SIP that hosts unknown remote services.
    public static final String UNKNOWN_APPS_SIP = "unknown_apps_sip";

    private final Context mApplicationContext;
    private final Injector mInjector;

    @VisibleForTesting
    static class Injector {
        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }
    }

    /** Creates a ProcessRunner. */
    IsolatedServiceBindingRunner() {
        this(OnDevicePersonalizationApplication.getAppContext(), new Injector());
    }

    @VisibleForTesting
    IsolatedServiceBindingRunner(@NonNull Context applicationContext, @NonNull Injector injector) {
        mApplicationContext = Objects.requireNonNull(applicationContext);
        mInjector = Objects.requireNonNull(injector);
    }

    /** Binds to a service and put it in one of ODP's shared isolated process. */
    @Override
    @NonNull public ListenableFuture<IsolatedServiceInfo> loadIsolatedService(
            @NonNull String taskName, @NonNull ComponentName componentName) {
        try {
            ListenableFuture<AbstractServiceBinder<IIsolatedService>> isolatedServiceFuture =
                    mInjector.getExecutor().submit(
                            () -> getIsolatedServiceBinder(componentName));

            return FluentFuture.from(isolatedServiceFuture)
                    .transformAsync(
                            (isolatedService) -> {
                                return Futures.immediateFuture(
                                        new IsolatedServiceInfo(
                                                mInjector.getClock().elapsedRealtime(),
                                                componentName,
                                                isolatedService));
                            },
                            mInjector.getExecutor())
                    .catchingAsync(
                            Exception.class,
                            e -> {
                                sLogger.d(
                                        TAG
                                                + ": loading of isolated service failed for "
                                                + componentName,
                                        e);
                                // Return OdpServiceException if the exception thrown was not
                                // already an OdpServiceException.
                                if (e instanceof OdpServiceException) {
                                    return Futures.immediateFailedFuture(e);
                                }
                                return Futures.immediateFailedFuture(
                                        new OdpServiceException(
                                            Constants.STATUS_ISOLATED_SERVICE_LOADING_FAILED, e));
                            },
                            mInjector.getExecutor());
        } catch (Exception e) {
            return Futures.immediateFailedFuture(
                    new OdpServiceException(Constants.STATUS_ISOLATED_SERVICE_LOADING_FAILED, e));
        }
    }

    /** Runs the remote isolated service in the shared isolated process. */
    @NonNull
    @Override
    public ListenableFuture<Bundle> runIsolatedService(
            @NonNull IsolatedServiceInfo isolatedProcessInfo, int operationCode,
            @NonNull Bundle serviceParams) {
        IIsolatedService service;
        try {
            service = isolatedProcessInfo.getIsolatedServiceBinder().getService(Runnable::run);
        } catch (Exception e) {
            // Failure in loading/connecting to the IsolatedService vs actual issue
            // in running the IsolatedService code via the onRequest call below.
            sLogger.d(TAG + ": unable to get the IsolatedService binder.", e);
            return Futures.immediateFailedFuture(
                    new OdpServiceException(Constants.STATUS_ISOLATED_SERVICE_LOADING_FAILED));
        }

        ListenableFuture<Bundle> callbackFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            service.onRequest(
                                    operationCode,
                                    serviceParams,
                                    new IIsolatedServiceCallback.Stub() {
                                        @Override
                                        public void onSuccess(Bundle result) {
                                            completer.set(result);
                                        }

                                        @Override
                                        public void onError(
                                                int errorCode,
                                                int isolatedServiceErrorCode,
                                                byte[] serializedExceptionInfo) {
                                            Exception cause =
                                                    ExceptionInfo.fromByteArray(
                                                            serializedExceptionInfo);
                                            if (isolatedServiceErrorCode > 0) {
                                                final long token = Binder.clearCallingIdentity();
                                                try {
                                                    ListenableFuture<?> unused =
                                                            AggregatedErrorCodesLogger
                                                                .logIsolatedServiceErrorCode(
                                                                    isolatedServiceErrorCode,
                                                                    isolatedProcessInfo
                                                                        .getComponentName(),
                                                                    mApplicationContext);
                                                } finally {
                                                    Binder.restoreCallingIdentity(token);
                                                }
                                                cause =
                                                        new IsolatedServiceException(
                                                                isolatedServiceErrorCode, cause);
                                            }
                                            completer.setException(
                                                    new OdpServiceException(
                                                            Constants.STATUS_SERVICE_FAILED,
                                                            cause));
                                        }
                                    });
                            // used for debugging purpose only.
                            return "IsolatedService.onRequest";
                        });
        return FluentFuture.from(callbackFuture)
                .catchingAsync(
                        Throwable.class, // Catch FutureGarbageCollectedException
                        e -> {
                            return (e instanceof IsolatedServiceException
                                            || e instanceof OdpServiceException)
                                    ? Futures.immediateFailedFuture(e)
                                    : Futures.immediateFailedFuture(
                                            new TimeoutException(
                                                    "Callback to future adapter was garbage"
                                                            + " collected."));
                        },
                        mInjector.getExecutor());
    }

    /** Unbinds from the remote isolated service. */
    @NonNull
    @Override
    public ListenableFuture<Void> unloadIsolatedService(
            @NonNull IsolatedServiceInfo isolatedServiceInfo) {
        try {
            return (ListenableFuture<Void>) mInjector.getExecutor().submit(
                    () -> isolatedServiceInfo.getIsolatedServiceBinder().unbindFromService());
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private AbstractServiceBinder<IIsolatedService> getIsolatedServiceBinder(
            @NonNull ComponentName service) throws Exception {
        PackageManager pm = mApplicationContext.getPackageManager();
        sLogger.d(TAG + ": Package manager = " + pm);
        ServiceInfo si = pm.getServiceInfo(service, PackageManager.GET_META_DATA);
        checkIsolatedService(service, si);
        boolean isSipRequested = isSharedIsolatedProcessRequested(si);

        // null instance name results in regular isolated service being created.
        String instanceName = isSipRequested ? getSipInstanceName(service.getPackageName()) : null;
        int bindFlag = isSipRequested
                ? Context.BIND_SHARED_ISOLATED_PROCESS
                : Context.BIND_AUTO_CREATE;

        return AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                mApplicationContext,
                service.getClassName(), service.getPackageName(),
                instanceName, bindFlag, IIsolatedService.Stub::asInterface);
    }

    @VisibleForTesting
    String getSipInstanceName(String packageName) {
        String partnerAppsList =
                (String) StableFlags.get(KEY_TRUSTED_PARTNER_APPS_LIST);
        String packageCertificate = null;
        try {
            packageCertificate = PackageUtils.getCertDigest(mApplicationContext, packageName);
        } catch (Exception e) {
            sLogger.d(TAG + ": not able to find certificate for package " + packageName, e);
        }
        boolean isPartnerApp = AllowListUtils.isAllowListed(
                packageName, packageCertificate, partnerAppsList);
        String sipInstanceName = isPartnerApp ? TRUSTED_PARTNER_APPS_SIP : UNKNOWN_APPS_SIP;
        return (boolean) StableFlags.get(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED)
                    ? sipInstanceName + "_disable_art_image_" : sipInstanceName;
    }

    @VisibleForTesting
    static void checkIsolatedService(ComponentName service, ServiceInfo si)
            throws OdpServiceException {
        if ((si.flags & si.FLAG_ISOLATED_PROCESS) == 0) {
            sLogger.e(
                    TAG, "ODP client service not configured to run in isolated process " + service);
            throw new OdpServiceException(
                    Constants.STATUS_MANIFEST_PARSING_FAILED,
                    "ODP client services should run in isolated processes.");
        }
    }

    @VisibleForTesting
    static boolean isSharedIsolatedProcessRequested(ServiceInfo si) {
        if (!SdkLevel.isAtLeastU()) {
            return false;
        }
        if (!(boolean) StableFlags.get(KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED)) {
            return false;
        }

        return (si.flags & si.FLAG_ALLOW_SHARED_ISOLATED_PROCESS) != 0;
    }
}
