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

package android.federatedcompute.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * Training options when schedule federated computation job.
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class TrainingOptions implements Parcelable {
    /** A unique JobScheduler job ID for the task. Must be non-zero. */
    private int mJobSchedulerJobId = 0;

    /** The task name to be provided to the federated compute server during checkin. */
    @NonNull private String mPopulationName = "";

    @Nullable private TrainingInterval mTrainingInterval = null;

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/ondevicepersonalization/federatedcompute/TrainingOptions.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    @DataClass.Generated.Member
    /* package-private */ TrainingOptions(
            int jobSchedulerJobId,
            @NonNull String populationName,
            @Nullable TrainingInterval trainingInterval) {
        this.mJobSchedulerJobId = jobSchedulerJobId;
        this.mPopulationName = populationName;
        AnnotationValidations.validate(NonNull.class, null, mPopulationName);
        this.mTrainingInterval = trainingInterval;

        // onConstructed(); // You can define this method to get a callback
    }

    /** A unique JobScheduler job ID for the task. Must be non-zero. */
    @DataClass.Generated.Member
    public int getJobSchedulerJobId() {
        return mJobSchedulerJobId;
    }

    /** The task name to be provided to the federated compute server during checkin. */
    @DataClass.Generated.Member
    public @NonNull String getPopulationName() {
        return mPopulationName;
    }

    @DataClass.Generated.Member
    public @Nullable TrainingInterval getTrainingInterval() {
        return mTrainingInterval;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(TrainingOptions other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        TrainingOptions that = (TrainingOptions) o;
        //noinspection PointlessBooleanExpression
        return true
                && mJobSchedulerJobId == that.mJobSchedulerJobId
                && java.util.Objects.equals(mPopulationName, that.mPopulationName)
                && java.util.Objects.equals(mTrainingInterval, that.mTrainingInterval);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mJobSchedulerJobId;
        _hash = 31 * _hash + java.util.Objects.hashCode(mPopulationName);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTrainingInterval);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mTrainingInterval != null) flg |= 0x4;
        dest.writeByte(flg);
        dest.writeInt(mJobSchedulerJobId);
        dest.writeString(mPopulationName);
        if (mTrainingInterval != null) dest.writeTypedObject(mTrainingInterval, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ TrainingOptions(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        int jobSchedulerJobId = in.readInt();
        String populationName = in.readString();
        TrainingInterval trainingInterval =
                (flg & 0x4) == 0
                        ? null
                        : (TrainingInterval) in.readTypedObject(TrainingInterval.CREATOR);

        this.mJobSchedulerJobId = jobSchedulerJobId;
        this.mPopulationName = populationName;
        AnnotationValidations.validate(NonNull.class, null, mPopulationName);
        this.mTrainingInterval = trainingInterval;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<TrainingOptions> CREATOR =
            new Parcelable.Creator<TrainingOptions>() {
                @Override
                public TrainingOptions[] newArray(int size) {
                    return new TrainingOptions[size];
                }

                @Override
                public TrainingOptions createFromParcel(@NonNull android.os.Parcel in) {
                    return new TrainingOptions(in);
                }
            };

    /** A builder for {@link TrainingOptions} */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private int mJobSchedulerJobId;
        private @NonNull String mPopulationName;
        private @Nullable TrainingInterval mTrainingInterval;

        private long mBuilderFieldsSet = 0L;

        public Builder() {}

        /** A unique JobScheduler job ID for the task. Must be non-zero. */
        @DataClass.Generated.Member
        public @NonNull Builder setJobSchedulerJobId(int value) {
            checkNotUsed();
            Preconditions.checkArgument(value != 0);
            mBuilderFieldsSet |= 0x1;
            mJobSchedulerJobId = value;
            return this;
        }

        /** The task name to be provided to the federated compute server during checkin. */
        @DataClass.Generated.Member
        public @NonNull Builder setPopulationName(@NonNull String value) {
            checkNotUsed();
            Preconditions.checkStringNotEmpty(value);
            mBuilderFieldsSet |= 0x2;
            mPopulationName = value;
            return this;
        }

        @DataClass.Generated.Member
        public @NonNull Builder setTrainingInterval(@NonNull TrainingInterval value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mTrainingInterval = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull TrainingOptions build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mJobSchedulerJobId = 0;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mPopulationName = "";
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mTrainingInterval = null;
            }
            TrainingOptions o =
                    new TrainingOptions(mJobSchedulerJobId, mPopulationName, mTrainingInterval);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x8) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    // @formatter:on
    // End of generated code

}
