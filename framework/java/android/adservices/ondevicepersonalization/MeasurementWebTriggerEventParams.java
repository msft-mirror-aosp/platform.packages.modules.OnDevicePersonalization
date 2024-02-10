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
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.net.Uri;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

// TODO(b/301732670): Add link to documentation describing the format of the ODP-specific
// attribution data that the server is expected to return.
/**
 * A class that contains Web Trigger Event data sent from the
 * <a href="https://developer.android.com/design-for-safety/privacy-sandbox/guides/attribution">
 * Measurement API</a> to the OnDevicePersonalization service when the browser registers a web
 * trigger URL with the native OS attribution API as described in
 * <a href="https://github.com/WICG/attribution-reporting-api/blob/main/app_to_web.md">
 * Cross App and Web Attribution Measurement</a>. The Measurement API fetches and processes the
 * attribution response from the browser-provided URL. If the URL response contains additional
 * data that needs to be processed by an {@link IsolatedService}, the Measurement API passes this
 * to the OnDevicePersonalization service and the OnDevicePersonalization service will invoke
 * the {@link IsolatedService} with the provided data.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class MeasurementWebTriggerEventParams {
    /**
     * The URL of the web page where the web trigger event occurred.
     */
    @NonNull private Uri mDestinationUrl;

    /**
     * The package name of the browser app where the web trigger event occurred.
     */
    @NonNull private String mAppPackageName;

    /**
     * The package and class name of the {@link IsolatedService} that should process
     * the web trigger event.
     */
    @NonNull private ComponentName mIsolatedService;

    /**
     * An optional SHA-256 hash of the signing key of the package that contains
     * the {@link IsolatedService}, to guard against package name spoofing via sideloading.
     * If this field is present and does not match the signing key of the installed receiver
     * service package, the web trigger event is discarded.
     */
    @DataClass.MaySetToNull
    @Nullable private String mCertDigest = null;

    /**
     * Additional data that the server may provide to the {@link IsolatedService}. This can be
     * {@code null} if the server does not need to provide any data other than the required fields.
     */
    @DataClass.MaySetToNull
    @Nullable private String mEventData = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/MeasurementWebTriggerEventParams.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ MeasurementWebTriggerEventParams(
            @NonNull Uri destinationUrl,
            @NonNull String appPackageName,
            @NonNull ComponentName isolatedService,
            @Nullable String certDigest,
            @Nullable String eventData) {
        this.mDestinationUrl = destinationUrl;
        AnnotationValidations.validate(
                NonNull.class, null, mDestinationUrl);
        this.mAppPackageName = appPackageName;
        AnnotationValidations.validate(
                NonNull.class, null, mAppPackageName);
        this.mIsolatedService = isolatedService;
        AnnotationValidations.validate(
                NonNull.class, null, mIsolatedService);
        this.mCertDigest = certDigest;
        this.mEventData = eventData;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The URL of the web page where the web trigger event occurred.
     */
    @DataClass.Generated.Member
    public @NonNull Uri getDestinationUrl() {
        return mDestinationUrl;
    }

    /**
     * The package name of the browser app where the web trigger event occurred.
     */
    @DataClass.Generated.Member
    public @NonNull String getAppPackageName() {
        return mAppPackageName;
    }

    /**
     * The package and class name of the {@link IsolatedService} that should process
     * the web trigger event.
     */
    @DataClass.Generated.Member
    public @NonNull ComponentName getIsolatedService() {
        return mIsolatedService;
    }

    /**
     * An optional SHA-256 hash of the signing key of the package that contains
     * the {@link IsolatedService}, to guard against package name spoofing via sideloading.
     * If this field is present and does not match the signing key of the installed receiver
     * service package, the web trigger event is discarded.
     */
    @DataClass.Generated.Member
    public @Nullable String getCertDigest() {
        return mCertDigest;
    }

    /**
     * Additional data that the server may provide to the {@link IsolatedService}. This can be
     * {@code null} if the server does not need to provide any data other than the required fields.
     */
    @DataClass.Generated.Member
    public @Nullable String getEventData() {
        return mEventData;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(MeasurementWebTriggerEventParams other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        MeasurementWebTriggerEventParams that = (MeasurementWebTriggerEventParams) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mDestinationUrl, that.mDestinationUrl)
                && java.util.Objects.equals(mAppPackageName, that.mAppPackageName)
                && java.util.Objects.equals(mIsolatedService, that.mIsolatedService)
                && java.util.Objects.equals(mCertDigest, that.mCertDigest)
                && java.util.Objects.equals(mEventData, that.mEventData);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mDestinationUrl);
        _hash = 31 * _hash + java.util.Objects.hashCode(mAppPackageName);
        _hash = 31 * _hash + java.util.Objects.hashCode(mIsolatedService);
        _hash = 31 * _hash + java.util.Objects.hashCode(mCertDigest);
        _hash = 31 * _hash + java.util.Objects.hashCode(mEventData);
        return _hash;
    }

    /**
     * A builder for {@link MeasurementWebTriggerEventParams}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull Uri mDestinationUrl;
        private @NonNull String mAppPackageName;
        private @NonNull ComponentName mIsolatedService;
        private @Nullable String mCertDigest;
        private @Nullable String mEventData;

        private long mBuilderFieldsSet = 0L;

        /**
         * Creates a new Builder.
         *
         * @param destinationUrl
         *   The URL of the web page where the web trigger event occurred.
         * @param appPackageName
         *   The package name of the browser app where the web trigger event occurred.
         * @param isolatedService
         *   The package and class name of the {@link IsolatedService} that should process
         *   the web trigger event.
         */
        public Builder(
                @NonNull Uri destinationUrl,
                @NonNull String appPackageName,
                @NonNull ComponentName isolatedService) {
            mDestinationUrl = destinationUrl;
            AnnotationValidations.validate(
                    NonNull.class, null, mDestinationUrl);
            mAppPackageName = appPackageName;
            AnnotationValidations.validate(
                    NonNull.class, null, mAppPackageName);
            mIsolatedService = isolatedService;
            AnnotationValidations.validate(
                    NonNull.class, null, mIsolatedService);
        }

        /**
         * The URL of the web page where the web trigger event occurred.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setDestinationUrl(@NonNull Uri value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mDestinationUrl = value;
            return this;
        }

        /**
         * The package name of the browser app where the web trigger event occurred.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setAppPackageName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mAppPackageName = value;
            return this;
        }

        /**
         * The package and class name of the {@link IsolatedService} that should process
         * the web trigger event.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setIsolatedService(@NonNull ComponentName value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mIsolatedService = value;
            return this;
        }

        /**
         * An optional SHA-256 hash of the signing key of the package that contains
         * the {@link IsolatedService}, to guard against package name spoofing via sideloading.
         * If this field is present and does not match the signing key of the installed receiver
         * service package, the web trigger event is discarded.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setCertDigest(@Nullable String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mCertDigest = value;
            return this;
        }

        /**
         * Additional data that the server may provide to the {@link IsolatedService}. This can be
         * {@code null} if the server does not need to provide any data other than the required fields.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setEventData(@Nullable String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mEventData = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull MeasurementWebTriggerEventParams build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x20; // Mark builder used

            if ((mBuilderFieldsSet & 0x8) == 0) {
                mCertDigest = null;
            }
            if ((mBuilderFieldsSet & 0x10) == 0) {
                mEventData = null;
            }
            MeasurementWebTriggerEventParams o = new MeasurementWebTriggerEventParams(
                    mDestinationUrl,
                    mAppPackageName,
                    mIsolatedService,
                    mCertDigest,
                    mEventData);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x20) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1707269583667L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/MeasurementWebTriggerEventParams.java",
            inputSignatures = "private @android.annotation.NonNull android.net.Uri mDestinationUrl\nprivate @android.annotation.NonNull java.lang.String mAppPackageName\nprivate @android.annotation.NonNull android.content.ComponentName mIsolatedService\nprivate @com.android.ondevicepersonalization.internal.util.DataClass.MaySetToNull @android.annotation.Nullable java.lang.String mCertDigest\nprivate @com.android.ondevicepersonalization.internal.util.DataClass.MaySetToNull @android.annotation.Nullable java.lang.String mEventData\nclass MeasurementWebTriggerEventParams extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
