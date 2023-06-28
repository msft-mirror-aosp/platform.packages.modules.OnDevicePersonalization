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

package android.app.ondevicepersonalization;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.Collections;
import java.util.List;

/**
 * The result returned by {@link IsolatedComputationCallback#onExecute()} in response to a call to
 * {@link OnDevicePersonalizationManager#execute()} from a client app.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class ExecuteOutput implements Parcelable {
    /**
     * Persistent data to be written to the REQUESTS table after
     * {@link IsolatedComputationCallback#onExecute()} completes. If null, no persistent data will
     * be written.
     */
    @Nullable private RequestLogRecord mRequestLogRecord = null;

    /**
     * A list of {@link RenderingConfig} objects, one per slot specified in the request from the
     * calling app. The calling app and the service must agree on the expected size of this list.
     */
    @DataClass.PluralOf("renderingConfig")
    @NonNull private List<RenderingConfig> mRenderingConfigs = Collections.emptyList();



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/app/ondevicepersonalization/ExecuteOutput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ ExecuteOutput(
            @Nullable RequestLogRecord requestLogRecord,
            @NonNull List<RenderingConfig> renderingConfigs) {
        this.mRequestLogRecord = requestLogRecord;
        this.mRenderingConfigs = renderingConfigs;
        AnnotationValidations.validate(
                NonNull.class, null, mRenderingConfigs);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Persistent data to be written to the REQUESTS table after
     * {@link IsolatedComputationCallback#onExecute()} completes. If null, no persistent data will
     * be written.
     */
    @DataClass.Generated.Member
    public @Nullable RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    /**
     * A list of {@link RenderingConfig} objects, one per slot specified in the request from the
     * calling app. The calling app and the service must agree on the expected size of this list.
     */
    @DataClass.Generated.Member
    public @NonNull List<RenderingConfig> getRenderingConfigs() {
        return mRenderingConfigs;
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
                && java.util.Objects.equals(mRenderingConfigs, that.mRenderingConfigs);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mRequestLogRecord);
        _hash = 31 * _hash + java.util.Objects.hashCode(mRenderingConfigs);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mRequestLogRecord != null) flg |= 0x1;
        dest.writeByte(flg);
        if (mRequestLogRecord != null) dest.writeTypedObject(mRequestLogRecord, flags);
        dest.writeParcelableList(mRenderingConfigs, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ ExecuteOutput(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        RequestLogRecord requestLogRecord = (flg & 0x1) == 0 ? null : (RequestLogRecord) in.readTypedObject(RequestLogRecord.CREATOR);
        List<RenderingConfig> renderingConfigs = new java.util.ArrayList<>();
        in.readParcelableList(renderingConfigs, RenderingConfig.class.getClassLoader());

        this.mRequestLogRecord = requestLogRecord;
        this.mRenderingConfigs = renderingConfigs;
        AnnotationValidations.validate(
                NonNull.class, null, mRenderingConfigs);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<ExecuteOutput> CREATOR
            = new Parcelable.Creator<ExecuteOutput>() {
        @Override
        public ExecuteOutput[] newArray(int size) {
            return new ExecuteOutput[size];
        }

        @Override
        public ExecuteOutput createFromParcel(@NonNull android.os.Parcel in) {
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
        private @NonNull List<RenderingConfig> mRenderingConfigs;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Persistent data to be written to the REQUESTS table after
         * {@link IsolatedComputationCallback#onExecute()} completes. If null, no persistent data will
         * be written.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRequestLogRecord(@NonNull RequestLogRecord value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mRequestLogRecord = value;
            return this;
        }

        /**
         * A list of {@link RenderingConfig} objects, one per slot specified in the request from the
         * calling app. The calling app and the service must agree on the expected size of this list.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRenderingConfigs(@NonNull List<RenderingConfig> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mRenderingConfigs = value;
            return this;
        }

        /** @see #setRenderingConfigs */
        @DataClass.Generated.Member
        public @NonNull Builder addRenderingConfig(@NonNull RenderingConfig value) {
            if (mRenderingConfigs == null) setRenderingConfigs(new java.util.ArrayList<>());
            mRenderingConfigs.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull ExecuteOutput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mRequestLogRecord = null;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mRenderingConfigs = Collections.emptyList();
            }
            ExecuteOutput o = new ExecuteOutput(
                    mRequestLogRecord,
                    mRenderingConfigs);
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
            time = 1687978725871L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/app/ondevicepersonalization/ExecuteOutput.java",
            inputSignatures = "private @android.annotation.Nullable android.app.ondevicepersonalization.RequestLogRecord mRequestLogRecord\nprivate @com.android.ondevicepersonalization.internal.util.DataClass.PluralOf(\"renderingConfig\") @android.annotation.NonNull java.util.List<android.app.ondevicepersonalization.RenderingConfig> mRenderingConfigs\nclass ExecuteOutput extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
