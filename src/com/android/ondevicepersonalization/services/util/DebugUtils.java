/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.util;

import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;

import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.ExceptionInfo;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;

import java.util.Objects;

/** Fuctions for testing and debugging. */
public class DebugUtils {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = DebugUtils.class.getSimpleName();
    private static final int MAX_EXCEPTION_CHAIN_DEPTH = 3;

    private static final String OVERRIDE_FC_SERVER_URL_PACKAGE =
            "debug.ondevicepersonalization.override_fc_server_url_package";
    private static final String OVERRIDE_FC_SERVER_URL =
            "debug.ondevicepersonalization.override_fc_server_url";

    /** Returns true if the device is debuggable. */
    public static boolean isDeveloperModeEnabled(@NonNull Context context) {
        ContentResolver resolver = Objects.requireNonNull(context.getContentResolver());
        return Build.isDebuggable()
                || Settings.Global.getInt(
                    resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    /** Returns the exception code reported by the service if debugging is allowed. */
    public static int getIsolatedServiceExceptionCode(
            @NonNull Context context,
            @NonNull ComponentName service,
            @NonNull OdpServiceException e) {
        try {
            if (!FlagsFactory.getFlags().isIsolatedServiceDebuggingEnabled()) {
                return 0;
            }
            if (isDeveloperModeEnabled(context)
                    && PackageUtils.isPackageDebuggable(context, service.getPackageName())) {
                if (e.getCause() != null && e.getCause() instanceof IsolatedServiceException) {
                    return ((IsolatedServiceException) e.getCause()).getErrorCode();
                }
            }
        } catch (Exception e2) {
            sLogger.e(e2, TAG + ": failed to get code");
        }
        return 0;
    }

    /** Serializes an exception chain to a byte[] */
    public static byte[] serializeExceptionInfo(
            ComponentName service, Throwable t) {
        try {
            Context context = OnDevicePersonalizationApplication.getAppContext();
            if (t == null || !isDeveloperModeEnabled(context)
                    || !FlagsFactory.getFlags().isIsolatedServiceDebuggingEnabled()
                    || !PackageUtils.isPackageDebuggable(context, service.getPackageName())) {
                return null;
            }

            return ExceptionInfo.toByteArray(t, MAX_EXCEPTION_CHAIN_DEPTH);
        } catch (Exception e) {
            sLogger.e(e, TAG + ": failed to serialize exception info");
            return null;
        }
    }

    /**
     * Returns an override URL for federated compute for the provided package if one exists, else
     * returns empty if a matching override is not found.
     *
     * @param applicationContext the application context.
     * @param packageName the package for which to check for override.
     * @return override URL or empty string if an override is not found.
     */
    public static String getFcServerOverrideUrl(Context applicationContext, String packageName) {
        String url = "";
        // Check for override manifest url property, if package is debuggable
        try {
            if (!PackageUtils.isPackageDebuggable(applicationContext, packageName)) {
                return url;
            }
        } catch (PackageManager.NameNotFoundException nne) {
            sLogger.e(TAG + ": failed to get override URL for package." + nne);
            return url;
        }

        // Check system properties first
        if (SystemProperties.get(OVERRIDE_FC_SERVER_URL_PACKAGE, "").equals(packageName)) {
            String overrideManifestUrl = SystemProperties.get(OVERRIDE_FC_SERVER_URL, "");
            if (!overrideManifestUrl.isEmpty()) {
                sLogger.d(
                        TAG
                                + ": Overriding FC server URL from system properties for package"
                                + packageName
                                + " to "
                                + overrideManifestUrl);
                url = overrideManifestUrl;
            }
        }

        return url;
    }

    private DebugUtils() {}
}
