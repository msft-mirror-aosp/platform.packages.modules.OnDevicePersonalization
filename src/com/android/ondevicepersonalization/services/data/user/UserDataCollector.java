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

package com.android.ondevicepersonalization.services.data.user;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.google.common.base.Strings;

import java.util.Locale;
import java.util.TimeZone;

/**
 * A collector for getting user data signals.
 */
public class UserDataCollector {
    public static final int BYTES_IN_MB = 1048576;

    private static UserDataCollector sSingleton = null;
    private static final String TAG = "UserDataCollector";

    private Context mContext;
    private NetworkCapabilities mNetworkCapabilities;

    private UserDataCollector(Context context) {
        mContext = context;
        ConnectivityManager connectivityManager = mContext.getSystemService(
                ConnectivityManager.class);
        mNetworkCapabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());
    }

    /** Returns an instance of UserDataCollector. */
    public static UserDataCollector getInstance(Context context) {
        synchronized (UserDataCollector.class) {
            if (sSingleton == null) {
                sSingleton = new UserDataCollector(context);
            }
            return sSingleton;
        }
    }

    /** Collects user data signals and stores in a UserData object. */
    public UserData getUserData() {
        UserData userData = new UserData();
        userData.timeMillis = getTimeMillis();
        userData.timeZone = getTimeZone();
        userData.orientation = getOrientation();
        userData.availableBytesMB = getAvailableBytesMB();
        userData.batteryPct = getBatteryPct();
        userData.country = getCountry();
        userData.language = getLanguage();
        userData.carrier = getCarrier();
        userData.connectionType = getConnectionType();
        userData.networkMeteredStatus = getNetworkMeteredStatus();
        userData.connectionSpeedKbps = getConnectionSpeedKbps();

        userData.osVersions = new UserData.OSVersion();
        getOSVersions(userData.osVersions);

        userData.deviceMetrics = new UserData.DeviceMetrics();
        getDeviceMetrics(userData.deviceMetrics);
        return userData;
    }

    /** Collects current system clock on the device. */
    public long getTimeMillis() {
        return System.currentTimeMillis();
    }

    /** Collects current device's time zone information. */
    public TimeZone getTimeZone() {
        return TimeZone.getDefault();
    }

    /** Collects the current device orientation. */
    public int getOrientation() {
        return mContext.getResources().getConfiguration().orientation;
    }

    /** Collects available bytes and converts to MB. */
    public int getAvailableBytesMB() {
        StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
        return (int) (statFs.getAvailableBytes() / BYTES_IN_MB);
    }

    /** Collects the battery percentage of the device. */
    public int getBatteryPct() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            return Math.round(level * 100.0f / (float) scale);
        }
        return 0;
    }

    /** Collects current device's country information. */
    public Country getCountry() {
        String countryCode = Locale.getDefault().getISO3Country();
        if (Strings.isNullOrEmpty(countryCode)) {
            return Country.UNKNOWN;
        } else {
            Country country = Country.UNKNOWN;
            try {
                country = Country.valueOf(countryCode);
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "Country code cannot match to a country.", iae);
                return country;
            }
            return country;
        }
    }

    /** Collects current device's language information. */
    public Language getLanguage() {
        String langCode = Locale.getDefault().getLanguage();
        if (Strings.isNullOrEmpty(langCode)) {
            return Language.UNKNOWN;
        } else {
            Language language = Language.UNKNOWN;
            try {
                language = Language.valueOf(langCode.toUpperCase(Locale.US));
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "Language code cannot match to a language.", iae);
                return language;
            }
            return language;
        }
    }

    /** Collects carrier info. */
    public String getCarrier() {
        return mContext.getSystemService(TelephonyManager.class).getSimOperatorName();
    }

    /** Collects device OS version info.
     * ODA only identifies three valid raw forms of OS releases
     * and convert it to the three-version format.
     * 13 -> 13.0.0
     * 8.1 -> 8.1.0
     * 4.1.2 as it is.
     */
    public void getOSVersions(UserData.OSVersion osVersions) {
        String osRelease = Build.VERSION.RELEASE;
        try {
            osVersions.major = Integer.parseInt(osRelease);
        } catch (NumberFormatException nfe1) {
            try {
                String[] versions = osRelease.split(".");
                if (versions.length == 2) {
                    osVersions.major = Integer.parseInt(versions[0]);
                    osVersions.minor = Integer.parseInt(versions[1]);
                } else if (versions.length == 3) {
                    osVersions.major = Integer.parseInt(versions[0]);
                    osVersions.minor = Integer.parseInt(versions[1]);
                    osVersions.micro = Integer.parseInt(versions[2]);
                } else {
                    // An irregular release like "UpsideDownCake"
                    Log.e(TAG, "OS release string cannot be matched to a regular version.", nfe1);
                }
            } catch (NumberFormatException nfe2) {
                // An irrgular release like "QKQ1.200830.002"
                Log.e(TAG, "OS release string cannot be matched to a regular version.", nfe2);
            }
        }
    }

    /** Collects connection type. */
    public UserData.ConnectionType getConnectionType() {
        if (mNetworkCapabilities == null) {
            return UserData.ConnectionType.UNKNOWN;
        } else if (mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
            switch (telephonyManager.getDataNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                case TelephonyManager.NETWORK_TYPE_GSM:
                    return UserData.ConnectionType.CELLULAR_2G;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    return UserData.ConnectionType.CELLULAR_3G;
                case TelephonyManager.NETWORK_TYPE_LTE:
                case TelephonyManager.NETWORK_TYPE_IWLAN:
                    return UserData.ConnectionType.CELLULAR_4G;
                case TelephonyManager.NETWORK_TYPE_NR:
                    return UserData.ConnectionType.CELLULAR_5G;
                default:
                    return UserData.ConnectionType.UNKNOWN;
            }
        } else if (mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return UserData.ConnectionType.WIFI;
        } else if (mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return UserData.ConnectionType.ETHERNET;
        }
        return UserData.ConnectionType.UNKNOWN;
    }

    /** Collects metered status. */
    public boolean getNetworkMeteredStatus() {
        if (mNetworkCapabilities == null) {
            return false;
        }
        int[] capabilities = mNetworkCapabilities.getCapabilities();
        for (int i = 0; i < capabilities.length; ++i) {
            if (capabilities[i] == NetworkCapabilities.NET_CAPABILITY_NOT_METERED) {
                return false;
            }
        }
        return true;
    }

    /** Collects connection speed in kbps */
    public int getConnectionSpeedKbps() {
        if (mNetworkCapabilities == null) {
            return 0;
        }
        return mNetworkCapabilities.getLinkDownstreamBandwidthKbps();
    }

    /** Collects current device's static metrics. */
    public void getDeviceMetrics(UserData.DeviceMetrics deviceMetrics) {
        deviceMetrics.make = Build.MANUFACTURER;
        deviceMetrics.model = Build.MODEL;
        deviceMetrics.screenHeight = mContext.getResources().getConfiguration().screenHeightDp;
        deviceMetrics.screenWidth = mContext.getResources().getConfiguration().screenWidthDp;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager wm = mContext.getSystemService(WindowManager.class);
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        deviceMetrics.xdpi = displayMetrics.xdpi;
        deviceMetrics.ydpi = displayMetrics.ydpi;
        deviceMetrics.pxRatio = displayMetrics.density;
    }
}
