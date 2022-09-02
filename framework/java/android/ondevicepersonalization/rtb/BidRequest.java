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

package android.ondevicepersonalization.rtb;

import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.List;

/**
 * A Bid Request sent from an {@link Exchange} to each {@link Bidder}.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class BidRequest implements Parcelable {
    /** A list of {@link Imp} objects. */
    @Nullable private List<Imp> mImps = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/rtb/BidRequest.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ BidRequest(
            @Nullable List<Imp> imps) {
        this.mImps = imps;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * A list of {@link Imp} objects.
     */
    @DataClass.Generated.Member
    public @Nullable List<Imp> getImps() {
        return mImps;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(BidRequest other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        BidRequest that = (BidRequest) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mImps, that.mImps);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mImps);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mImps != null) flg |= 0x1;
        dest.writeByte(flg);
        if (mImps != null) dest.writeParcelableList(mImps, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ BidRequest(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        List<Imp> imps = null;
        if ((flg & 0x1) != 0) {
            imps = new java.util.ArrayList<>();
            in.readParcelableList(imps, Imp.class.getClassLoader());
        }

        this.mImps = imps;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<BidRequest> CREATOR
            = new Parcelable.Creator<BidRequest>() {
        @Override
        public BidRequest[] newArray(int size) {
            return new BidRequest[size];
        }

        @Override
        public BidRequest createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new BidRequest(in);
        }
    };

    /**
     * A builder for {@link BidRequest}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable List<Imp> mImps;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * A list of {@link Imp} objects.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setImps(@android.annotation.NonNull List<Imp> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mImps = value;
            return this;
        }

        /** @see #setImps */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder addImps(@android.annotation.NonNull Imp value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...

            if (mImps == null) setImps(new java.util.ArrayList<>());
            mImps.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @android.annotation.NonNull BidRequest build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mImps = null;
            }
            BidRequest o = new BidRequest(
                    mImps);
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
            time = 1659555613573L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/rtb/BidRequest.java",
            inputSignatures = "private @android.annotation.Nullable java.util.List<android.ondevicepersonalization.rtb.Imp> mImps\nclass BidRequest extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
