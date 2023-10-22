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

import static android.adservices.ondevicepersonalization.Constants.KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS;

import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

// TODO(b/289102463): Add a link to the public doc for the EVENTS table when available.
/**
 * Data to be logged in the EVENTS table.
 *
 * Each record in the EVENTS table is associated with one row from an existing
 * {@link RequestLogRecord} in the requests table {@link RequestLogRecord#getRows()}.
 * The purpose of the EVENTS table is to add supplemental information to logged data
 * from a prior request, e.g., logging an event when a link in a rendered WebView is
 * clicked {@code IsolatedWorker#onEvent(EventInput, java.util.function.Consumer)}.
 * The contents of the EVENTS table can be
 * consumed by Federated Learning facilitated model training, or Federated Analytics facilitated
 * cross-device statistical analysis.
 */
@FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class EventLogRecord implements Parcelable {
    /**
     * The index of the row in an existing {@link RequestLogRecord} that this payload should be
     * associated with.
     **/
    private @IntRange(from = 0) int mRowIndex = 0;

    /**
     * The service-assigned identifier that identifies this payload. Each row in
     * {@link RequestLogRecord} can be associated with up to one event of a specified type.
     * The platform drops events if another event with the same type already exists for a row
     * in {@link RequestLogRecord}. Must be >0 and <128. This allows up to 127 events to be
     * written for each row in {@link RequestLogRecord}. If unspecified, the default is 1.
     */
    private @IntRange(from = 1, to = 127) int mType = 1;

    /**
     * Time of the event in milliseconds.
     * @hide
     */
    private long mTimeMillis = 0;

    /**
     * Additional data to be logged. Can be null if no additional data needs to be written as part
     * of the event, and only the occurrence of the event needs to be logged.
     */
    @Nullable ContentValues mData = null;

    /**
     * The existing {@link RequestLogRecord} that this payload should be associated with. In an
     * implementation of
     * {@link IsolatedWorker#onExecute(ExecuteInput, java.util.function.Consumer)}, this should be
     * set to a value returned by {@link LogReader#getRequests(long, long)}. In an implementation
     * of {@link IsolatedWorker#onEvent(EventInput, java.util.function.Consumer)}, this should be
     * set to {@code null} because the payload will be automatically associated with the current
     * {@link RequestLogRecord}.
     *
     * @hide
     */
    @Nullable RequestLogRecord mRequestLogRecord = null;


    abstract static class BaseBuilder {
        /**
         * @hide
         */
        public abstract Builder setTimeMillis(long value);
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/EventLogRecord.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ EventLogRecord(
            @IntRange(from = 0) int rowIndex,
            @IntRange(from = 1, to = 127) int type,
            long timeMillis,
            @Nullable ContentValues data,
            @Nullable RequestLogRecord requestLogRecord) {
        this.mRowIndex = rowIndex;
        AnnotationValidations.validate(
                IntRange.class, null, mRowIndex,
                "from", 0);
        this.mType = type;
        AnnotationValidations.validate(
                IntRange.class, null, mType,
                "from", 1,
                "to", 127);
        this.mTimeMillis = timeMillis;
        this.mData = data;
        this.mRequestLogRecord = requestLogRecord;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The index of the row in an existing {@link RequestLogRecord} that this payload should be
     * associated with.
     */
    @DataClass.Generated.Member
    public @IntRange(from = 0) int getRowIndex() {
        return mRowIndex;
    }

    /**
     * The service-assigned identifier that identifies this payload. Each row in
     * {@link RequestLogRecord} can be associated with up to one event of a specified type.
     * The platform drops events if another event with the same type already exists for a row
     * in {@link RequestLogRecord}. Must be >0 and <128. This allows up to 127 events to be
     * written for each row in {@link RequestLogRecord}. If unspecified, the default is 1.
     */
    @DataClass.Generated.Member
    public @IntRange(from = 1, to = 127) int getType() {
        return mType;
    }

    /**
     * Time of the event in milliseconds.
     *
     * @hide
     */
    @DataClass.Generated.Member
    public long getTimeMillis() {
        return mTimeMillis;
    }

    /**
     * Additional data to be logged. Can be null if no additional data needs to be written as part
     * of the event, and only the occurrence of the event needs to be logged.
     */
    @DataClass.Generated.Member
    public @Nullable ContentValues getData() {
        return mData;
    }

    /**
     * The existing {@link RequestLogRecord} that this payload should be associated with. In an
     * implementation of
     * {@link IsolatedWorker#onExecute(ExecuteInput, java.util.function.Consumer)}, this should be
     * set to a value returned by {@link LogReader#getRequests(long, long)}. In an implementation
     * of {@link IsolatedWorker#onEvent(EventInput, java.util.function.Consumer)}, this should be
     * set to {@code null} because the payload will be automatically associated with the current
     * {@link RequestLogRecord}.
     *
     * @hide
     */
    @DataClass.Generated.Member
    public @Nullable RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(EventLogRecord other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        EventLogRecord that = (EventLogRecord) o;
        //noinspection PointlessBooleanExpression
        return true
                && mRowIndex == that.mRowIndex
                && mType == that.mType
                && mTimeMillis == that.mTimeMillis
                && java.util.Objects.equals(mData, that.mData)
                && java.util.Objects.equals(mRequestLogRecord, that.mRequestLogRecord);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mRowIndex;
        _hash = 31 * _hash + mType;
        _hash = 31 * _hash + Long.hashCode(mTimeMillis);
        _hash = 31 * _hash + java.util.Objects.hashCode(mData);
        _hash = 31 * _hash + java.util.Objects.hashCode(mRequestLogRecord);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mData != null) flg |= 0x8;
        if (mRequestLogRecord != null) flg |= 0x10;
        dest.writeByte(flg);
        dest.writeInt(mRowIndex);
        dest.writeInt(mType);
        dest.writeLong(mTimeMillis);
        if (mData != null) dest.writeTypedObject(mData, flags);
        if (mRequestLogRecord != null) dest.writeTypedObject(mRequestLogRecord, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ EventLogRecord(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        int rowIndex = in.readInt();
        int type = in.readInt();
        long timeMillis = in.readLong();
        ContentValues data = (flg & 0x8) == 0 ? null : (ContentValues) in.readTypedObject(ContentValues.CREATOR);
        RequestLogRecord requestLogRecord = (flg & 0x10) == 0 ? null : (RequestLogRecord) in.readTypedObject(RequestLogRecord.CREATOR);

        this.mRowIndex = rowIndex;
        AnnotationValidations.validate(
                IntRange.class, null, mRowIndex,
                "from", 0);
        this.mType = type;
        AnnotationValidations.validate(
                IntRange.class, null, mType,
                "from", 1,
                "to", 127);
        this.mTimeMillis = timeMillis;
        this.mData = data;
        this.mRequestLogRecord = requestLogRecord;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<EventLogRecord> CREATOR
            = new Parcelable.Creator<EventLogRecord>() {
        @Override
        public EventLogRecord[] newArray(int size) {
            return new EventLogRecord[size];
        }

        @Override
        public EventLogRecord createFromParcel(@NonNull android.os.Parcel in) {
            return new EventLogRecord(in);
        }
    };

    /**
     * A builder for {@link EventLogRecord}
     */
    @FlaggedApi(KEY_ENABLE_ONDEVICEPERSONALIZATION_APIS)
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder extends BaseBuilder {

        private @IntRange(from = 0) int mRowIndex;
        private @IntRange(from = 1, to = 127) int mType;
        private long mTimeMillis;
        private @Nullable ContentValues mData;
        private @Nullable RequestLogRecord mRequestLogRecord;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The index of the row in an existing {@link RequestLogRecord} that this payload should be
         * associated with.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRowIndex(@IntRange(from = 0) int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mRowIndex = value;
            return this;
        }

        /**
         * The service-assigned identifier that identifies this payload. Each row in
         * {@link RequestLogRecord} can be associated with up to one event of a specified type.
         * The platform drops events if another event with the same type already exists for a row
         * in {@link RequestLogRecord}. Must be >0 and <128. This allows up to 127 events to be
         * written for each row in {@link RequestLogRecord}. If unspecified, the default is 1.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setType(@IntRange(from = 1, to = 127) int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mType = value;
            return this;
        }

        /**
         * Time of the event in milliseconds.
         *
         * @hide
         */
        @DataClass.Generated.Member
        @Override
        public @NonNull Builder setTimeMillis(long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mTimeMillis = value;
            return this;
        }

        /**
         * Additional data to be logged. Can be null if no additional data needs to be written as part
         * of the event, and only the occurrence of the event needs to be logged.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setData(@NonNull ContentValues value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mData = value;
            return this;
        }

        /**
         * The existing {@link RequestLogRecord} that this payload should be associated with. In an
         * implementation of
         * {@link IsolatedWorker#onExecute(ExecuteInput, java.util.function.Consumer)}, this should be
         * set to a value returned by {@link LogReader#getRequests(long, long)}. In an implementation
         * of {@link IsolatedWorker#onEvent(EventInput, java.util.function.Consumer)}, this should be
         * set to {@code null} because the payload will be automatically associated with the current
         * {@link RequestLogRecord}.
         *
         * @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRequestLogRecord(@NonNull RequestLogRecord value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mRequestLogRecord = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull EventLogRecord build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x20; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mRowIndex = 0;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mType = 1;
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mTimeMillis = 0;
            }
            if ((mBuilderFieldsSet & 0x8) == 0) {
                mData = null;
            }
            if ((mBuilderFieldsSet & 0x10) == 0) {
                mRequestLogRecord = null;
            }
            EventLogRecord o = new EventLogRecord(
                    mRowIndex,
                    mType,
                    mTimeMillis,
                    mData,
                    mRequestLogRecord);
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
            time = 1697576750150L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/EventLogRecord.java",
            inputSignatures = "private @android.annotation.IntRange int mRowIndex\nprivate @android.annotation.IntRange int mType\nprivate  long mTimeMillis\n @android.annotation.Nullable android.content.ContentValues mData\n @android.annotation.Nullable android.adservices.ondevicepersonalization.RequestLogRecord mRequestLogRecord\nclass EventLogRecord extends java.lang.Object implements [android.os.Parcelable]\npublic abstract  android.adservices.ondevicepersonalization.EventLogRecord.Builder setTimeMillis(long)\nclass BaseBuilder extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)\npublic abstract  android.adservices.ondevicepersonalization.EventLogRecord.Builder setTimeMillis(long)\nclass BaseBuilder extends java.lang.Object implements []")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
