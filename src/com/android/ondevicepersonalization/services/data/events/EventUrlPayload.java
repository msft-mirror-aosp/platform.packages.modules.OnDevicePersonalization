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
import android.annotation.Nullable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.io.Serializable;

/**
 * EventUrlPayload object for the ODP encrypted URLs
 */
@DataClass(
        genBuilder = true,
        genEqualsHashCode = true
)
public class EventUrlPayload implements Serializable {
    /** Event object for the payload. */
    @NonNull
    private final Event mEvent;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/data/events/EventUrlPayload.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ EventUrlPayload(
            @NonNull Event event) {
        this.mEvent = event;
        AnnotationValidations.validate(
                NonNull.class, null, mEvent);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Event object for the payload.
     */
    @DataClass.Generated.Member
    public @NonNull Event getEvent() {
        return mEvent;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(EventUrlPayload other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        EventUrlPayload that = (EventUrlPayload) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mEvent, that.mEvent);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mEvent);
        return _hash;
    }

    /**
     * A builder for {@link EventUrlPayload}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static class Builder {

        private @NonNull Event mEvent;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Creates a new Builder.
         *
         * @param event
         *   Event object for the payload.
         */
        public Builder(
                @NonNull Event event) {
            mEvent = event;
            AnnotationValidations.validate(
                    NonNull.class, null, mEvent);
        }

        /**
         * Event object for the payload.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setEvent(@NonNull Event value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mEvent = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull EventUrlPayload build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            EventUrlPayload o = new EventUrlPayload(
                    mEvent);
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
            time = 1681338798343L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/data/events/EventUrlPayload.java",
            inputSignatures = "private final @android.annotation.NonNull com.android.ondevicepersonalization.services.data.events.Event mEvent\nclass EventUrlPayload extends java.lang.Object implements [java.io.Serializable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
