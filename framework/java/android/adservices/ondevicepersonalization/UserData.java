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

package android.adservices.ondevicepersonalization;

import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User data provided by the platform to an {@link IsolatedService}.
 *
 * @hide
 */
// This class should be updated with the Kotlin mirror
// {@link com.android.ondevicepersonalization.services.policyengine.data.UserData}.
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class UserData implements Parcelable {
    /** The device timezone +/- minutes offset from UTC. */
    int mTimezoneUtcOffsetMins = 0;

    /**
     * The device orientation. The value can be one of the constants ORIENTATION_UNDEFINED,
     * ORIENTATION_PORTRAIT or ORIENTATION_LANDSCAPE defined in
     * {@link android.content.res.Configuration}.
     */
    int mOrientation = 0;

    /** The available space on device in bytes. */
    @IntRange(from = 0) long mAvailableStorageBytes = 0;

    /** Battery percentage. */
    @IntRange(from = 0, to = 100) int mBatteryPercentage = 0;

    /** The name of the carrier. */
    @NonNull String mCarrier = "";

    /** Connection type unknown. @hide */
    public static final int CONNECTION_TYPE_UNKNOWN = 0;
    /** Connection type ethernet. @hide */
    public static final int CONNECTION_TYPE_ETHERNET = 1;
    /** Connection type wifi. @hide */
    public static final int CONNECTION_TYPE_WIFI = 2;
    /** Connection type cellular 2G. @hide */
    public static final int CONNECTION_TYPE_CELLULAR_2G = 3;
    /** Connection type cellular 3G. @hide */
    public static final int CONNECTION_TYPE_CELLULAR_3G = 4;
    /** Connection type cellular 4G. @hide */
    public static final int CONNECTION_TYPE_CELLULAR_4G = 5;
    /** Connection type cellular 5G. @hide */
    public static final int CONNECTION_TYPE_CELLULAR_5G = 6;

    /** Connection types. @hide */
    @ConnectionType int mConnectionType = 0;

    /** Network connection speed in kbps. 0 if no network connection is present. @hide */
    @IntRange(from = 0) long mNetworkConnectionSpeedKbps = 0;

    /** Whether the network is metered. False - not metered. True - metered. @hide */
    boolean mNetworkMetered = false;

    /** Information about installed and uninstalled apps. */
    @NonNull Map<String, AppInfo> mAppInfo = Collections.emptyMap();

    /** The app usage history in the last 30 days, sorted by total time spent. @hide */
    @NonNull List<AppUsageStatus> mAppUsageHistory = Collections.emptyList();

    /** The most recently known location. @hide */
    @NonNull Location mCurrentLocation = Location.EMPTY;

    /** The location history in last 30 days, sorted by the stay duration. @hide */
    @NonNull List<LocationStatus> mLocationHistory = Collections.emptyList();



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/UserData.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /** @hide */
    @IntDef(prefix = "CONNECTION_TYPE_", value = {
        CONNECTION_TYPE_UNKNOWN,
        CONNECTION_TYPE_ETHERNET,
        CONNECTION_TYPE_WIFI,
        CONNECTION_TYPE_CELLULAR_2G,
        CONNECTION_TYPE_CELLULAR_3G,
        CONNECTION_TYPE_CELLULAR_4G,
        CONNECTION_TYPE_CELLULAR_5G
    })
    @Retention(RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface ConnectionType {}

    /** @hide */
    @DataClass.Generated.Member
    @NonNull public static String connectionTypeToString(@ConnectionType int value) {
        switch (value) {
            case CONNECTION_TYPE_UNKNOWN:
                    return "CONNECTION_TYPE_UNKNOWN";
            case CONNECTION_TYPE_ETHERNET:
                    return "CONNECTION_TYPE_ETHERNET";
            case CONNECTION_TYPE_WIFI:
                    return "CONNECTION_TYPE_WIFI";
            case CONNECTION_TYPE_CELLULAR_2G:
                    return "CONNECTION_TYPE_CELLULAR_2G";
            case CONNECTION_TYPE_CELLULAR_3G:
                    return "CONNECTION_TYPE_CELLULAR_3G";
            case CONNECTION_TYPE_CELLULAR_4G:
                    return "CONNECTION_TYPE_CELLULAR_4G";
            case CONNECTION_TYPE_CELLULAR_5G:
                    return "CONNECTION_TYPE_CELLULAR_5G";
            default: return Integer.toHexString(value);
        }
    }

    @DataClass.Generated.Member
    /* package-private */ UserData(
            int timezoneUtcOffsetMins,
            int orientation,
            @IntRange(from = 0) long availableStorageBytes,
            @IntRange(from = 0, to = 100) int batteryPercentage,
            @NonNull String carrier,
            @ConnectionType int connectionType,
            @IntRange(from = 0) long networkConnectionSpeedKbps,
            boolean networkMetered,
            @NonNull Map<String,AppInfo> appInfo,
            @NonNull List<AppUsageStatus> appUsageHistory,
            @NonNull Location currentLocation,
            @NonNull List<LocationStatus> locationHistory) {
        this.mTimezoneUtcOffsetMins = timezoneUtcOffsetMins;
        this.mOrientation = orientation;
        this.mAvailableStorageBytes = availableStorageBytes;
        AnnotationValidations.validate(
                IntRange.class, null, mAvailableStorageBytes,
                "from", 0);
        this.mBatteryPercentage = batteryPercentage;
        AnnotationValidations.validate(
                IntRange.class, null, mBatteryPercentage,
                "from", 0,
                "to", 100);
        this.mCarrier = carrier;
        AnnotationValidations.validate(
                NonNull.class, null, mCarrier);
        this.mConnectionType = connectionType;

        if (!(mConnectionType == CONNECTION_TYPE_UNKNOWN)
                && !(mConnectionType == CONNECTION_TYPE_ETHERNET)
                && !(mConnectionType == CONNECTION_TYPE_WIFI)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_2G)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_3G)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_4G)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_5G)) {
            throw new java.lang.IllegalArgumentException(
                    "connectionType was " + mConnectionType + " but must be one of: "
                            + "CONNECTION_TYPE_UNKNOWN(" + CONNECTION_TYPE_UNKNOWN + "), "
                            + "CONNECTION_TYPE_ETHERNET(" + CONNECTION_TYPE_ETHERNET + "), "
                            + "CONNECTION_TYPE_WIFI(" + CONNECTION_TYPE_WIFI + "), "
                            + "CONNECTION_TYPE_CELLULAR_2G(" + CONNECTION_TYPE_CELLULAR_2G + "), "
                            + "CONNECTION_TYPE_CELLULAR_3G(" + CONNECTION_TYPE_CELLULAR_3G + "), "
                            + "CONNECTION_TYPE_CELLULAR_4G(" + CONNECTION_TYPE_CELLULAR_4G + "), "
                            + "CONNECTION_TYPE_CELLULAR_5G(" + CONNECTION_TYPE_CELLULAR_5G + ")");
        }

        this.mNetworkConnectionSpeedKbps = networkConnectionSpeedKbps;
        AnnotationValidations.validate(
                IntRange.class, null, mNetworkConnectionSpeedKbps,
                "from", 0);
        this.mNetworkMetered = networkMetered;
        this.mAppInfo = appInfo;
        AnnotationValidations.validate(
                NonNull.class, null, mAppInfo);
        this.mAppUsageHistory = appUsageHistory;
        AnnotationValidations.validate(
                NonNull.class, null, mAppUsageHistory);
        this.mCurrentLocation = currentLocation;
        AnnotationValidations.validate(
                NonNull.class, null, mCurrentLocation);
        this.mLocationHistory = locationHistory;
        AnnotationValidations.validate(
                NonNull.class, null, mLocationHistory);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The device timezone +/- minutes offset from UTC.
     */
    @DataClass.Generated.Member
    public int getTimezoneUtcOffsetMins() {
        return mTimezoneUtcOffsetMins;
    }

    /**
     * The device orientation. The value can be one of the constants ORIENTATION_UNDEFINED,
     * ORIENTATION_PORTRAIT or ORIENTATION_LANDSCAPE defined in
     * {@link android.content.res.Configuration}.
     */
    @DataClass.Generated.Member
    public int getOrientation() {
        return mOrientation;
    }

    /**
     * The available space on device in bytes.
     */
    @DataClass.Generated.Member
    public @IntRange(from = 0) long getAvailableStorageBytes() {
        return mAvailableStorageBytes;
    }

    /**
     * Battery percentage.
     */
    @DataClass.Generated.Member
    public @IntRange(from = 0, to = 100) int getBatteryPercentage() {
        return mBatteryPercentage;
    }

    /**
     * The name of the carrier.
     */
    @DataClass.Generated.Member
    public @NonNull String getCarrier() {
        return mCarrier;
    }

    /**
     * Connection types. @hide
     */
    @DataClass.Generated.Member
    public @ConnectionType int getConnectionType() {
        return mConnectionType;
    }

    /**
     * Network connection speed in kbps. 0 if no network connection is present. @hide
     */
    @DataClass.Generated.Member
    public @IntRange(from = 0) long getNetworkConnectionSpeedKbps() {
        return mNetworkConnectionSpeedKbps;
    }

    /**
     * Whether the network is metered. False - not metered. True - metered. @hide
     */
    @DataClass.Generated.Member
    public boolean isNetworkMetered() {
        return mNetworkMetered;
    }

    /**
     * Information about installed and uninstalled apps.
     */
    @DataClass.Generated.Member
    public @NonNull Map<String,AppInfo> getAppInfo() {
        return mAppInfo;
    }

    /**
     * The app usage history in the last 30 days, sorted by total time spent. @hide
     */
    @DataClass.Generated.Member
    public @NonNull List<AppUsageStatus> getAppUsageHistory() {
        return mAppUsageHistory;
    }

    /**
     * The most recently known location. @hide
     */
    @DataClass.Generated.Member
    public @NonNull Location getCurrentLocation() {
        return mCurrentLocation;
    }

    /**
     * The location history in last 30 days, sorted by the stay duration. @hide
     */
    @DataClass.Generated.Member
    public @NonNull List<LocationStatus> getLocationHistory() {
        return mLocationHistory;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(UserData other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        UserData that = (UserData) o;
        //noinspection PointlessBooleanExpression
        return true
                && mTimezoneUtcOffsetMins == that.mTimezoneUtcOffsetMins
                && mOrientation == that.mOrientation
                && mAvailableStorageBytes == that.mAvailableStorageBytes
                && mBatteryPercentage == that.mBatteryPercentage
                && java.util.Objects.equals(mCarrier, that.mCarrier)
                && mConnectionType == that.mConnectionType
                && mNetworkConnectionSpeedKbps == that.mNetworkConnectionSpeedKbps
                && mNetworkMetered == that.mNetworkMetered
                && java.util.Objects.equals(mAppInfo, that.mAppInfo)
                && java.util.Objects.equals(mAppUsageHistory, that.mAppUsageHistory)
                && java.util.Objects.equals(mCurrentLocation, that.mCurrentLocation)
                && java.util.Objects.equals(mLocationHistory, that.mLocationHistory);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mTimezoneUtcOffsetMins;
        _hash = 31 * _hash + mOrientation;
        _hash = 31 * _hash + Long.hashCode(mAvailableStorageBytes);
        _hash = 31 * _hash + mBatteryPercentage;
        _hash = 31 * _hash + java.util.Objects.hashCode(mCarrier);
        _hash = 31 * _hash + mConnectionType;
        _hash = 31 * _hash + Long.hashCode(mNetworkConnectionSpeedKbps);
        _hash = 31 * _hash + Boolean.hashCode(mNetworkMetered);
        _hash = 31 * _hash + java.util.Objects.hashCode(mAppInfo);
        _hash = 31 * _hash + java.util.Objects.hashCode(mAppUsageHistory);
        _hash = 31 * _hash + java.util.Objects.hashCode(mCurrentLocation);
        _hash = 31 * _hash + java.util.Objects.hashCode(mLocationHistory);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        int flg = 0;
        if (mNetworkMetered) flg |= 0x80;
        dest.writeInt(flg);
        dest.writeInt(mTimezoneUtcOffsetMins);
        dest.writeInt(mOrientation);
        dest.writeLong(mAvailableStorageBytes);
        dest.writeInt(mBatteryPercentage);
        dest.writeString(mCarrier);
        dest.writeInt(mConnectionType);
        dest.writeLong(mNetworkConnectionSpeedKbps);
        dest.writeMap(mAppInfo);
        dest.writeParcelableList(mAppUsageHistory, flags);
        dest.writeTypedObject(mCurrentLocation, flags);
        dest.writeParcelableList(mLocationHistory, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserData(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int flg = in.readInt();
        boolean networkMetered = (flg & 0x80) != 0;
        int timezoneUtcOffsetMins = in.readInt();
        int orientation = in.readInt();
        long availableStorageBytes = in.readLong();
        int batteryPercentage = in.readInt();
        String carrier = in.readString();
        int connectionType = in.readInt();
        long networkConnectionSpeedKbps = in.readLong();
        Map<String,AppInfo> appInfo = new java.util.LinkedHashMap<>();
        in.readMap(appInfo, AppInfo.class.getClassLoader());
        List<AppUsageStatus> appUsageHistory = new java.util.ArrayList<>();
        in.readParcelableList(appUsageHistory, AppUsageStatus.class.getClassLoader());
        Location currentLocation = (Location) in.readTypedObject(Location.CREATOR);
        List<LocationStatus> locationHistory = new java.util.ArrayList<>();
        in.readParcelableList(locationHistory, LocationStatus.class.getClassLoader());

        this.mTimezoneUtcOffsetMins = timezoneUtcOffsetMins;
        this.mOrientation = orientation;
        this.mAvailableStorageBytes = availableStorageBytes;
        AnnotationValidations.validate(
                IntRange.class, null, mAvailableStorageBytes,
                "from", 0);
        this.mBatteryPercentage = batteryPercentage;
        AnnotationValidations.validate(
                IntRange.class, null, mBatteryPercentage,
                "from", 0,
                "to", 100);
        this.mCarrier = carrier;
        AnnotationValidations.validate(
                NonNull.class, null, mCarrier);
        this.mConnectionType = connectionType;

        if (!(mConnectionType == CONNECTION_TYPE_UNKNOWN)
                && !(mConnectionType == CONNECTION_TYPE_ETHERNET)
                && !(mConnectionType == CONNECTION_TYPE_WIFI)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_2G)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_3G)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_4G)
                && !(mConnectionType == CONNECTION_TYPE_CELLULAR_5G)) {
            throw new java.lang.IllegalArgumentException(
                    "connectionType was " + mConnectionType + " but must be one of: "
                            + "CONNECTION_TYPE_UNKNOWN(" + CONNECTION_TYPE_UNKNOWN + "), "
                            + "CONNECTION_TYPE_ETHERNET(" + CONNECTION_TYPE_ETHERNET + "), "
                            + "CONNECTION_TYPE_WIFI(" + CONNECTION_TYPE_WIFI + "), "
                            + "CONNECTION_TYPE_CELLULAR_2G(" + CONNECTION_TYPE_CELLULAR_2G + "), "
                            + "CONNECTION_TYPE_CELLULAR_3G(" + CONNECTION_TYPE_CELLULAR_3G + "), "
                            + "CONNECTION_TYPE_CELLULAR_4G(" + CONNECTION_TYPE_CELLULAR_4G + "), "
                            + "CONNECTION_TYPE_CELLULAR_5G(" + CONNECTION_TYPE_CELLULAR_5G + ")");
        }

        this.mNetworkConnectionSpeedKbps = networkConnectionSpeedKbps;
        AnnotationValidations.validate(
                IntRange.class, null, mNetworkConnectionSpeedKbps,
                "from", 0);
        this.mNetworkMetered = networkMetered;
        this.mAppInfo = appInfo;
        AnnotationValidations.validate(
                NonNull.class, null, mAppInfo);
        this.mAppUsageHistory = appUsageHistory;
        AnnotationValidations.validate(
                NonNull.class, null, mAppUsageHistory);
        this.mCurrentLocation = currentLocation;
        AnnotationValidations.validate(
                NonNull.class, null, mCurrentLocation);
        this.mLocationHistory = locationHistory;
        AnnotationValidations.validate(
                NonNull.class, null, mLocationHistory);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<UserData> CREATOR
            = new Parcelable.Creator<UserData>() {
        @Override
        public UserData[] newArray(int size) {
            return new UserData[size];
        }

        @Override
        public UserData createFromParcel(@NonNull android.os.Parcel in) {
            return new UserData(in);
        }
    };

    /**
     * A builder for {@link UserData}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private int mTimezoneUtcOffsetMins;
        private int mOrientation;
        private @IntRange(from = 0) long mAvailableStorageBytes;
        private @IntRange(from = 0, to = 100) int mBatteryPercentage;
        private @NonNull String mCarrier;
        private @ConnectionType int mConnectionType;
        private @IntRange(from = 0) long mNetworkConnectionSpeedKbps;
        private boolean mNetworkMetered;
        private @NonNull Map<String,AppInfo> mAppInfo;
        private @NonNull List<AppUsageStatus> mAppUsageHistory;
        private @NonNull Location mCurrentLocation;
        private @NonNull List<LocationStatus> mLocationHistory;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The device timezone +/- minutes offset from UTC.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTimezoneUtcOffsetMins(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mTimezoneUtcOffsetMins = value;
            return this;
        }

        /**
         * The device orientation. The value can be one of the constants ORIENTATION_UNDEFINED,
         * ORIENTATION_PORTRAIT or ORIENTATION_LANDSCAPE defined in
         * {@link android.content.res.Configuration}.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setOrientation(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mOrientation = value;
            return this;
        }

        /**
         * The available space on device in bytes.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setAvailableStorageBytes(@IntRange(from = 0) long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mAvailableStorageBytes = value;
            return this;
        }

        /**
         * Battery percentage.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setBatteryPercentage(@IntRange(from = 0, to = 100) int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mBatteryPercentage = value;
            return this;
        }

        /**
         * The name of the carrier.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setCarrier(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mCarrier = value;
            return this;
        }

        /**
         * Connection types. @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setConnectionType(@ConnectionType int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x20;
            mConnectionType = value;
            return this;
        }

        /**
         * Network connection speed in kbps. 0 if no network connection is present. @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setNetworkConnectionSpeedKbps(@IntRange(from = 0) long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x40;
            mNetworkConnectionSpeedKbps = value;
            return this;
        }

        /**
         * Whether the network is metered. False - not metered. True - metered. @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setNetworkMetered(boolean value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x80;
            mNetworkMetered = value;
            return this;
        }

        /**
         * Information about installed and uninstalled apps.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setAppInfo(@NonNull Map<String,AppInfo> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x100;
            mAppInfo = value;
            return this;
        }

        /**
         * The app usage history in the last 30 days, sorted by total time spent. @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setAppUsageHistory(@NonNull List<AppUsageStatus> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x200;
            mAppUsageHistory = value;
            return this;
        }

        /**
         * The most recently known location. @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setCurrentLocation(@NonNull Location value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x400;
            mCurrentLocation = value;
            return this;
        }

        /**
         * The location history in last 30 days, sorted by the stay duration. @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setLocationHistory(@NonNull List<LocationStatus> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x800;
            mLocationHistory = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull UserData build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1000; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mTimezoneUtcOffsetMins = 0;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mOrientation = 0;
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mAvailableStorageBytes = 0;
            }
            if ((mBuilderFieldsSet & 0x8) == 0) {
                mBatteryPercentage = 0;
            }
            if ((mBuilderFieldsSet & 0x10) == 0) {
                mCarrier = "";
            }
            if ((mBuilderFieldsSet & 0x20) == 0) {
                mConnectionType = 0;
            }
            if ((mBuilderFieldsSet & 0x40) == 0) {
                mNetworkConnectionSpeedKbps = 0;
            }
            if ((mBuilderFieldsSet & 0x80) == 0) {
                mNetworkMetered = false;
            }
            if ((mBuilderFieldsSet & 0x100) == 0) {
                mAppInfo = Collections.emptyMap();
            }
            if ((mBuilderFieldsSet & 0x200) == 0) {
                mAppUsageHistory = Collections.emptyList();
            }
            if ((mBuilderFieldsSet & 0x400) == 0) {
                mCurrentLocation = Location.EMPTY;
            }
            if ((mBuilderFieldsSet & 0x800) == 0) {
                mLocationHistory = Collections.emptyList();
            }
            UserData o = new UserData(
                    mTimezoneUtcOffsetMins,
                    mOrientation,
                    mAvailableStorageBytes,
                    mBatteryPercentage,
                    mCarrier,
                    mConnectionType,
                    mNetworkConnectionSpeedKbps,
                    mNetworkMetered,
                    mAppInfo,
                    mAppUsageHistory,
                    mCurrentLocation,
                    mLocationHistory);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x1000) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1694023783291L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/UserData.java",
            inputSignatures = "  int mTimezoneUtcOffsetMins\n  int mOrientation\n @android.annotation.IntRange long mAvailableStorageBytes\n @android.annotation.IntRange int mBatteryPercentage\n @android.annotation.NonNull java.lang.String mCarrier\npublic static final  int CONNECTION_TYPE_UNKNOWN\npublic static final  int CONNECTION_TYPE_ETHERNET\npublic static final  int CONNECTION_TYPE_WIFI\npublic static final  int CONNECTION_TYPE_CELLULAR_2G\npublic static final  int CONNECTION_TYPE_CELLULAR_3G\npublic static final  int CONNECTION_TYPE_CELLULAR_4G\npublic static final  int CONNECTION_TYPE_CELLULAR_5G\n @android.adservices.ondevicepersonalization.UserData.ConnectionType int mConnectionType\n @android.annotation.IntRange long mNetworkConnectionSpeedKbps\n  boolean mNetworkMetered\n @android.annotation.NonNull java.util.Map<java.lang.String,android.adservices.ondevicepersonalization.AppInfo> mAppInfo\n @android.annotation.NonNull java.util.List<android.adservices.ondevicepersonalization.AppUsageStatus> mAppUsageHistory\n @android.annotation.NonNull android.adservices.ondevicepersonalization.Location mCurrentLocation\n @android.annotation.NonNull java.util.List<android.adservices.ondevicepersonalization.LocationStatus> mLocationHistory\nclass UserData extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
