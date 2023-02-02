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

import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.List;

/**
 * The result returned by a {@link PersonalizationService} in response to a request from a
 * calling app.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class SelectContentResult implements Parcelable {
    /**
     * A list of {@link SlotResult} objects, one per slot specified in the request from the
     * calling app.
     */
    @Nullable private List<SlotResult> mSlotResults = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/SelectContentResult.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ SelectContentResult(
            @Nullable List<SlotResult> slotResults) {
        this.mSlotResults = slotResults;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * A list of {@link SlotResult} objects, one per slot specified in the request from the
     * calling app.
     */
    @DataClass.Generated.Member
    public @Nullable List<SlotResult> getSlotResults() {
        return mSlotResults;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(SelectContentResult other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        SelectContentResult that = (SelectContentResult) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mSlotResults, that.mSlotResults);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mSlotResults);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mSlotResults != null) flg |= 0x1;
        dest.writeByte(flg);
        if (mSlotResults != null) dest.writeParcelableList(mSlotResults, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ SelectContentResult(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        List<SlotResult> slotResults = null;
        if ((flg & 0x1) != 0) {
            slotResults = new java.util.ArrayList<>();
            in.readParcelableList(slotResults, SlotResult.class.getClassLoader());
        }

        this.mSlotResults = slotResults;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<SelectContentResult> CREATOR
            = new Parcelable.Creator<SelectContentResult>() {
        @Override
        public SelectContentResult[] newArray(int size) {
            return new SelectContentResult[size];
        }

        @Override
        public SelectContentResult createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new SelectContentResult(in);
        }
    };

    /**
     * A builder for {@link SelectContentResult}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable List<SlotResult> mSlotResults;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * A list of {@link SlotResult} objects, one per slot specified in the request from the
         * calling app.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setSlotResults(@android.annotation.NonNull List<SlotResult> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mSlotResults = value;
            return this;
        }

        /** @see #setSlotResults */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder addSlotResults(@android.annotation.NonNull SlotResult value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...

            if (mSlotResults == null) setSlotResults(new java.util.ArrayList<>());
            mSlotResults.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @android.annotation.NonNull SelectContentResult build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mSlotResults = null;
            }
            SelectContentResult o = new SelectContentResult(
                    mSlotResults);
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
            time = 1675280067432L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/SelectContentResult.java",
            inputSignatures = "private @android.annotation.Nullable java.util.List<android.ondevicepersonalization.SlotResult> mSlotResults\nclass SelectContentResult extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
