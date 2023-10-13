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

import static android.adservices.ondevicepersonalization.Constants.KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.Collections;
import java.util.List;

/**
 * The result returned by {@link IsolatedWorker#onDownloadCompleted()}.
 *
 */
@FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class DownloadCompletedOutput implements Parcelable {
    /**
     * The keys to be retained in the REMOTE_DATA table. Any existing keys that are not
     * present in this list are removed from the table.
     */
    @DataClass.PluralOf("retainedKey")
    @NonNull private List<String> mRetainedKeys = Collections.emptyList();



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/DownloadCompletedOutput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ DownloadCompletedOutput(
            @NonNull List<String> retainedKeys) {
        this.mRetainedKeys = retainedKeys;
        AnnotationValidations.validate(
                NonNull.class, null, mRetainedKeys);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The keys to be retained in the REMOTE_DATA table. Any existing keys that are not
     * present in this list are removed from the table.
     */
    @DataClass.Generated.Member
    public @NonNull List<String> getRetainedKeys() {
        return mRetainedKeys;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(DownloadCompletedOutput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        DownloadCompletedOutput that = (DownloadCompletedOutput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mRetainedKeys, that.mRetainedKeys);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mRetainedKeys);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeStringList(mRetainedKeys);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ DownloadCompletedOutput(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        List<String> retainedKeys = new java.util.ArrayList<>();
        in.readStringList(retainedKeys);

        this.mRetainedKeys = retainedKeys;
        AnnotationValidations.validate(
                NonNull.class, null, mRetainedKeys);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<DownloadCompletedOutput> CREATOR
            = new Parcelable.Creator<DownloadCompletedOutput>() {
        @Override
        public DownloadCompletedOutput[] newArray(int size) {
            return new DownloadCompletedOutput[size];
        }

        @Override
        public DownloadCompletedOutput createFromParcel(@NonNull android.os.Parcel in) {
            return new DownloadCompletedOutput(in);
        }
    };

    /**
     * A builder for {@link DownloadCompletedOutput}
     */
    @FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull List<String> mRetainedKeys;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The keys to be retained in the REMOTE_DATA table. Any existing keys that are not
         * present in this list are removed from the table.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRetainedKeys(@NonNull List<String> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mRetainedKeys = value;
            return this;
        }

        /** @see #setRetainedKeys */
        @DataClass.Generated.Member
        public @NonNull Builder addRetainedKey(@NonNull String value) {
            if (mRetainedKeys == null) setRetainedKeys(new java.util.ArrayList<>());
            mRetainedKeys.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull DownloadCompletedOutput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mRetainedKeys = Collections.emptyList();
            }
            DownloadCompletedOutput o = new DownloadCompletedOutput(
                    mRetainedKeys);
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
            time = 1696972554365L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/DownloadCompletedOutput.java",
            inputSignatures = "private @com.android.ondevicepersonalization.internal.util.DataClass.PluralOf(\"retainedKey\") @android.annotation.NonNull java.util.List<java.lang.String> mRetainedKeys\nclass DownloadCompletedOutput extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
