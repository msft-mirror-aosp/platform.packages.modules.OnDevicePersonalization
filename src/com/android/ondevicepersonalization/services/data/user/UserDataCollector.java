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

import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
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

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A collector for getting user data signals.
 */
public class UserDataCollector {
    public static final int BYTES_IN_MB = 1048576;

    private static UserDataCollector sSingleton = null;
    private static final String TAG = "UserDataCollector";

    @NonNull private final Context mContext;
    @NonNull private Locale mLocale;
    @NonNull private final TelephonyManager mTelephonyManager;
    @NonNull private final NetworkCapabilities mNetworkCapabilities;

    private UserDataCollector(Context context) {
        mContext = context;

        mLocale = Locale.getDefault();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
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

        userData.appsInfo = new ArrayList();
        getInstalledApps(userData.appsInfo);

        userData.appsUsageStats = new ArrayList();
        getAppUsageStats(userData.appsUsageStats);

        userData.locationInfo = new UserData.LocationInfo();
        getCurrentLocation(userData.locationInfo);

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
        String countryCode = mLocale.getISO3Country();
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
        String langCode = mLocale.getLanguage();
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
    public Carrier getCarrier() {
        // TODO: handle i18n later if the carrier's name is in non-English script.
        switch (mTelephonyManager.getSimOperatorName().toUpperCase(Locale.US)) {
            case "RELIANCE JIO":
                return Carrier.RELIANCE_JIO;
            case "VODAFONE":
                return Carrier.VODAFONE;
            case "T-MOBILE - US":
            case "T-MOBILE":
                return Carrier.T_MOBILE;
            case "VERIZON WIRELESS":
                return Carrier.VERIZON_WIRELESS;
            case "AIRTEL":
                return Carrier.AIRTEL;
            case "ORANGE":
                return Carrier.ORANGE;
            case "NTT DOCOMO":
                return Carrier.NTT_DOCOMO;
            case "MOVISTAR":
                return Carrier.MOVISTAR;
            case "AT&T":
                return Carrier.AT_T;
            case "TELCEL":
                return Carrier.TELCEL;
            case "VIVO":
                return Carrier.VIVO;
            case "VI":
                return Carrier.VI;
            case "TIM":
                return Carrier.TIM;
            case "O2":
                return Carrier.O2;
            case "TELEKOM":
                return Carrier.TELEKOM;
            case "CLARO BR":
                return Carrier.CLARO_BR;
            case "SK TELECOM":
                return Carrier.SK_TELECOM;
            case "МТС":
                return Carrier.MTC;
            case "AU":
                return Carrier.AU;
            case "TELE2":
                return Carrier.TELE2;
            case "SFR":
                return Carrier.SFR;
            case "ETECSA":
                return Carrier.ETECSA;
            case "IR-MCI (HAMRAHE AVVAL)":
                return Carrier.IR_MCI;
            case "KT":
                return Carrier.KT;
            case "TELKOMSEL":
                return Carrier.TELKOMSEL;
            case "IRANCELL":
                return Carrier.IRANCELL;
            case "MEGAFON":
                return Carrier.MEGAFON;
            case "TELEFONICA":
                return Carrier.TELEFONICA;
            default:
                return Carrier.UNKNOWN;
        }
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
            switch (mTelephonyManager.getDataNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_GSM:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return UserData.ConnectionType.CELLULAR_2G;
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                case TelephonyManager.NETWORK_TYPE_UMTS:
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
        deviceMetrics.make = getDeviceMake();
        deviceMetrics.model = getDeviceModel();
        deviceMetrics.screenHeight = mContext.getResources().getConfiguration().screenHeightDp;
        deviceMetrics.screenWidth = mContext.getResources().getConfiguration().screenWidthDp;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager wm = mContext.getSystemService(WindowManager.class);
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        deviceMetrics.xdpi = displayMetrics.xdpi;
        deviceMetrics.ydpi = displayMetrics.ydpi;
        deviceMetrics.pxRatio = displayMetrics.density;
    }

    /**
     * Collects device make info.
     */
    private Make getDeviceMake() {
        String manufacturer = Build.MANUFACTURER.toUpperCase(Locale.US);
        Make make = Make.UNKNOWN;
        try {
            make = Make.valueOf(manufacturer);
        } catch (IllegalArgumentException iae) {
            // handle corner cases for irregularly formatted string.
            make = getMakeFromSpecialString(manufacturer);
            if (make == Make.UNKNOWN) {
                Log.e(TAG, "Manufacturer string cannot match to an available make type.", iae);
            }
            return make;
        }
        return make;
    }

    /** Collects device model info */
    private Model getDeviceModel() {
        // Uppercase and replace whitespace/hyphen with underscore character
        String deviceModel = Build.MODEL.toUpperCase(Locale.US).replace(' ', '_').replace('-', '_');
        Model model = Model.UNKNOWN;
        try {
            model = Model.valueOf(deviceModel);
        } catch (IllegalArgumentException iae) {
            // handle corner cases for irregularly formatted string.
            model = getModelFromSpecialString(deviceModel);
            if (model == Model.UNKNOWN) {
                Log.e(TAG, "Model string cannot match to an available make type.", iae);
            }
            return model;
        }
        return model;
    }

    /**
     * Helper function that handles irregularly formatted manufacturer string,
     * which cannot be directly cast into enums.
     */
    private Make getMakeFromSpecialString(String deviceMake) {
        switch (deviceMake) {
            case "TECNO MOBILE LIMITED":
                return Make.TECNO;
            case "INFINIX MOBILITY LIMITED":
                return Make.INFINIX;
            case "HMD GLOBAL":
                return Make.HMD_GLOBAL;
            case "LGE":
            case "LG ELECTRONICS":
                return Make.LG_ELECTRONICS;
            case "SKYWORTHDIGITAL":
                return Make.SKYWORTH;
            case "ITEL":
            case "ITEL MOBILE LIMITED":
                return Make.ITEL_MOBILE;
            case "KAON":
            case "KAONMEDIA":
                return Make.KAON_MEDIA;
            case "ZEBRA TECHNOLOGIES":
                return Make.ZEBRA_TECHNOLOGIES;
            case "VOLVOCARS":
                return Make.VOLVO_CARS;
            case "SUMITOMOELECTRICINDUSTRIES":
                return Make.SUMITOMO_ELECTRIC_INDUSTRIES;
            case "STARHUB":
                return Make.STAR_HUB;
            case "GENERALMOBILE":
                return Make.GENERAL_MOBILE;
            case "KONNECTONE":
                return Make.KONNECT_ONE;
            case "CASIO COMPUTER CO., LTD.":
                return Make.CASIO_COMPUTER;
            case "SEI":
            case "SEI ROBOTICS":
                return Make.SEI_ROBOTICS;
            case "EASTAEON":
                return Make.EAST_AEON;
            case "HIMEDIA":
                return Make.HI_MEDIA;
            case "HOT PEPPER INC":
                return Make.HOT_PEPPER;
            case "SKY":
            case "SKY DEVICES":
            case "SKYDEVICES":
                return Make.SKY_DEVICES;
            case "FOXX DEVELOPMENT INC.":
                return Make.FOXX_DEVELOPMENT;
            case "RELIANCE COMMUNICATIONS":
                return Make.RELIANCE_COMMUNICATIONS;
            case "EMPORIA TELECOM GMBH & CO. KG":
                return Make.EMPORIA;
            case "CIPHERLAB":
                return Make.CIPHER_LAB;
            case "ISAFEMOBILE":
                return Make.ISAFE_MOBILE;
            case "CLARO COLUMBIA":
                return Make.CLARO;
            case "MYPHONE":
                return Make.MY_PHONE;
            case "TAG HEUER":
                return Make.TAG_HEUER;
            case "XWIRELESSLLC":
                return Make.XWIRELESS;
            default:
                return Make.UNKNOWN;
        }
    }

    /**
     * Helper function that handles irregularly formatted model string,
     * which cannot be directly cast into enums.
     */
    private Model getModelFromSpecialString(String deviceModel) {
        switch (deviceModel) {
            case "AT&T_TV":
                return Model.ATT_TV;
            case "XVIEW+":
                return Model.XVIEW_PLUS;
            case "2201117TG":
                return Model.REDMI_2201117TG;
            case "2201117TY":
                return Model.REDMI_2201117TY;
            case "B860H_V5.0":
                return Model.B860H_V5;
            case "MOTO_G(20)":
                return Model.MOTO_G20;
            case "2020/2021_UHD_ANDROID_TV":
                return Model.PHILIPS_2020_2021_UHD_ANDROID_TV;
            case "2109119DG":
                return Model.XIAOMI_2109119DG;
            case "MOTO_G(9)_PLAY":
                return Model.MOTO_G9_PLAY;
            case "MOTO_E(7)":
                return Model.MOTO_E7;
            case "2021/22_PHILIPS_UHD_ANDROID_TV":
                return Model.PHILIPS_2021_22_UHD_ANDROID_TV;
            case "MOTO_G(30)":
                return Model.MOTO_G30;
            case "MOTO_G_POWER_(2021)":
                return Model.MOTO_G_POWER_2021;
            case "MOTO_G(7)_POWER":
                return Model.MOTO_G7_POWER;
            case "OTT_XVIEW+_AV1":
                return Model.OTT_XVIEW_PLUS_AV1;
            default:
                return Model.UNKNOWN;
        }
    }

    /** Get app install and uninstall record. */
    public void getInstalledApps(List<UserData.AppInfo> appsInfo) {
        PackageManager packageManager = mContext.getPackageManager();
        for (ApplicationInfo appInfo :
                packageManager.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES)) {
            UserData.AppInfo app = new UserData.AppInfo();
            app.packageName = appInfo.packageName;
            if ((appInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0) {
                app.installed = true;
            } else {
                app.installed = false;
            }
            appsInfo.add(app);
        }
    }

    /** Get app usage stats for the last 24 hours. */
    // Todo(b/246132780): change the query time range to prevent overlaps.
    public void getAppUsageStats(List<UserData.AppUsageStats> appsUsageStats) {
        UsageStatsManager usageStatsManager = mContext.getSystemService(UsageStatsManager.class);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        final long startTimeMillis = cal.getTimeInMillis();
        final long endTimeMillis = System.currentTimeMillis();
        final List<UsageStats> statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTimeMillis, endTimeMillis);
        for (UsageStats stats : statsList) {
            UserData.AppUsageStats appUsageStats = new UserData.AppUsageStats();
            appUsageStats.packageName = stats.getPackageName();
            appUsageStats.startTimeMillis = startTimeMillis;
            appUsageStats.endTimeMillis = endTimeMillis;
            appUsageStats.totalTimeSec = stats.getTotalTimeVisible();
            appsUsageStats.add(appUsageStats);
        }
    }

    /** Get current location information. */
    public void getCurrentLocation(UserData.LocationInfo locationInfo) {
        LocationManager locationManager = mContext.getSystemService(LocationManager.class);
        String currentProvider = LocationManager.GPS_PROVIDER;
        if (locationManager.getProvider(currentProvider) == null) {
            currentProvider = LocationManager.FUSED_PROVIDER;
        }
        // List<String> providers = locationManager.getAllProviders();
        locationManager.getCurrentLocation(
                currentProvider,
                null,
                mContext.getMainExecutor(),
                location -> {
                    if (location != null) {
                        locationInfo.timeMillis = getTimeMillis();
                        locationInfo.latitude = location.getLatitude();
                        locationInfo.longitude = location.getLongitude();
                        String provider = location.getProvider();
                        if (LocationManager.GPS_PROVIDER.equals(provider)) {
                            locationInfo.provider = UserData.LocationProvider.GPS;
                            locationInfo.isPreciseLocation = true;
                        } else {
                            locationInfo.isPreciseLocation = false;
                            if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                                locationInfo.provider = UserData.LocationProvider.NETWORK;
                            } else {
                                locationInfo.provider = UserData.LocationProvider.UNKNOWN;
                            }
                        }
                    }
                }
        );
    }

    /**
     * Setter to update locale for testing purpose.
     */
    @VisibleForTesting
    public void setLocale(Locale locale) {
        mLocale = locale;
    }
}
