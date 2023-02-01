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

package com.android.ondevicepersonalization.services.data.events;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.io.Serializable;

/**
 * Event object for the Events table
 */
@DataClass(
        genBuilder = true,
        genEqualsHashCode = true
)
public class Event implements Serializable {
    /** The id of the query. */
    @NonNull
    private final long mQueryId;

    /** Time of the event in milliseconds. */
    @NonNull
    private final long mTimeMillis;

    /** Id of the slot owner for this event */
    @NonNull
    private final String mSlotId;

    /** Id of the bidder for this event */
    @NonNull
    private final String mBidId;

    /** Name of the service package or this event */
    @NonNull
    private final String mServicePackageName;

    /** The position of the event in the slot */
    @NonNull
    private final int mSlotPosition;

    /** {@link EventType} defining the type of event */
    @NonNull
    private final int mType;

    /** Blob representing the event. */
    @NonNull
    private final byte[] mEvent;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/data/events/Event.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ Event(
            @NonNull long queryId,
            @NonNull long timeMillis,
            @NonNull String slotId,
            @NonNull String bidId,
            @NonNull String servicePackageName,
            @NonNull int slotPosition,
            @NonNull int type,
            @NonNull byte[] event) {
        this.mQueryId = queryId;
        AnnotationValidations.validate(
                NonNull.class, null, mQueryId);
        this.mTimeMillis = timeMillis;
        AnnotationValidations.validate(
                NonNull.class, null, mTimeMillis);
        this.mSlotId = slotId;
        AnnotationValidations.validate(
                NonNull.class, null, mSlotId);
        this.mBidId = bidId;
        AnnotationValidations.validate(
                NonNull.class, null, mBidId);
        this.mServicePackageName = servicePackageName;
        AnnotationValidations.validate(
                NonNull.class, null, mServicePackageName);
        this.mSlotPosition = slotPosition;
        AnnotationValidations.validate(
                NonNull.class, null, mSlotPosition);
        this.mType = type;
        AnnotationValidations.validate(
                NonNull.class, null, mType);
        this.mEvent = event;
        AnnotationValidations.validate(
                NonNull.class, null, mEvent);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The id of the query.
     */
    @DataClass.Generated.Member
    public @NonNull long getQueryId() {
        return mQueryId;
    }

    /**
     * Time of the query in milliseconds.
     */
    @DataClass.Generated.Member
    public @NonNull long getTimeMillis() {
        return mTimeMillis;
    }

    /**
     * Id of the slot owner for this event
     */
    @DataClass.Generated.Member
    public @NonNull String getSlotId() {
        return mSlotId;
    }

    /**
     * Id of the bidder for this event
     */
    @DataClass.Generated.Member
    public @NonNull String getBidId() {
        return mBidId;
    }

    /**
     * Name of the service package or this event
     */
    @DataClass.Generated.Member
    public @NonNull String getServicePackageName() {
        return mServicePackageName;
    }

    /**
     * The position of the event in the slot
     */
    @DataClass.Generated.Member
    public @NonNull int getSlotPosition() {
        return mSlotPosition;
    }

    /**
     * {@link EventType} defining the type of event
     */
    @DataClass.Generated.Member
    public @NonNull int getType() {
        return mType;
    }

    /**
     * Blob representing the event.
     */
    @DataClass.Generated.Member
    public @NonNull byte[] getEvent() {
        return mEvent;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(Event other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        Event that = (Event) o;
        //noinspection PointlessBooleanExpression
        return true
                && mQueryId == that.mQueryId
                && mTimeMillis == that.mTimeMillis
                && java.util.Objects.equals(mSlotId, that.mSlotId)
                && java.util.Objects.equals(mBidId, that.mBidId)
                && java.util.Objects.equals(mServicePackageName, that.mServicePackageName)
                && mSlotPosition == that.mSlotPosition
                && mType == that.mType
                && java.util.Arrays.equals(mEvent, that.mEvent);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Long.hashCode(mQueryId);
        _hash = 31 * _hash + Long.hashCode(mTimeMillis);
        _hash = 31 * _hash + java.util.Objects.hashCode(mSlotId);
        _hash = 31 * _hash + java.util.Objects.hashCode(mBidId);
        _hash = 31 * _hash + java.util.Objects.hashCode(mServicePackageName);
        _hash = 31 * _hash + mSlotPosition;
        _hash = 31 * _hash + mType;
        _hash = 31 * _hash + java.util.Arrays.hashCode(mEvent);
        return _hash;
    }

    /**
     * A builder for {@link Event}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static class Builder {

        private @NonNull long mQueryId;
        private @NonNull long mTimeMillis;
        private @NonNull String mSlotId;
        private @NonNull String mBidId;
        private @NonNull String mServicePackageName;
        private @NonNull int mSlotPosition;
        private @NonNull int mType;
        private @NonNull byte[] mEvent;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Creates a new Builder.
         *
         * @param queryId
         *   The id of the query.
         * @param timeMillis
         *   Time of the query in milliseconds.
         * @param slotId
         *   Id of the slot owner for this event
         * @param bidId
         *   Id of the bidder for this event
         * @param servicePackageName
         *   Name of the service package or this event
         * @param slotPosition
         *   The position of the event in the slot
         * @param type
         *   {@link EventType} defining the type of event
         * @param event
         *   Blob representing the event.
         */
        public Builder(
                @NonNull long queryId,
                @NonNull long timeMillis,
                @NonNull String slotId,
                @NonNull String bidId,
                @NonNull String servicePackageName,
                @NonNull int slotPosition,
                @NonNull int type,
                @NonNull byte[] event) {
            mQueryId = queryId;
            AnnotationValidations.validate(
                    NonNull.class, null, mQueryId);
            mTimeMillis = timeMillis;
            AnnotationValidations.validate(
                    NonNull.class, null, mTimeMillis);
            mSlotId = slotId;
            AnnotationValidations.validate(
                    NonNull.class, null, mSlotId);
            mBidId = bidId;
            AnnotationValidations.validate(
                    NonNull.class, null, mBidId);
            mServicePackageName = servicePackageName;
            AnnotationValidations.validate(
                    NonNull.class, null, mServicePackageName);
            mSlotPosition = slotPosition;
            AnnotationValidations.validate(
                    NonNull.class, null, mSlotPosition);
            mType = type;
            AnnotationValidations.validate(
                    NonNull.class, null, mType);
            mEvent = event;
            AnnotationValidations.validate(
                    NonNull.class, null, mEvent);
        }

        /**
         * The id of the query.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setQueryId(@NonNull long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mQueryId = value;
            return this;
        }

        /**
         * Time of the query in milliseconds.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTimeMillis(@NonNull long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mTimeMillis = value;
            return this;
        }

        /**
         * Id of the slot owner for this event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setSlotId(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mSlotId = value;
            return this;
        }

        /**
         * Id of the bidder for this event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setBidId(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mBidId = value;
            return this;
        }

        /**
         * Name of the service package or this event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setServicePackageName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mServicePackageName = value;
            return this;
        }

        /**
         * The position of the event in the slot
         */
        @DataClass.Generated.Member
        public @NonNull Builder setSlotPosition(@NonNull int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x20;
            mSlotPosition = value;
            return this;
        }

        /**
         * {@link EventType} defining the type of event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setType(@NonNull int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x40;
            mType = value;
            return this;
        }

        /**
         * Blob representing the event.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setEvent(@NonNull byte... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x80;
            mEvent = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull Event build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x100; // Mark builder used

            Event o = new Event(
                    mQueryId,
                    mTimeMillis,
                    mSlotId,
                    mBidId,
                    mServicePackageName,
                    mSlotPosition,
                    mType,
                    mEvent);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x100) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1674839291488L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/data/events/Event.java",
            inputSignatures = "private final @android.annotation.NonNull long mQueryId\nprivate final @android.annotation.NonNull long mTimeMillis\nprivate final @android.annotation.NonNull java.lang.String mSlotId\nprivate final @android.annotation.NonNull java.lang.String mBidId\nprivate final @android.annotation.NonNull java.lang.String mServicePackageName\nprivate final @android.annotation.NonNull int mSlotPosition\nprivate final @android.annotation.NonNull int mType\nprivate final @android.annotation.NonNull byte[] mEvent\nclass Event extends java.lang.Object implements [java.io.Serializable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
