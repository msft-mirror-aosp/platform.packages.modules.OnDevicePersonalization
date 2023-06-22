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

package android.ondevicepersonalization;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.Map;

/**
 * The output to be rendered in a slot within a calling app.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class DownloadInput {
    /** Map containing downloaded keys and values */
    @NonNull Map<String, byte[]> mData;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/DownloadInput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ DownloadInput(
            @NonNull Map<String,byte[]> data) {
        this.mData = data;
        AnnotationValidations.validate(
                NonNull.class, null, mData);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Map containing downloaded keys and values
     */
    @DataClass.Generated.Member
    public @NonNull Map<String,byte[]> getData() {
        return mData;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(DownloadInput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        DownloadInput that = (DownloadInput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mData, that.mData);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mData);
        return _hash;
    }

    /**
     * A builder for {@link DownloadInput}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull Map<String,byte[]> mData;

        private long mBuilderFieldsSet = 0L;

        /** Creates a new Builder. */
        public Builder() {}

        /**
         * Creates a new Builder.
         *
         * @param data
         *   Map containing downloaded keys and values
         */
        public Builder(
                @NonNull Map<String,byte[]> data) {
            mData = data;
            AnnotationValidations.validate(
                    NonNull.class, null, mData);
        }

        /**
         * Map containing downloaded keys and values
         */
        @DataClass.Generated.Member
        public @NonNull Builder setData(@NonNull Map<String,byte[]> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mData = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull DownloadInput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            DownloadInput o = new DownloadInput(
                    mData);
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
            time = 1680551317042L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/DownloadInput.java",
            inputSignatures = " @android.annotation.NonNull java.util.Map<java.lang.String,byte[]> mData\nclass DownloadInput extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
