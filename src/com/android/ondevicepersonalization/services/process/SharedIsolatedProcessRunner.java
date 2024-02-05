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

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED;

import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;

/** Utilities for running remote isolated services in a shared isolated process. */
public class SharedIsolatedProcessRunner implements ProcessRunner  {

    private final Context mApplicationContext;
    private final Injector mInjector;

    static class Injector {
        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }
    }
    SharedIsolatedProcessRunner(
            @NonNull Context applicationContext,
            @NonNull Injector injector) {
        mApplicationContext = Objects.requireNonNull(applicationContext);
        mInjector = Objects.requireNonNull(injector);
    }

    private static class LazyInstanceHolder {
        static final SharedIsolatedProcessRunner LAZY_INSTANCE =
                new SharedIsolatedProcessRunner(
                        OnDevicePersonalizationApplication.getAppContext(),
                        new Injector());
    }

    /** Returns the global ProcessRunner. */
    @NonNull
    public static SharedIsolatedProcessRunner getInstance() {
        return SharedIsolatedProcessRunner.LazyInstanceHolder.LAZY_INSTANCE;
    }

    /** Binds to a service and put it in one of ODP's shared isolated process. */
    @Override
    @NonNull public ListenableFuture<IsolatedServiceInfo> loadIsolatedService(
            @NonNull String taskName, @NonNull ComponentName componentName) {
        try {
            ListenableFuture<AbstractServiceBinder<IIsolatedService>> isolatedServiceFuture =
                    mInjector.getExecutor().submit(
                            () -> getIsolatedServiceBinder(mApplicationContext, componentName));

            return FluentFuture.from(isolatedServiceFuture)
                    .transformAsync(
                            (isolatedService) -> {
                                try {
                                    return Futures.immediateFuture(new IsolatedServiceInfo(
                                            mInjector.getClock().elapsedRealtime(), componentName,
                                            /* pluginController= */ null, isolatedService));
                                } catch (Exception e) {
                                    return Futures.immediateFailedFuture(e);
                                }
                            }, mInjector.getExecutor())
                    .catchingAsync(
                            Exception.class,
                            Futures::immediateFailedFuture,
                            mInjector.getExecutor()
                    );
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    /** Runs the remote isolated service in the shared isolated process. */
    @NonNull
    @Override
    public ListenableFuture<Bundle> runIsolatedService(
            @NonNull IsolatedServiceInfo isolatedProcessInfo, int operationCode,
            @NonNull Bundle serviceParams) {
            return CallbackToFutureAdapter.getFuture(
                completer -> {
                    isolatedProcessInfo.getIsolatedServiceBinder()
                            .getService(Runnable::run)
                            .onRequest(
                                    operationCode, serviceParams,
                                    new IIsolatedServiceCallback.Stub() {
                                        @Override public void onSuccess(Bundle result) {
                                            completer.set(result);
                                        }

                                        // TO-DO (323882182): Granular isolated servce failures.
                                        @Override public void onError(int errorCode) {
                                            completer.setException(
                                                new OdpServiceException(ERROR_ISOLATED_SERVICE_FAILED));
                                        }
                                    });
                    return null;
                });
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
            @NonNull Context context, @NonNull ComponentName service) {
        return AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                        context,
                        service.getClassName(),
                        service.getPackageName(),
                        // TO-DO (323427279): Put trusted apps into a separate SIP.
                        "trustedSip",
                        Context.BIND_SHARED_ISOLATED_PROCESS,
                        IIsolatedService.Stub::asInterface);
    }
}
