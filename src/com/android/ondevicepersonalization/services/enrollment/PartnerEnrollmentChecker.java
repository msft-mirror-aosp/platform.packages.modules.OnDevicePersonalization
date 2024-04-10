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

package com.android.ondevicepersonalization.services.enrollment;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.util.AllowListUtils;
import com.android.ondevicepersonalization.services.util.PackageUtils;

/** Check if an entity is enrolled to call ODP */
public class PartnerEnrollmentChecker {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = PartnerEnrollmentChecker.class.getSimpleName();

    /** check if a caller app is enrolled based on package name and certificate*/
    public static boolean isCallerAppEnrolled(final String packageName) {
        boolean isEnrolled = true;

        // Enrollment check #1: packageName or packageName + certificate should be in allow list
        final String callerAppAllowList = FlagsFactory.getFlags().getCallerAppAllowList();
        String packageCertificate = null;
        try {
            packageCertificate = PackageUtils.getCertDigest(packageName);
        } catch (Exception e) {
            sLogger.d(TAG + ": not able to find certificate for package " + packageName, e);
        }

        boolean isCallerAppAllowListed = AllowListUtils.isAllowListed(
                packageName,
                packageCertificate,
                callerAppAllowList);
        isEnrolled = isEnrolled && isCallerAppAllowListed;
        if (!isEnrolled) {
            return isEnrolled;
        }

        // Add more enrollment checks below
        return isEnrolled;
    }

    /** check if an isolated service is enrolled based on package name and certificate*/
    public static boolean isIsolatedServiceEnrolled(final String packageName) {
        boolean isEnrolled = true;

        // Enrollment check #1: packageName or packageName + certificate should be in allow list
        final String isolatedServiceAllowList =
                FlagsFactory.getFlags().getIsolatedServiceAllowList();
        String packageCertificate = null;
        try {
            packageCertificate = PackageUtils.getCertDigest(packageName);
        } catch (Exception e) {
            sLogger.d(TAG + ": not able to find certificate for package " + packageName, e);
        }

        boolean isIsolatedServiceAllowListed = AllowListUtils.isAllowListed(
                packageName,
                packageCertificate,
                isolatedServiceAllowList);
        isEnrolled = isEnrolled && isIsolatedServiceAllowListed;
        if (!isEnrolled) {
            return isEnrolled;
        }

        // Add more enrollment checks below
        return isEnrolled;
    }
}
