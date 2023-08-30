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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcelable;
import android.os.PersistableBundle;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * The result returned by {@link IsolatedWorker#onExecute()} in response to a
 * {@link OnDevicePersonalizationManager#requestSurfacePackage()} request from a calling app.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class RenderOutput implements Parcelable {
    /** The content to be rendered. */
    @Nullable private String mContent = null;

    /**
     * Parameters for template rendering
     */
    @NonNull private PersistableBundle mTemplateParams = PersistableBundle.EMPTY;

    /**
     * Template ID to retrieve from REMOTE_DATA for rendering
     */
    @Nullable private String mTemplateId = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderOutput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ RenderOutput(
            @Nullable String content,
            @NonNull PersistableBundle templateParams,
            @Nullable String templateId) {
        this.mContent = content;
        this.mTemplateParams = templateParams;
        AnnotationValidations.validate(
                NonNull.class, null, mTemplateParams);
        this.mTemplateId = templateId;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The content to be rendered.
     */
    @DataClass.Generated.Member
    public @Nullable String getContent() {
        return mContent;
    }

    /**
     * Parameters for template rendering
     */
    @DataClass.Generated.Member
    public @NonNull PersistableBundle getTemplateParams() {
        return mTemplateParams;
    }

    /**
     * Template ID to retrieve from REMOTE_DATA for rendering
     */
    @DataClass.Generated.Member
    public @Nullable String getTemplateId() {
        return mTemplateId;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(RenderOutput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        RenderOutput that = (RenderOutput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mContent, that.mContent)
                && java.util.Objects.equals(mTemplateParams, that.mTemplateParams)
                && java.util.Objects.equals(mTemplateId, that.mTemplateId);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mContent);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTemplateParams);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTemplateId);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mContent != null) flg |= 0x1;
        if (mTemplateId != null) flg |= 0x4;
        dest.writeByte(flg);
        if (mContent != null) dest.writeString(mContent);
        dest.writeTypedObject(mTemplateParams, flags);
        if (mTemplateId != null) dest.writeString(mTemplateId);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ RenderOutput(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        String content = (flg & 0x1) == 0 ? null : in.readString();
        PersistableBundle templateParams = (PersistableBundle) in.readTypedObject(PersistableBundle.CREATOR);
        String templateId = (flg & 0x4) == 0 ? null : in.readString();

        this.mContent = content;
        this.mTemplateParams = templateParams;
        AnnotationValidations.validate(
                NonNull.class, null, mTemplateParams);
        this.mTemplateId = templateId;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<RenderOutput> CREATOR
            = new Parcelable.Creator<RenderOutput>() {
        @Override
        public RenderOutput[] newArray(int size) {
            return new RenderOutput[size];
        }

        @Override
        public RenderOutput createFromParcel(@NonNull android.os.Parcel in) {
            return new RenderOutput(in);
        }
    };

    /**
     * A builder for {@link RenderOutput}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable String mContent;
        private @NonNull PersistableBundle mTemplateParams;
        private @Nullable String mTemplateId;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The content to be rendered.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setContent(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mContent = value;
            return this;
        }

        /**
         * Parameters for template rendering
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTemplateParams(@NonNull PersistableBundle value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mTemplateParams = value;
            return this;
        }

        /**
         * Template ID to retrieve from REMOTE_DATA for rendering
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTemplateId(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mTemplateId = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull RenderOutput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mContent = null;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mTemplateParams = PersistableBundle.EMPTY;
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mTemplateId = null;
            }
            RenderOutput o = new RenderOutput(
                    mContent,
                    mTemplateParams,
                    mTemplateId);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x8) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1692118415895L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderOutput.java",
            inputSignatures = "private @android.annotation.Nullable java.lang.String mContent\nprivate @android.annotation.NonNull android.os.PersistableBundle mTemplateParams\nprivate @android.annotation.Nullable java.lang.String mTemplateId\nclass RenderOutput extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
