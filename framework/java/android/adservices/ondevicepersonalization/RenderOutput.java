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
import android.annotation.Nullable;
import android.os.PersistableBundle;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * The result returned by
 * {@link IsolatedWorker#onRender(RenderInput, java.util.function.Consumer)}.
 *
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class RenderOutput {
    /**
     * The HTML content to be rendered in a webview. If this is null, the ODP service
     * generates HTML from the data in {@link #getTemplateId()} and {@link #getTemplateParams()}
     * as described below.
     */
    @DataClass.MaySetToNull
    @Nullable private String mContent = null;

    /**
     * A key in the REMOTE_DATA {@link IsolatedService#getRemoteData(RequestToken)} table that
     * points to an <a href="velocity.apache.org">Apache Velocity</a> template. This is ignored if
     * {@link #getContent()} is not null.
     */
    @DataClass.MaySetToNull
    @Nullable private String mTemplateId = null;

    /**
     * The parameters to be populated in the template from {@link #getTemplateId()}. This is
     * ignored if {@link #getContent()} is not null.
     */
    @NonNull private PersistableBundle mTemplateParams = PersistableBundle.EMPTY;





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
            @Nullable String templateId,
            @NonNull PersistableBundle templateParams) {
        this.mContent = content;
        this.mTemplateId = templateId;
        this.mTemplateParams = templateParams;
        AnnotationValidations.validate(
                NonNull.class, null, mTemplateParams);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The HTML content to be rendered in a webview. If this is null, the ODP service
     * generates HTML from the data in {@link #getTemplateId()} and {@link #getTemplateParams()}
     * as described below.
     */
    @DataClass.Generated.Member
    public @Nullable String getContent() {
        return mContent;
    }

    /**
     * A key in the REMOTE_DATA {@link IsolatedService#getRemoteData(RequestToken)} table that
     * points to an <a href="velocity.apache.org">Apache Velocity</a> template. This is ignored if
     * {@link #getContent()} is not null.
     */
    @DataClass.Generated.Member
    public @Nullable String getTemplateId() {
        return mTemplateId;
    }

    /**
     * The parameters to be populated in the template from {@link #getTemplateId()}. This is
     * ignored if {@link #getContent()} is not null.
     */
    @DataClass.Generated.Member
    public @NonNull PersistableBundle getTemplateParams() {
        return mTemplateParams;
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
                && java.util.Objects.equals(mTemplateId, that.mTemplateId)
                && java.util.Objects.equals(mTemplateParams, that.mTemplateParams);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mContent);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTemplateId);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTemplateParams);
        return _hash;
    }

    /**
     * A builder for {@link RenderOutput}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable String mContent;
        private @Nullable String mTemplateId;
        private @NonNull PersistableBundle mTemplateParams;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The HTML content to be rendered in a webview. If this is null, the ODP service
         * generates HTML from the data in {@link #getTemplateId()} and {@link #getTemplateParams()}
         * as described below.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setContent(@Nullable String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mContent = value;
            return this;
        }

        /**
         * A key in the REMOTE_DATA {@link IsolatedService#getRemoteData(RequestToken)} table that
         * points to an <a href="velocity.apache.org">Apache Velocity</a> template. This is ignored if
         * {@link #getContent()} is not null.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTemplateId(@Nullable String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mTemplateId = value;
            return this;
        }

        /**
         * The parameters to be populated in the template from {@link #getTemplateId()}. This is
         * ignored if {@link #getContent()} is not null.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTemplateParams(@NonNull PersistableBundle value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mTemplateParams = value;
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
                mTemplateId = null;
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mTemplateParams = PersistableBundle.EMPTY;
            }
            RenderOutput o = new RenderOutput(
                    mContent,
                    mTemplateId,
                    mTemplateParams);
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
            time = 1707253768205L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderOutput.java",
            inputSignatures = "private @com.android.ondevicepersonalization.internal.util.DataClass.MaySetToNull @android.annotation.Nullable java.lang.String mContent\nprivate @com.android.ondevicepersonalization.internal.util.DataClass.MaySetToNull @android.annotation.Nullable java.lang.String mTemplateId\nprivate @android.annotation.NonNull android.os.PersistableBundle mTemplateParams\nclass RenderOutput extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
