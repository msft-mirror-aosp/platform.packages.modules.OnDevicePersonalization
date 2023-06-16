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
 * The result returned by {@link IsolatedComputationCallback#onExecute()} in response to a request
 * from a calling app.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class ExecuteOutput implements Parcelable {
    /**
     * Data to be written to the log.
     */
    @Nullable private RequestLogRecord mRequestLogRecord = null;

    /**
     * A list of {@link RenderingData} objects, one per slot specified in the request from the
     * calling app.
     */
    @Nullable private List<RenderingData> mRenderingDataList = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/ExecuteOutput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ ExecuteOutput(
            @Nullable RequestLogRecord requestLogRecord,
            @Nullable List<RenderingData> renderingDataList) {
        this.mRequestLogRecord = requestLogRecord;
        this.mRenderingDataList = renderingDataList;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Data to be written to the log.
     */
    @DataClass.Generated.Member
    public @Nullable RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    /**
     * A list of {@link RenderingData} objects, one per slot specified in the request from the
     * calling app.
     */
    @DataClass.Generated.Member
    public @Nullable List<RenderingData> getRenderingDataList() {
        return mRenderingDataList;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(ExecuteOutput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        ExecuteOutput that = (ExecuteOutput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mRequestLogRecord, that.mRequestLogRecord)
                && java.util.Objects.equals(mRenderingDataList, that.mRenderingDataList);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mRequestLogRecord);
        _hash = 31 * _hash + java.util.Objects.hashCode(mRenderingDataList);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mRequestLogRecord != null) flg |= 0x1;
        if (mRenderingDataList != null) flg |= 0x2;
        dest.writeByte(flg);
        if (mRequestLogRecord != null) dest.writeTypedObject(mRequestLogRecord, flags);
        if (mRenderingDataList != null) dest.writeParcelableList(mRenderingDataList, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ ExecuteOutput(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        RequestLogRecord requestLogRecord = (flg & 0x1) == 0 ? null : (RequestLogRecord) in.readTypedObject(RequestLogRecord.CREATOR);
        List<RenderingData> renderingDataList = null;
        if ((flg & 0x2) != 0) {
            renderingDataList = new java.util.ArrayList<>();
            in.readParcelableList(renderingDataList, RenderingData.class.getClassLoader());
        }

        this.mRequestLogRecord = requestLogRecord;
        this.mRenderingDataList = renderingDataList;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<ExecuteOutput> CREATOR
            = new Parcelable.Creator<ExecuteOutput>() {
        @Override
        public ExecuteOutput[] newArray(int size) {
            return new ExecuteOutput[size];
        }

        @Override
        public ExecuteOutput createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new ExecuteOutput(in);
        }
    };

    /**
     * A builder for {@link ExecuteOutput}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable RequestLogRecord mRequestLogRecord;
        private @Nullable List<RenderingData> mRenderingDataList;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Data to be written to the log.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setRequestLogRecord(@android.annotation.NonNull RequestLogRecord value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mRequestLogRecord = value;
            return this;
        }

        /**
         * A list of {@link RenderingData} objects, one per slot specified in the request from the
         * calling app.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setRenderingDataList(@android.annotation.NonNull List<RenderingData> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mRenderingDataList = value;
            return this;
        }

        /** @see #setRenderingDataList */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder addRenderingDataList(@android.annotation.NonNull RenderingData value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...

            if (mRenderingDataList == null) setRenderingDataList(new java.util.ArrayList<>());
            mRenderingDataList.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @android.annotation.NonNull ExecuteOutput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mRequestLogRecord = null;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mRenderingDataList = null;
            }
            ExecuteOutput o = new ExecuteOutput(
                    mRequestLogRecord,
                    mRenderingDataList);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x4) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1686691864276L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/ExecuteOutput.java",
            inputSignatures = "private @android.annotation.Nullable android.ondevicepersonalization.RequestLogRecord mRequestLogRecord\nprivate @android.annotation.Nullable java.util.List<android.ondevicepersonalization.RenderingData> mRenderingDataList\nclass ExecuteOutput extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
