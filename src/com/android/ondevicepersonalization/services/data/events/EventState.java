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

package com.android.ondevicepersonalization.services.data.events;

import android.annotation.NonNull;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * EventState object for the EventState table
 */
@DataClass(
        genBuilder = true,
        genEqualsHashCode = true
)
public class EventState {
    /** Token representing the event state. */
    @NonNull
    private final byte[] mToken;

    /** Name of the service package for this event */
    @NonNull
    private final String mServicePackageName;

    /** Unique identifier of the task for processing this event */
    @NonNull
    private final String mTaskIdentifier;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/data/events/EventState.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ EventState(
            @NonNull byte[] token,
            @NonNull String servicePackageName,
            @NonNull String taskIdentifier) {
        this.mToken = token;
        AnnotationValidations.validate(
                NonNull.class, null, mToken);
        this.mServicePackageName = servicePackageName;
        AnnotationValidations.validate(
                NonNull.class, null, mServicePackageName);
        this.mTaskIdentifier = taskIdentifier;
        AnnotationValidations.validate(
                NonNull.class, null, mTaskIdentifier);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Token representing the event state.
     */
    @DataClass.Generated.Member
    public @NonNull byte[] getToken() {
        return mToken;
    }

    /**
     * Name of the service package for this event
     */
    @DataClass.Generated.Member
    public @NonNull String getServicePackageName() {
        return mServicePackageName;
    }

    /**
     * Unique identifier of the task for processing this event
     */
    @DataClass.Generated.Member
    public @NonNull String getTaskIdentifier() {
        return mTaskIdentifier;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(EventState other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        EventState that = (EventState) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Arrays.equals(mToken, that.mToken)
                && java.util.Objects.equals(mServicePackageName, that.mServicePackageName)
                && java.util.Objects.equals(mTaskIdentifier, that.mTaskIdentifier);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Arrays.hashCode(mToken);
        _hash = 31 * _hash + java.util.Objects.hashCode(mServicePackageName);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTaskIdentifier);
        return _hash;
    }

    /**
     * A builder for {@link EventState}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static class Builder {

        private @NonNull byte[] mToken;
        private @NonNull String mServicePackageName;
        private @NonNull String mTaskIdentifier;

        private long mBuilderFieldsSet = 0L;

        public Builder() {}

        /**
         * Creates a new Builder.
         *
         * @param token
         *   Token representing the event state.
         * @param servicePackageName
         *   Name of the service package for this event
         * @param taskIdentifier
         *   Unique identifier of the task for processing this event
         */
        public Builder(
                @NonNull byte[] token,
                @NonNull String servicePackageName,
                @NonNull String taskIdentifier) {
            mToken = token;
            AnnotationValidations.validate(
                    NonNull.class, null, mToken);
            mServicePackageName = servicePackageName;
            AnnotationValidations.validate(
                    NonNull.class, null, mServicePackageName);
            mTaskIdentifier = taskIdentifier;
            AnnotationValidations.validate(
                    NonNull.class, null, mTaskIdentifier);
        }

        /**
         * Token representing the event state.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setToken(@NonNull byte... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mToken = value;
            return this;
        }

        /**
         * Name of the service package for this event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setServicePackageName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mServicePackageName = value;
            return this;
        }

        /**
         * Unique identifier of the task for processing this event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTaskIdentifier(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mTaskIdentifier = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull EventState build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8; // Mark builder used

            EventState o = new EventState(
                    mToken,
                    mServicePackageName,
                    mTaskIdentifier);
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
            time = 1695678195125L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/data/events/EventState.java",
            inputSignatures = "private final @android.annotation.NonNull byte[] mToken\nprivate final @android.annotation.NonNull java.lang.String mServicePackageName\nprivate final @android.annotation.NonNull java.lang.String mTaskIdentifier\nclass EventState extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
