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
 * Parcelable version of {@link RenderOutput}.
 * @hide
 */
@DataClass(genAidl = false, genBuilder = false)
public final class RenderOutputParcel implements Parcelable {
    /**
     * The HTML content to be rendered in a webview. If this is null, the ODP service
     * generates HTML from the data in {@link #getTemplateId()} and {@link #getTemplateParams()}
     * as described below.
     */
    @Nullable private String mContent = null;

    /**
     * A key in the REMOTE_DATA {@link IsolatedService#getRemoteData(RequestToken)} table that
     * points to an <a href="velocity.apache.org">Apache Velocity</a> template. This is ignored if
     * {@link #getContent()} is not null.
     */
    @Nullable private String mTemplateId = null;

    /**
     * The parameters to be populated in the template from {@link #getTemplateId()}. This is
     * ignored if {@link #getContent()} is not null.
     */
    @NonNull private PersistableBundle mTemplateParams = PersistableBundle.EMPTY;

    /** @hide */
    public RenderOutputParcel(@NonNull RenderOutput value) {
        this(value.getContent(), value.getTemplateId(), value.getTemplateParams());
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderOutputParcel.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new RenderOutputParcel.
     *
     * @param content
     *   The HTML content to be rendered in a webview. If this is null, the ODP service
     *   generates HTML from the data in {@link #getTemplateId()} and {@link #getTemplateParams()}
     *   as described below.
     * @param templateId
     *   A key in the REMOTE_DATA {@link IsolatedService#getRemoteData(RequestToken)} table that
     *   points to an <a href="velocity.apache.org">Apache Velocity</a> template. This is ignored if
     *   {@link #getContent()} is not null.
     * @param templateParams
     *   The parameters to be populated in the template from {@link #getTemplateId()}. This is
     *   ignored if {@link #getContent()} is not null.
     */
    @DataClass.Generated.Member
    public RenderOutputParcel(
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
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mContent != null) flg |= 0x1;
        if (mTemplateId != null) flg |= 0x2;
        dest.writeByte(flg);
        if (mContent != null) dest.writeString(mContent);
        if (mTemplateId != null) dest.writeString(mTemplateId);
        dest.writeTypedObject(mTemplateParams, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ RenderOutputParcel(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        String content = (flg & 0x1) == 0 ? null : in.readString();
        String templateId = (flg & 0x2) == 0 ? null : in.readString();
        PersistableBundle templateParams = (PersistableBundle) in.readTypedObject(PersistableBundle.CREATOR);

        this.mContent = content;
        this.mTemplateId = templateId;
        this.mTemplateParams = templateParams;
        AnnotationValidations.validate(
                NonNull.class, null, mTemplateParams);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<RenderOutputParcel> CREATOR
            = new Parcelable.Creator<RenderOutputParcel>() {
        @Override
        public RenderOutputParcel[] newArray(int size) {
            return new RenderOutputParcel[size];
        }

        @Override
        public RenderOutputParcel createFromParcel(@NonNull android.os.Parcel in) {
            return new RenderOutputParcel(in);
        }
    };

    @DataClass.Generated(
            time = 1698864341247L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderOutputParcel.java",
            inputSignatures = "private @android.annotation.Nullable java.lang.String mContent\nprivate @android.annotation.Nullable java.lang.String mTemplateId\nprivate @android.annotation.NonNull android.os.PersistableBundle mTemplateParams\nclass RenderOutputParcel extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genAidl=false, genBuilder=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
