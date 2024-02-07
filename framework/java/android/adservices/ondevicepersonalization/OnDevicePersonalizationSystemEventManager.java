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

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.REGISTER_MEASUREMENT_EVENT;

import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.SystemClock;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Provides APIs that receive and process events from the OS.
 * @hide
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
public class OnDevicePersonalizationSystemEventManager {
    /** @hide */
    public static final String ON_DEVICE_PERSONALIZATION_SYSTEM_EVENT_SERVICE =
            "on_device_personalization_system_event_service";
    private static final String INTENT_FILTER_ACTION =
            "android.OnDevicePersonalizationService";
    private static final String ODP_MANAGING_SERVICE_PACKAGE_SUFFIX =
            "com.android.ondevicepersonalization.services";
    private static final String ALT_ODP_MANAGING_SERVICE_PACKAGE_SUFFIX =
            "com.google.android.ondevicepersonalization.services";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    // TODO(b/301732670): Define a new service for this manager and bind to it.
    private final AbstractServiceBinder<IOnDevicePersonalizationManagingService> mServiceBinder;
    private final Context mContext;

    /** @hide */
    public OnDevicePersonalizationSystemEventManager(Context context) {
        this(
                context,
                AbstractServiceBinder.getServiceBinderByIntent(
                        context,
                        INTENT_FILTER_ACTION,
                        List.of(
                                ODP_MANAGING_SERVICE_PACKAGE_SUFFIX,
                                ALT_ODP_MANAGING_SERVICE_PACKAGE_SUFFIX),
                        0,
                        IOnDevicePersonalizationManagingService.Stub::asInterface));
    }

    /** @hide */
    @VisibleForTesting
    public OnDevicePersonalizationSystemEventManager(
            Context context,
            AbstractServiceBinder<IOnDevicePersonalizationManagingService> serviceBinder) {
        mContext = context;
        mServiceBinder = serviceBinder;
    }

    /**
     * Receives a measurement event from the Measurement Service.
     *
     * @param measurementEvent the input data from the measurement service.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param receiver This either returns {@code null} on success, or an exception on failure.
     */
    @RequiresPermission(REGISTER_MEASUREMENT_EVENT)
    public void registerMeasurementEvent(
            @NonNull RegisterMeasurementEventInput measurementEvent,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> receiver) {
        Objects.requireNonNull(measurementEvent);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);
        if (measurementEvent.getType()
                != RegisterMeasurementEventInput.MEASUREMENT_EVENT_WEB_TRIGGER) {
            throw new IllegalArgumentException("invalid measurementEventType");
        }
        long startTimeMillis = SystemClock.elapsedRealtime();

        try {
            final IOnDevicePersonalizationManagingService service =
                    mServiceBinder.getService(executor);
            Bundle bundle = new Bundle();
            bundle.putParcelable(
                    Constants.EXTRA_DESTINATION_URL, measurementEvent.getDestinationUrl());
            bundle.putString(
                    Constants.EXTRA_APP_PACKAGE_NAME, measurementEvent.getAppPackageName());
            bundle.putString(Constants.EXTRA_MEASUREMENT_DATA, measurementEvent.getEventData());
            service.registerMeasurementEvent(
                    measurementEvent.getType(),
                    bundle,
                    new CallerMetadata.Builder().setStartTimeMillis(startTimeMillis).build(),
                    new IRegisterMeasurementEventCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(() -> receiver.onResult(null));
                        }
                        @Override
                        public void onError(int errorCode) {
                            executor.execute(() -> receiver.onError(
                                    new IllegalStateException("Error: " + errorCode)));
                        }
                    }
            );
        } catch (IllegalArgumentException | NullPointerException e) {
            throw e;
        } catch (Exception e) {
            receiver.onError(e);
        }
    }
}
