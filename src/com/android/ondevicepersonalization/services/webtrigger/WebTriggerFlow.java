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

package com.android.ondevicepersonalization.services.webtrigger;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.UserData;
import android.adservices.ondevicepersonalization.WebTriggerInputParcel;
import android.adservices.ondevicepersonalization.WebTriggerOutputParcel;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfig;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.process.ProcessRunnerImpl;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.LogUtils;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles a Web Trigger Registration.
 */
public class WebTriggerFlow {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "WebTriggerFlow";
    private static final String TASK_NAME = "WebTrigger";
    private static final String PACKAGE_NAME_KEY = "package";
    private static final String CLASS_NAME_KEY = "class";
    private static final String CERT_DIGEST_KEY = "cert_digest";
    private static final String DATA_KEY = "data";

    static class ParsedTriggerHeader {
        @NonNull String mPackageName;
        @NonNull String mClassName;
        @NonNull String mCertDigest;
        @NonNull String mData;

        ParsedTriggerHeader(
                @NonNull String packageName,
                @NonNull String className,
                @NonNull String certDigest,
                @NonNull String data) {
            mPackageName = Objects.requireNonNull(packageName);
            mClassName = Objects.requireNonNull(className);
            mCertDigest = Objects.requireNonNull(certDigest);
            mData = Objects.requireNonNull(data);
        }
    }

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        ListeningScheduledExecutorService getScheduledExecutor() {
            return OnDevicePersonalizationExecutors.getScheduledExecutor();
        }

