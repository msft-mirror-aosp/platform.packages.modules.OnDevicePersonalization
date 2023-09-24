/*
 * Copyright (C) 2023 The Android Open Source Project
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
 * The input data for {@link IsolatedWorker#onWebViewEvent()}.
 *
 */
@DataClass(genHiddenBuilder = true, genEqualsHashCode = true)
public final class WebViewEventInput implements Parcelable {
    /**
     * The {@link RequestLogRecord} that was returned as a result of
     * {@link IsolatedWorker#onExecute()}.
     */
    @Nullable private RequestLogRecord mRequestLogRecord = null;

    /**
     * The Event URL parameters that the service passed to
     * {@link EventUrlProvider#createEventTrackingUrlWithResponse()}.
     */
    @NonNull private PersistableBundle mParameters = PersistableBundle.EMPTY;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/WebViewEventInput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ WebViewEventInput(
            @Nullable RequestLogRecord requestLogRecord,
            @NonNull PersistableBundle parameters) {
        this.mRequestLogRecord = requestLogRecord;
        this.mParameters = parameters;
        AnnotationValidations.validate(
                NonNull.class, null, mParameters);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The {@link RequestLogRecord} that was returned as a result of
     * {@link IsolatedWorker#onExecute()}.
     */
    @DataClass.Generated.Member
    public @Nullable RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    /**
     * The Event URL parameters that the service passed to
     * {@link EventUrlProvider#createEventTrackingUrlWithResponse()}.
     */
    @DataClass.Generated.Member
    public @NonNull PersistableBundle getParameters() {
        return mParameters;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(WebViewEventInput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        WebViewEventInput that = (WebViewEventInput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mRequestLogRecord, that.mRequestLogRecord)
                && java.util.Objects.equals(mParameters, that.mParameters);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mRequestLogRecord);
        _hash = 31 * _hash + java.util.Objects.hashCode(mParameters);
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
        dest.writeTypedObject(mParameters, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ WebViewEventInput(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        RequestLogRecord requestLogRecord = (flg & 0x1) == 0 ? null : (RequestLogRecord) in.readTypedObject(RequestLogRecord.CREATOR);
        PersistableBundle parameters = (PersistableBundle) in.readTypedObject(PersistableBundle.CREATOR);

        this.mRequestLogRecord = requestLogRecord;
        this.mParameters = parameters;
        AnnotationValidations.validate(
                NonNull.class, null, mParameters);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<WebViewEventInput> CREATOR
            = new Parcelable.Creator<WebViewEventInput>() {
        @Override
        public WebViewEventInput[] newArray(int size) {
            return new WebViewEventInput[size];
        }

        @Override
        public WebViewEventInput createFromParcel(@NonNull android.os.Parcel in) {
            return new WebViewEventInput(in);
        }
    };

    /**
     * A builder for {@link WebViewEventInput}
     * @hide
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable RequestLogRecord mRequestLogRecord;
        private @NonNull PersistableBundle mParameters;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The {@link RequestLogRecord} that was returned as a result of
         * {@link IsolatedWorker#onExecute()}.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRequestLogRecord(@NonNull RequestLogRecord value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mRequestLogRecord = value;
            return this;
        }

        /**
         * The Event URL parameters that the service passed to
         * {@link EventUrlProvider#createEventTrackingUrlWithResponse()}.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setParameters(@NonNull PersistableBundle value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mParameters = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull WebViewEventInput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mRequestLogRecord = null;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mParameters = PersistableBundle.EMPTY;
            }
            WebViewEventInput o = new WebViewEventInput(
                    mRequestLogRecord,
                    mParameters);
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
            time = 1695492714319L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/WebViewEventInput.java",
            inputSignatures = "private @android.annotation.Nullable android.adservices.ondevicepersonalization.RequestLogRecord mRequestLogRecord\nprivate @android.annotation.NonNull android.os.PersistableBundle mParameters\nclass WebViewEventInput extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genHiddenBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
