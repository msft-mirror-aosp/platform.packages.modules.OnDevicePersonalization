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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.Log;

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

    private UserDataCollector(Context context) {
        mContext = context;
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
        userData.screenHeight = getScreenHeightInDp();
        userData.screenWidth = getScreenWidthInDp();
        userData.carrier = getCarrier();
        userData.make = getDeviceMake();
        userData.model = getDeviceModel();
        userData.osVersion = getOSVersion();
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

    /** Collects current device's screen height in dp units. */
    public int getScreenHeightInDp() {
        return mContext.getResources().getConfiguration().screenHeightDp;
    }

    /** Collects current device's screen height in dp units. */
    public int getScreenWidthInDp() {
        return mContext.getResources().getConfiguration().screenWidthDp;
    }

    /** Collects carrier info. */
    public String getCarrier() {
        return mContext.getSystemService(TelephonyManager.class).getSimOperatorName();
    }

    /** Collects device make info */
    public String getDeviceMake() {
        return Build.MANUFACTURER;
    }

    /** Collects device model info */
    public String getDeviceModel() {
        return Build.MODEL;
    }

    /** Collects device OS version info */
    public String getOSVersion() {
        return Build.VERSION.RELEASE;
    }
}
