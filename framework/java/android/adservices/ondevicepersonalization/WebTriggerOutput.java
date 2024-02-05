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

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.Collections;
import java.util.List;

/**
 * The result returned by
 * {@link IsolatedWorker#onWebTrigger(WebTriggerInput, java.util.function.Consumer)}.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class WebTriggerOutput {
    /**
     * Persistent data to be written to the REQUESTS table after
     * {@link IsolatedWorker#onWebTrigger(WebTriggerInput, java.util.function.Consumer)}
     * completes. If null, no persistent data will be written.
     */
    @Nullable private RequestLogRecord mRequestLogRecord = null;

    /**
     * A list of {@link EventLogRecord}. Writes events to the EVENTS table and associates
     * them with requests with the specified corresponding {@link RequestLogRecord} from
     * {@link EventLogRecord#getRequestLogRecord()}.
     * If the event does not contain a {@link RequestLogRecord} that was previously written
     * by this service, the {@link EventLogRecord} is not written.
     *
     */
    @DataClass.PluralOf("eventLogRecord")
    @NonNull private List<EventLogRecord> mEventLogRecords = Collections.emptyList();



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/WebTriggerOutput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ WebTriggerOutput(
            @Nullable RequestLogRecord requestLogRecord,
            @NonNull List<EventLogRecord> eventLogRecords) {
        this.mRequestLogRecord = requestLogRecord;
        this.mEventLogRecords = eventLogRecords;
        AnnotationValidations.validate(
                NonNull.class, null, mEventLogRecords);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Persistent data to be written to the REQUESTS table after
     * {@link IsolatedWorker#onWebTrigger(WebTriggerInput, java.util.function.Consumer)}
     * completes. If null, no persistent data will be written.
     */
    @DataClass.Generated.Member
    public @Nullable RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    /**
     * A list of {@link EventLogRecord}. Writes events to the EVENTS table and associates
     * them with requests with the specified corresponding {@link RequestLogRecord} from
     * {@link EventLogRecord#getRequestLogRecord()}.
     * If the event does not contain a {@link RequestLogRecord} that was previously written
     * by this service, the {@link EventLogRecord} is not written.
     */
    @DataClass.Generated.Member
    public @NonNull List<EventLogRecord> getEventLogRecords() {
        return mEventLogRecords;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(WebTriggerOutput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        WebTriggerOutput that = (WebTriggerOutput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mRequestLogRecord, that.mRequestLogRecord)
                && java.util.Objects.equals(mEventLogRecords, that.mEventLogRecords);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mRequestLogRecord);
        _hash = 31 * _hash + java.util.Objects.hashCode(mEventLogRecords);
        return _hash;
    }

    /**
     * A builder for {@link WebTriggerOutput}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable RequestLogRecord mRequestLogRecord;
        private @NonNull List<EventLogRecord> mEventLogRecords;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Persistent data to be written to the REQUESTS table after
         * {@link IsolatedWorker#onWebTrigger(WebTriggerInput, java.util.function.Consumer)}
         * completes. If null, no persistent data will be written.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRequestLogRecord(@NonNull RequestLogRecord value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mRequestLogRecord = value;
            return this;
        }

        /**
         * A list of {@link EventLogRecord}. Writes events to the EVENTS table and associates
         * them with requests with the specified corresponding {@link RequestLogRecord} from
         * {@link EventLogRecord#getRequestLogRecord()}.
         * If the event does not contain a {@link RequestLogRecord} that was previously written
         * by this service, the {@link EventLogRecord} is not written.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setEventLogRecords(@NonNull List<EventLogRecord> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mEventLogRecords = value;
            return this;
        }

        /** @see #setEventLogRecords */
        @DataClass.Generated.Member
        public @NonNull Builder addEventLogRecord(@NonNull EventLogRecord value) {
            if (mEventLogRecords == null) setEventLogRecords(new java.util.ArrayList<>());
            mEventLogRecords.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull WebTriggerOutput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mRequestLogRecord = null;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mEventLogRecords = Collections.emptyList();
            }
            WebTriggerOutput o = new WebTriggerOutput(
                    mRequestLogRecord,
                    mEventLogRecords);
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
            time = 1704482032122L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/WebTriggerOutput.java",
            inputSignatures = "private @android.annotation.Nullable android.adservices.ondevicepersonalization.RequestLogRecord mRequestLogRecord\nprivate @com.android.ondevicepersonalization.internal.util.DataClass.PluralOf(\"eventLogRecord\") @android.annotation.NonNull java.util.List<android.adservices.ondevicepersonalization.EventLogRecord> mEventLogRecords\nclass WebTriggerOutput extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
