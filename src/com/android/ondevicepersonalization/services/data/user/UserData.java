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

import java.util.List;

/**
 * A data class that holds user data to be sent to ad vendors.
 */
public final class UserData {
    // The current system time in milliseconds.
    public long timeMillis = 0;

    // The device time zone +/- minutes offset from UTC.
    public int utcOffset = 0;

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

    /** Values for OS versions. */
    public static class OSVersion {
        public int major = 0;
        public int minor = 0;
        public int micro = 0;
    }

    // OS versions of the device.
    public OSVersion osVersions;

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
        public Make make = Make.UNKNOWN;

        // Device model
        public Model model = Model.UNKNOWN;

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

    /** Application information on device. */
    public static class AppInfo {
        // Application package name.
        public String packageName = null;

        // Whether the application is installed or uninstalled.
        public boolean installed = false;
    }

    // installed packages.
    public List<AppInfo> appsInfo = null;

    /** Application usage stats. */
    public static class AppUsageStats {
        // Application package name.
        public String packageName = null;

        // Starting time in milliseconds.
        public long startTimeMillis = 0;

        // Ending time in milliseconds.
        public long endTimeMillis = 0;

        // Total time that the app is visible in seconds.
        public long totalTimeSec = 0;
    }

    // Application usage stats.
    public List<AppUsageStats> appsUsageStats = null;

    // Location provider values.
    public enum LocationProvider {
        UNKNOWN,
        GPS,
        NETWORK,
    };

    /** Location information. */
    public static class LocationInfo {
        // Time in milliseconds.
        public long timeMillis = 0;

        // Latitude.
        public double latitude = 0;

        // Longitude.
        public double longitude = 0;

        // Location provider.
        public LocationProvider provider = LocationProvider.UNKNOWN;

        // Whether the location source is precise.
        public boolean isPreciseLocation = false;
    }

    // Location info.
    public LocationInfo locationInfo = null;
}
