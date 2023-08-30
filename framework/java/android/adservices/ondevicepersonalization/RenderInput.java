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

import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * The input data for {@link IsolatedWorker#onRender()}.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class RenderInput implements Parcelable {
    /** The width of the slot. */
    private int mWidth = 0;

    /** The height of the slot. */
    private int mHeight = 0;

    /**
     * The index of the {@link RenderingConfig} in {@link ExecuteOutput} that this render
     * request is for.
     */
    private int mRenderingConfigIndex = 0;

    /** A {@link RenderingConfig} returned by {@link onExecute}. */
    @Nullable RenderingConfig mRenderingConfig = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderInput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ RenderInput(
            int width,
            int height,
            int renderingConfigIndex,
            @Nullable RenderingConfig renderingConfig) {
        this.mWidth = width;
        this.mHeight = height;
        this.mRenderingConfigIndex = renderingConfigIndex;
        this.mRenderingConfig = renderingConfig;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The width of the slot.
     */
    @DataClass.Generated.Member
    public int getWidth() {
        return mWidth;
    }

    /**
     * The height of the slot.
     */
    @DataClass.Generated.Member
    public int getHeight() {
        return mHeight;
    }

    /**
     * The index of the {@link RenderingConfig} in {@link ExecuteOutput} that this render
     * request is for.
     */
    @DataClass.Generated.Member
    public int getRenderingConfigIndex() {
        return mRenderingConfigIndex;
    }

    /**
     * A {@link RenderingConfig} returned by {@link onExecute}.
     */
    @DataClass.Generated.Member
    public @Nullable RenderingConfig getRenderingConfig() {
        return mRenderingConfig;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(RenderInput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        RenderInput that = (RenderInput) o;
        //noinspection PointlessBooleanExpression
        return true
                && mWidth == that.mWidth
                && mHeight == that.mHeight
                && mRenderingConfigIndex == that.mRenderingConfigIndex
                && java.util.Objects.equals(mRenderingConfig, that.mRenderingConfig);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mWidth;
        _hash = 31 * _hash + mHeight;
        _hash = 31 * _hash + mRenderingConfigIndex;
        _hash = 31 * _hash + java.util.Objects.hashCode(mRenderingConfig);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mRenderingConfig != null) flg |= 0x8;
        dest.writeByte(flg);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mRenderingConfigIndex);
        if (mRenderingConfig != null) dest.writeTypedObject(mRenderingConfig, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ RenderInput(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        int width = in.readInt();
        int height = in.readInt();
        int renderingConfigIndex = in.readInt();
        RenderingConfig renderingConfig = (flg & 0x8) == 0 ? null : (RenderingConfig) in.readTypedObject(RenderingConfig.CREATOR);

        this.mWidth = width;
        this.mHeight = height;
        this.mRenderingConfigIndex = renderingConfigIndex;
        this.mRenderingConfig = renderingConfig;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<RenderInput> CREATOR
            = new Parcelable.Creator<RenderInput>() {
        @Override
        public RenderInput[] newArray(int size) {
            return new RenderInput[size];
        }

        @Override
        public RenderInput createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new RenderInput(in);
        }
    };

    /**
     * A builder for {@link RenderInput}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private int mWidth;
        private int mHeight;
        private int mRenderingConfigIndex;
        private @Nullable RenderingConfig mRenderingConfig;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The width of the slot.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setWidth(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mWidth = value;
            return this;
        }

        /**
         * The height of the slot.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setHeight(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mHeight = value;
            return this;
        }

        /**
         * The index of the {@link RenderingConfig} in {@link ExecuteOutput} that this render
         * request is for.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setRenderingConfigIndex(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mRenderingConfigIndex = value;
            return this;
        }

        /**
         * A {@link RenderingConfig} returned by {@link onExecute}.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setRenderingConfig(@android.annotation.NonNull RenderingConfig value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mRenderingConfig = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @android.annotation.NonNull RenderInput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mWidth = 0;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mHeight = 0;
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mRenderingConfigIndex = 0;
            }
            if ((mBuilderFieldsSet & 0x8) == 0) {
                mRenderingConfig = null;
            }
            RenderInput o = new RenderInput(
                    mWidth,
                    mHeight,
                    mRenderingConfigIndex,
                    mRenderingConfig);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x10) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1692118409407L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderInput.java",
            inputSignatures = "private  int mWidth\nprivate  int mHeight\nprivate  int mRenderingConfigIndex\n @android.annotation.Nullable android.adservices.ondevicepersonalization.RenderingConfig mRenderingConfig\nclass RenderInput extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
