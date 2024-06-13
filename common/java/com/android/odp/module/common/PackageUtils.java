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

package com.android.odp.module.common;

import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static android.federatedcompute.common.ClientConstants.ODP_AOSP_BUILT_APEX_NAME;
import static android.federatedcompute.common.ClientConstants.ODP_APEX_KEYWORD;
import static android.federatedcompute.common.ClientConstants.ODP_MAINLINE_SIGNED_APEX_NAME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import libcore.util.HexEncoding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * PackageUtils for OnDevicePersonalization module.
 *
 * @hide
 */
public class PackageUtils {
    private PackageUtils() {
    }

    /**
     * Computes the SHA256 digest of some data.
     *
     * @param data The data.
     * @return The digest or null if an error occurs.
     */
    @Nullable
    public static byte[] computeSha256DigestBytes(@NonNull byte[] data) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            /* can't happen */
            return null;
        }

        messageDigest.update(data);

        return messageDigest.digest();
    }

    /**
     * Retrieves the certDigest of the given packageName
     *
     * @param context Context of the calling service
     * @param packageName Package name owning the certDigest
     * @return certDigest of the given packageName
     */
    @Nullable
    public static String getCertDigest(@NonNull Context context, @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        PackageInfo sdkPackageInfo =
                context.getPackageManager()
                        .getPackageInfo(
                                packageName,
                                PackageManager.PackageInfoFlags.of(
                                        GET_SIGNING_CERTIFICATES
                                                | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
        SigningInfo signingInfo = sdkPackageInfo.signingInfo;
        Signature[] signatures =
                signingInfo != null ? signingInfo.getSigningCertificateHistory() : null;
        byte[] digest = PackageUtils.computeSha256DigestBytes(signatures[0].toByteArray());
        return new String(HexEncoding.encode(digest));
    }

    /**
     * Determines if a package is debuggable
     *
     * @return true if the package is debuggable, false otherwise
     */
    public static boolean isPackageDebuggable(@NonNull Context context, @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        ApplicationInfo sdkApplicationInfo =
                context.getPackageManager()
                        .getApplicationInfo(
                                packageName, PackageManager.ApplicationInfoFlags.of(GET_META_DATA));
        return (sdkApplicationInfo.flags &= ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /**
     * Get the apex version of OnDevicePersonalization.
     *
     * @param context The context of the calling process.
     * @return The long version code of OnDevicePersonalization apex. If there is no name match,
     *     then return -1L.
     */
    public static long getApexVersion(Context context) {
        try {
            PackageInfo odpMainlineApexInfo = context.getPackageManager()
                    .getPackageInfo(ODP_MAINLINE_SIGNED_APEX_NAME, PackageManager.MATCH_APEX);
            if (odpMainlineApexInfo != null && odpMainlineApexInfo.isApex) {
                return odpMainlineApexInfo.getLongVersionCode();
            }

            PackageInfo odpAospApexInfo = context.getPackageManager()
                    .getPackageInfo(ODP_AOSP_BUILT_APEX_NAME, PackageManager.MATCH_APEX);
            if (odpAospApexInfo != null && odpAospApexInfo.isApex) {
                return odpAospApexInfo.getLongVersionCode();
            }
        } catch (PackageManager.NameNotFoundException e) {
        }

        List<PackageInfo> installedPackages =
                context.getPackageManager().getInstalledPackages(
                        PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX));
        for (PackageInfo pkg : installedPackages) {
            if (pkg.packageName.contains(ODP_APEX_KEYWORD) && pkg.isApex) {
                return pkg.getLongVersionCode();
            }
        }
        return -1L;
    }
}
