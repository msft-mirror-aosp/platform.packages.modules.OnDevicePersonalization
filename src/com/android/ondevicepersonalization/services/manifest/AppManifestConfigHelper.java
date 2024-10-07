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

package com.android.ondevicepersonalization.services.manifest;

import static android.content.pm.PackageManager.GET_META_DATA;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.enrollment.PartnerEnrollmentChecker;

import com.google.common.collect.ImmutableList;

/**
 * Helper class for parsing and checking app manifest configs
 */
public final class AppManifestConfigHelper {
    private static final String ON_DEVICE_PERSONALIZATION_CONFIG_PROPERTY =
            "android.ondevicepersonalization.ON_DEVICE_PERSONALIZATION_CONFIG";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AppManifestConfigHelper.class.getSimpleName();

    private AppManifestConfigHelper() {
    }

    /**
     * Determines if the given package's manifest contains ODP settings
     *
     * @param context the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     * @return true if the ODP setting exists, false otherwise
     */
    public static boolean manifestContainsOdpSettings(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getProperty(ON_DEVICE_PERSONALIZATION_CONFIG_PROPERTY, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Returns the ODP manifest config for a package.
     *
     * <p>Throws a {@link RuntimeException} if the package, its ODP settings are not found or cannot
     * be parsed.
     */
    /** Returns the ODP manifest config for a package. */
    public static AppManifestConfig getAppManifestConfig(Context context, String packageName) {
        if (!manifestContainsOdpSettings(context, packageName)) {
            // TODO(b/241941021) Determine correct exception to throw
            throw new IllegalArgumentException(
                    "OdpSettings not found for package: " + packageName.toString());
        }
        PackageManager pm = context.getPackageManager();
        try {
            int resId = pm.getProperty(ON_DEVICE_PERSONALIZATION_CONFIG_PROPERTY,
                    packageName).getResourceId();
            Resources resources = pm.getResourcesForApplication(packageName);
            XmlResourceParser xmlParser = resources.getXml(resId);
            // TODO(b/239479120) Update to avoid re-parsing the XML too frequently if required
            return AppManifestConfigParser.getConfig(xmlParser);
        } catch (Exception e) {
            // TODO(b/241941021) Determine correct exception to throw
            throw new IllegalArgumentException(
                    "Failed to parse manifest for package: " + packageName, e);
        }
    }

    /**
     * Gets the download URL from package's ODP settings config
     *
     * @param context     the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     */
    public static String getDownloadUrlFromOdpSettings(Context context, String packageName) {
        return getAppManifestConfig(context, packageName).getDownloadUrl();
    }

    /**
     * Gets the service name from package's ODP settings config
     *
     * @param context     the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     */
    public static String getServiceNameFromOdpSettings(Context context,
            String packageName) {
        return getAppManifestConfig(context, packageName).getServiceName();
    }

    /**
     * Gets the federated compute service remote server url from package's ODP settings config
     *
     * @param context     the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     */
    public static String getFcRemoteServerUrlFromOdpSettings(Context context,
            String packageName) {
        return getAppManifestConfig(context, packageName).getFcRemoteServerUrl();
    }

    /**
     * Get the list of packages enrolled for ODP, by checking manifest for ODP settings.
     *
     * @param context The context of the calling process.
     * @param enrolledOnly Whether to only include packages that pass the enrollment check.
     * @return The list of packages that contain ODP manifest settings (and potentially enrolled)
     */
    public static ImmutableList<String> getOdpPackages(Context context, boolean enrolledOnly) {
        ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
        for (PackageInfo packageInfo :
                context.getPackageManager()
                        .getInstalledPackages(PackageManager.PackageInfoFlags.of(GET_META_DATA))) {
            String packageName = packageInfo.packageName;

            if (manifestContainsOdpSettings(context, packageName)) {
                if (enrolledOnly
                        && !PartnerEnrollmentChecker.isIsolatedServiceEnrolled(packageName)) {
                    sLogger.d(TAG + ": package %s has ODP manifest, but not enrolled", packageName);
                    continue;
                }

                String enrolledString = enrolledOnly ? "and is enrolled" : "";
                sLogger.d(TAG + ": package %s has ODP manifest " + enrolledString, packageName);
                builder.add(packageName);
            }
        }
        return builder.build();
    }

    /**
     * Get the list of services enrolled for ODP.
     *
     * @param context The context of the calling process.
     * @param enrolledOnly Whether to only include services that pass the enrollment check.
     * @return The list of matching Services with ODP manifest settings (and are potentially
     *     enrolled).
     */
    public static ImmutableList<ComponentName> getOdpServices(
            Context context, boolean enrolledOnly) {
        ImmutableList.Builder<ComponentName> builder = new ImmutableList.Builder<>();
        for (PackageInfo packageInfo :
                context.getPackageManager()
                        .getInstalledPackages(PackageManager.PackageInfoFlags.of(GET_META_DATA))) {
            String packageName = packageInfo.packageName;

            if (manifestContainsOdpSettings(context, packageName)) {
                if (enrolledOnly
                        && !PartnerEnrollmentChecker.isIsolatedServiceEnrolled(packageName)) {
                    sLogger.d(TAG + ": service %s has ODP manifest, but not enrolled", packageName);
                    continue;
                }

                String enrolledString = enrolledOnly ? "and is enrolled" : "";
                sLogger.d(TAG + ": service %s has ODP manifest " + enrolledString, packageName);

                String serviceClass = getServiceNameFromOdpSettings(context, packageName);
                ComponentName service = ComponentName.createRelative(packageName, serviceClass);
                builder.add(service);
            }
        }
        return builder.build();
    }
}
