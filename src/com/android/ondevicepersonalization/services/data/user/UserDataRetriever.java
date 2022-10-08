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

import java.util.TimeZone;

/**
 * A retriever for getting user data signals.
 */
public class UserDataRetriever {
    private Context mContext;
    private static UserDataRetriever sSingleton = null;

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

    public int getOrientation() {
        return mContext.getResources().getConfiguration().orientation;
    }
}
