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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcelable;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * Information about apps.
 *
 */
@DataClass(genHiddenBuilder = true, genEqualsHashCode = true)
public final class AppInfo implements Parcelable {
    /** Whether the app is installed. */
    private boolean mInstalled = false;

    /**
     * Creates a new AppInfo.
     *
     * @param installed {@code true} if the app is installed.
     */
    @FlaggedApi(Flags.FLAG_DATA_CLASS_MISSING_CTORS_AND_GETTERS_ENABLED)
    public AppInfo(boolean installed) {
        this.mInstalled = installed;
    }

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/AppInfo.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off

    /**
     * Whether the app is installed.
     */
    @DataClass.Generated.Member
    public @NonNull boolean isInstalled() {
        return mInstalled;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(AppInfo other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        AppInfo that = (AppInfo) o;
        //noinspection PointlessBooleanExpression
        return true
                && mInstalled == that.mInstalled;
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Boolean.hashCode(mInstalled);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mInstalled) flg |= 0x1;
        dest.writeByte(flg);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ AppInfo(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        boolean installed = (flg & 0x1) != 0;

        this.mInstalled = installed;
        AnnotationValidations.validate(
                NonNull.class, null, mInstalled);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<AppInfo> CREATOR
            = new Parcelable.Creator<AppInfo>() {
        @Override
        public AppInfo[] newArray(int size) {
            return new AppInfo[size];
        }

        @Override
        public AppInfo createFromParcel(@NonNull android.os.Parcel in) {
            return new AppInfo(in);
        }
    };

    /**
     * A builder for {@link AppInfo}
     * @hide
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull boolean mInstalled;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Whether the app is installed.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setInstalled(@NonNull boolean value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mInstalled = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull AppInfo build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mInstalled = false;
            }
            AppInfo o = new AppInfo(
                    mInstalled);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x2) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1695492606666L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/AppInfo.java",
            inputSignatures = " @android.annotation.NonNull boolean mInstalled\nclass AppInfo extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genHiddenBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
