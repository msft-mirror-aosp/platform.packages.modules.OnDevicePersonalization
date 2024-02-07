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

/** Check if an entity is enrolled to call ODP */
public class PartnerEnrollmentChecker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = PartnerEnrollmentChecker.class.getSimpleName();

    /** check if a caller app is enrolled based on package name */
    public static boolean isCallerAppEnrolled(final String packageName) {
        boolean isEnrolled = true;

        // Enrollment check #1: packageName should be in allow list
        final String callerAppAllowList = FlagsFactory.getFlags().getCallerAppAllowList();
        boolean isCallerAppAllowListed =
                AllowListUtils.isAllowListed(packageName, callerAppAllowList);
        isEnrolled = isEnrolled && isCallerAppAllowListed;
        if (!isEnrolled) {
            sLogger.w(TAG + ": caller app " + packageName
                    + " is not enrolled to call ODP, not in allow list");
            return isEnrolled;
        }

        // Add more enrollment checks below
        return isEnrolled;
    }

    /** check if an isolated service is enrolled based on package name */
    public static boolean isIsolatedServiceEnrolled(final String packageName) {
        boolean isEnrolled = true;

        // Enrollment check #1: packageName should be in allow list
        final String isolatedServiceAllowList =
                FlagsFactory.getFlags().getIsolatedServiceAllowList();
        boolean isIsolatedServiceAllowListed =
                AllowListUtils.isAllowListed(packageName, isolatedServiceAllowList);
        isEnrolled = isEnrolled && isIsolatedServiceAllowListed;
        if (!isEnrolled) {
            sLogger.w(TAG + ": isolated service " + packageName
                    + " is not enrolled to access ODP, not in allow list");
            return isEnrolled;
        }

        // Add more enrollment checks below
        return isEnrolled;
    }
}