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

import android.content.res.Configuration;

import java.util.TimeZone;

/**
 * A data class that holds user data to be sent to ad vendors.
 */
public final class UserData {
    // The current system time in milliseconds.
    public long timeMillis = 0;

    // The device time zone.
    public TimeZone timeZone = null;

    // The device orientation.
    public int orientation = Configuration.ORIENTATION_PORTRAIT;

    // Available bytes in MB.
    public int availableBytesMB = 0;

    // Battery percentage.
    public int batteryPct = 0;

    // The 3-letter ISO-3166 country code
    public Country country = Country.UNKNOWN;

    // The 2-letter ISO-639 language code
    public Language language = Language.UNKNOWN;

    // Mobile carrier.
    public Carrier carrier = Carrier.UNKNOWN;

    // OS version of the device
    public String osVersion = "";

    // Connection type values.
    public enum ConnectionType {
        UNKNOWN,
        ETHERNET,
        WIFI,
        CELLULAR_2G,
        CELLULAR_3G,
        CELLULAR_4G,
        CELLULAR_5G
    };

    // Connection type.
    public ConnectionType connectionType = ConnectionType.UNKNOWN;

    // Status if network is metered. False - not metered. True - metered.
    public boolean networkMeteredStatus = false;

    // Connection speed in kbps.
    public int connectionSpeedKbps = 0;

    /** Constant device metrics values. */
    public static class DeviceMetrics {
        // Device manufacturer
        public String make = "";

        // Device model
        public String model = "";

        // Screen height of the device in dp units
        public int screenHeight = Configuration.SCREEN_HEIGHT_DP_UNDEFINED;

        // Screen weight of the device in dp units
        public int screenWidth = Configuration.SCREEN_WIDTH_DP_UNDEFINED;

        // Device x dpi;
        public float xdpi = 0;

        // Device y dpi;
        public float ydpi = 0;

        // Dveice pixel ratio.
        public float pxRatio = 0;
    }

    // Device metrics values.
    public DeviceMetrics deviceMetrics = null;
}
