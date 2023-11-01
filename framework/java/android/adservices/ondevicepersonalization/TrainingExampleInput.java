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

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.function.Consumer;

/**
 * The input data for {@link IsolatedWorker#onTrainingExample(TrainingExampleInput, Consumer)}
 *
 * @hide
 */
@DataClass(genHiddenBuilder = true, genEqualsHashCode = true)
public final class TrainingExampleInput implements Parcelable {
    /** The name of the federated compute population. */
    @NonNull private String mPopulationName = "";

    /**
     * The name of the task within the population. One population may have multiple tasks.
     * The task name can be used to uniquely identify the job.
     */
    @NonNull private String mTaskName = "";

    /** Token used to support the resumption of training. */
    @Nullable private byte[] mResumptionToken = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExampleInput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ TrainingExampleInput(
            @NonNull String populationName,
            @NonNull String taskName,
            @Nullable byte[] resumptionToken) {
        this.mPopulationName = populationName;
        AnnotationValidations.validate(
                NonNull.class, null, mPopulationName);
        this.mTaskName = taskName;
        AnnotationValidations.validate(
                NonNull.class, null, mTaskName);
        this.mResumptionToken = resumptionToken;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The name of the federated compute population.
     */
    @DataClass.Generated.Member
    public @NonNull String getPopulationName() {
        return mPopulationName;
    }

    /**
     * The name of the task within the population. One population may have multiple tasks.
     * The task name can be used to uniquely identify the job.
     */
    @DataClass.Generated.Member
    public @NonNull String getTaskName() {
        return mTaskName;
    }

    /**
     * Token used to support the resumption of training.
     */
    @DataClass.Generated.Member
    public @Nullable byte[] getResumptionToken() {
        return mResumptionToken;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(TrainingExampleInput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        TrainingExampleInput that = (TrainingExampleInput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mPopulationName, that.mPopulationName)
                && java.util.Objects.equals(mTaskName, that.mTaskName)
                && java.util.Arrays.equals(mResumptionToken, that.mResumptionToken);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mPopulationName);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTaskName);
        _hash = 31 * _hash + java.util.Arrays.hashCode(mResumptionToken);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeString(mPopulationName);
        dest.writeString(mTaskName);
        dest.writeByteArray(mResumptionToken);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ TrainingExampleInput(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        String populationName = in.readString();
        String taskName = in.readString();
        byte[] resumptionToken = in.createByteArray();

        this.mPopulationName = populationName;
        AnnotationValidations.validate(
                NonNull.class, null, mPopulationName);
        this.mTaskName = taskName;
        AnnotationValidations.validate(
                NonNull.class, null, mTaskName);
        this.mResumptionToken = resumptionToken;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<TrainingExampleInput> CREATOR
            = new Parcelable.Creator<TrainingExampleInput>() {
        @Override
        public TrainingExampleInput[] newArray(int size) {
            return new TrainingExampleInput[size];
        }

        @Override
        public TrainingExampleInput createFromParcel(@NonNull android.os.Parcel in) {
            return new TrainingExampleInput(in);
        }
    };

    /**
     * A builder for {@link TrainingExampleInput}
     * @hide
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull String mPopulationName;
        private @NonNull String mTaskName;
        private @Nullable byte[] mResumptionToken;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * The name of the federated compute population.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setPopulationName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mPopulationName = value;
            return this;
        }

        /**
         * The name of the task within the population. One population may have multiple tasks.
         * The task name can be used to uniquely identify the job.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTaskName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mTaskName = value;
            return this;
        }

        /**
         * Token used to support the resumption of training.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setResumptionToken(@NonNull byte... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mResumptionToken = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull TrainingExampleInput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mPopulationName = "";
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mTaskName = "";
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mResumptionToken = null;
            }
            TrainingExampleInput o = new TrainingExampleInput(
                    mPopulationName,
                    mTaskName,
                    mResumptionToken);
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
            time = 1697577073626L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExampleInput.java",
            inputSignatures = "private @android.annotation.NonNull java.lang.String mPopulationName\nprivate @android.annotation.NonNull java.lang.String mTaskName\nprivate @android.annotation.Nullable byte[] mResumptionToken\nclass TrainingExampleInput extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genHiddenBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
