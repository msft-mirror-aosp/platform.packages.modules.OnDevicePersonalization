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
    /** The id of the event. */
    private final long mEventId;

    /** The id of the query. */
    private final long mQueryId;

    /** Index of the associated entry in the request log for this event. */
    private final long mRowIndex;

    /** Name of the service package for this event */
    @NonNull
    private final String mServicePackageName;

    /** {@link EventType} defining the type of event */
    private final int mType;

    /** Time of the event in milliseconds. */
    private final long mTimeMillis;

    /** Blob representing the event. */
    @Nullable
    private final byte[] mEventData;



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
            long eventId,
            long queryId,
            long rowIndex,
            @NonNull String servicePackageName,
            int type,
            long timeMillis,
            @Nullable byte[] eventData) {
        this.mEventId = eventId;
        this.mQueryId = queryId;
        this.mRowIndex = rowIndex;
        this.mServicePackageName = servicePackageName;
        AnnotationValidations.validate(
                NonNull.class, null, mServicePackageName);
        this.mType = type;
        this.mTimeMillis = timeMillis;
        this.mEventData = eventData;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The id of the event.
     */
    @DataClass.Generated.Member
    public long getEventId() {
        return mEventId;
    }

    /**
     * The id of the query.
     */
    @DataClass.Generated.Member
    public long getQueryId() {
        return mQueryId;
    }

    /**
     * Index of the associated entry in the request log for this event.
     */
    @DataClass.Generated.Member
    public long getRowIndex() {
        return mRowIndex;
    }

    /**
     * Name of the service package for this event
     */
    @DataClass.Generated.Member
    public @NonNull String getServicePackageName() {
        return mServicePackageName;
    }

    /**
     * {@link EventType} defining the type of event
     */
    @DataClass.Generated.Member
    public int getType() {
        return mType;
    }

    /**
     * Time of the event in milliseconds.
     */
    @DataClass.Generated.Member
    public long getTimeMillis() {
        return mTimeMillis;
    }

    /**
     * Blob representing the event.
     */
    @DataClass.Generated.Member
    public @Nullable byte[] getEventData() {
        return mEventData;
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
                && mEventId == that.mEventId
                && mQueryId == that.mQueryId
                && mRowIndex == that.mRowIndex
                && java.util.Objects.equals(mServicePackageName, that.mServicePackageName)
                && mType == that.mType
                && mTimeMillis == that.mTimeMillis
                && java.util.Arrays.equals(mEventData, that.mEventData);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Long.hashCode(mEventId);
        _hash = 31 * _hash + Long.hashCode(mQueryId);
        _hash = 31 * _hash + Long.hashCode(mRowIndex);
        _hash = 31 * _hash + java.util.Objects.hashCode(mServicePackageName);
        _hash = 31 * _hash + mType;
        _hash = 31 * _hash + Long.hashCode(mTimeMillis);
        _hash = 31 * _hash + java.util.Arrays.hashCode(mEventData);
        return _hash;
    }

    /**
     * A builder for {@link Event}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static class Builder {

        private long mEventId;
        private long mQueryId;
        private long mRowIndex;
        private @NonNull String mServicePackageName;
        private int mType;
        private long mTimeMillis;
        private @Nullable byte[] mEventData;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Creates a new Builder.
         *
         * @param eventId
         *   The id of the event.
         * @param queryId
         *   The id of the query.
         * @param rowIndex
         *   Index of the associated entry in the request log for this event.
         * @param servicePackageName
         *   Name of the service package for this event
         * @param type
         *   {@link EventType} defining the type of event
         * @param timeMillis
         *   Time of the event in milliseconds.
         * @param eventData
         *   Blob representing the event.
         */
        public Builder(
                long eventId,
                long queryId,
                long rowIndex,
                @NonNull String servicePackageName,
                int type,
                long timeMillis,
                @Nullable byte[] eventData) {
            mEventId = eventId;
            mQueryId = queryId;
            mRowIndex = rowIndex;
            mServicePackageName = servicePackageName;
            AnnotationValidations.validate(
                    NonNull.class, null, mServicePackageName);
            mType = type;
            mTimeMillis = timeMillis;
            mEventData = eventData;
        }

        /**
         * The id of the event.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setEventId(long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mEventId = value;
            return this;
        }

        /**
         * The id of the query.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setQueryId(long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mQueryId = value;
            return this;
        }

        /**
         * Index of the associated entry in the request log for this event.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRowIndex(long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mRowIndex = value;
            return this;
        }

        /**
         * Name of the service package for this event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setServicePackageName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mServicePackageName = value;
            return this;
        }

        /**
         * {@link EventType} defining the type of event
         */
        @DataClass.Generated.Member
        public @NonNull Builder setType(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mType = value;
            return this;
        }

        /**
         * Time of the event in milliseconds.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTimeMillis(long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x20;
            mTimeMillis = value;
            return this;
        }

        /**
         * Blob representing the event.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setEventData(@NonNull byte... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x40;
            mEventData = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull Event build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x80; // Mark builder used

            Event o = new Event(
                    mEventId,
                    mQueryId,
                    mRowIndex,
                    mServicePackageName,
                    mType,
                    mTimeMillis,
                    mEventData);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x80) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1686610284923L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/data/events/Event.java",
            inputSignatures = "private final  long mEventId\nprivate final  long mQueryId\nprivate final  long mRowIndex\nprivate final @android.annotation.NonNull java.lang.String mServicePackageName\nprivate final  int mType\nprivate final  long mTimeMillis\nprivate final @android.annotation.Nullable byte[] mEventData\nclass Event extends java.lang.Object implements [java.io.Serializable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