        ProcessRunner getProcessRunner() {
            return ProcessRunnerImpl.getInstance();
        }
    }

    @NonNull private final String mDestinationUrl;
    @NonNull private final String mRegistrationUrl;
    @NonNull private final String mTriggerHeader;
    @NonNull private final Context mContext;
    @NonNull private final Injector mInjector;

    public WebTriggerFlow(
            @NonNull String destinationUrl,
            @NonNull String registrationUrl,
            @NonNull String triggerHeader,
            @NonNull Context context) {
        this(destinationUrl, registrationUrl, triggerHeader, context, new Injector());
    }

    @VisibleForTesting
    WebTriggerFlow(
            @NonNull String destinationUrl,
            @NonNull String registrationUrl,
            @NonNull String triggerHeader,
            @NonNull Context context,
            @NonNull Injector injector) {
        mDestinationUrl = Objects.requireNonNull(destinationUrl);
        mRegistrationUrl = Objects.requireNonNull(registrationUrl);
        mTriggerHeader = Objects.requireNonNull(triggerHeader);
        mContext = Objects.requireNonNull(context);
        mInjector = Objects.requireNonNull(injector);
    }

    /** Schedules the trigger processing flow to run asynchronously and returns immediately. */
    public ListenableFuture<Void> run() {
        if (getGlobalKillSwitch()) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Disabled by kill switch"));
        }

        return Futures.submitAsync(() -> this.processRequest(), mInjector.getExecutor());
    }

    private ListenableFuture<Void> processRequest() {
        try {
            if (mDestinationUrl.isBlank() || mRegistrationUrl.isBlank()
                    || mTriggerHeader.isBlank()) {
                return Futures.immediateFailedFuture(
                        new IllegalArgumentException("Missing url or header"));
            }

            JSONObject json = new JSONObject(mTriggerHeader);
            ParsedTriggerHeader parsedHeader = new ParsedTriggerHeader(
                    json.optString(PACKAGE_NAME_KEY),
                    json.optString(CLASS_NAME_KEY),
                    json.optString(CERT_DIGEST_KEY).replaceAll(":", ""),
                    json.optString(DATA_KEY));

            if (parsedHeader.mPackageName.isBlank()
                    || parsedHeader.mClassName.isBlank()) {
                return Futures.immediateFailedFuture(
                        new IllegalArgumentException("Missing package or class name"));
            }

            if (!parsedHeader.mCertDigest.isBlank()) {
                String installedPackageCert = PackageUtils.getCertDigest(
                        mContext, parsedHeader.mPackageName);
                if (!parsedHeader.mCertDigest.equals(installedPackageCert)) {
                    sLogger.i(TAG + ": Dropping trigger event due to cert mismatch");
                    return Futures.immediateFailedFuture(
                            new IllegalArgumentException("package cert mismatch"));
                }
            }

            AppManifestConfig config = Objects.requireNonNull(
                    AppManifestConfigHelper.getAppManifestConfig(
                        mContext, parsedHeader.mPackageName));
            if (!parsedHeader.mClassName.equals(config.getServiceName())) {
                sLogger.d(TAG + ": service class not found");
                return Futures.immediateFailedFuture(
                        new IllegalStateException("package or class not found"));
            }

            ComponentName service = ComponentName.createRelative(
                    parsedHeader.mPackageName, parsedHeader.mClassName);

            ListenableFuture<IsolatedServiceInfo> loadFuture =
                    mInjector.getProcessRunner().loadIsolatedService(
                            TASK_NAME, service);

            ListenableFuture<Void> resultFuture =
                    FluentFuture.from(loadFuture).transformAsync(
                            result -> runIsolatedService(parsedHeader, result),
                            mInjector.getExecutor())
                    .transform(
                        result -> result.getParcelable(
                                Constants.EXTRA_RESULT, WebTriggerOutputParcel.class),
                        mInjector.getExecutor())
                    .transformAsync(
                        result -> writeToLog(parsedHeader, result),
                        mInjector.getExecutor())
                    .withTimeout(
                        mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                        TimeUnit.SECONDS,
                        mInjector.getScheduledExecutor()
                    );

            var unused = Futures.whenAllComplete(loadFuture, resultFuture)
                    .callAsync(
                            () -> mInjector.getProcessRunner().unloadIsolatedService(
                                loadFuture.get()),
                            mInjector.getExecutor());

            return resultFuture;

        } catch (Exception e) {
            sLogger.e(e, TAG + ": Error");
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<Bundle> runIsolatedService(
            ParsedTriggerHeader parsedHeader,
            IsolatedServiceInfo isolatedServiceInfo) {
        sLogger.d(TAG + ": runIsolatedService() started.");
        Bundle serviceParams = new Bundle();
        WebTriggerInputParcel input =
                new WebTriggerInputParcel.Builder(
                        mDestinationUrl, mRegistrationUrl, "no-app", parsedHeader.mData)
                    .build();
        serviceParams.putParcelable(Constants.EXTRA_INPUT, input);
        DataAccessServiceImpl binder = new DataAccessServiceImpl(
                isolatedServiceInfo.getComponentName().getPackageName(),
                mContext, /* includeLocalData */ true,
                /* includeEventData */ true);
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, binder);
        UserDataAccessor userDataAccessor = new UserDataAccessor();
        UserData userData = userDataAccessor.getUserData();
        serviceParams.putParcelable(Constants.EXTRA_USER_DATA, userData);
        ListenableFuture<Bundle> result = mInjector.getProcessRunner().runIsolatedService(
                isolatedServiceInfo, Constants.OP_WEB_TRIGGER,
                serviceParams);
        return result;
    }

    private ListenableFuture<Void> writeToLog(
            ParsedTriggerHeader parsedHeader,
            WebTriggerOutputParcel result) {
        sLogger.d(TAG + ": writeToLog() started.");
        return FluentFuture.from(
                LogUtils.writeLogRecords(
                    mContext,
                    parsedHeader.mPackageName,
                    result.getRequestLogRecord(),
                    result.getEventLogRecords()))
                .transform(v -> null, MoreExecutors.newDirectExecutorService());
    }

    private boolean getGlobalKillSwitch() {
        long origId = Binder.clearCallingIdentity();
        boolean globalKillSwitch = mInjector.getFlags().getGlobalKillSwitch();
        Binder.restoreCallingIdentity(origId);
        return globalKillSwitch;
    }
}
