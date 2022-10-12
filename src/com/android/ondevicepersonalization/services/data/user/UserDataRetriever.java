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
import android.os.Environment;
import android.os.StatFs;

import java.util.TimeZone;

/**
 * A retriever for getting user data signals.
 */
public class UserDataRetriever {
    public static final int BYTES_IN_MB = 1048576;

    private static UserDataRetriever sSingleton = null;

    private Context mContext;

    private UserDataRetriever(Context context) {
        mContext = context;
    }

    /** Returns an instance of UserDataRetriever. */
    public static UserDataRetriever getInstance(Context context) {
        synchronized (UserDataRetriever.class) {
            if (sSingleton == null) {
                sSingleton = new UserDataRetriever(context);
            }
            return sSingleton;
        }
    }

    /** Retrieves user data signals and stores in a UserData object. */
    public UserData getUserData() {
        UserData userData = new UserData();
        userData.timeMillis = getTimeMillis();
        userData.timeZone = getTimeZone();
        userData.orientation = getOrientation();
        userData.availableBytesMB = getAvailableBytesMB();
        userData.batteryPct = getBatteryPct();
        return userData;
    }

    /** Retrieves current system clock on the device. */
    public long getTimeMillis() {
        return System.currentTimeMillis();
    }

    /** Retrieves current device's time zone information. */
    public TimeZone getTimeZone() {
        return TimeZone.getDefault();
    }

    /** Retrieves the current device orientation. */
    public int getOrientation() {
        return mContext.getResources().getConfiguration().orientation;
    }

    /** Retrieves available bytes and converts to MB. */
    public int getAvailableBytesMB() {
        StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
        return (int) (statFs.getAvailableBytes() / BYTES_IN_MB);
    }

    /** Retrieves the battery percentage of the device. */
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
}
