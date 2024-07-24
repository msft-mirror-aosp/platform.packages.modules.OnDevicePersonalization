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

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.internal.util.Preconditions;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.Collections;
import java.util.List;

/** The output data of {@link IsolatedWorker#onTrainingExamples} */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class TrainingExamplesOutput {
    /**
     * The list of training example byte arrays. The format is a binary serialized <a
     * href="https://github.com/tensorflow/tensorflow/blob/master/tensorflow/core/example/example.proto">
     * tensorflow.Example</a> proto. The maximum allowed example size is 50KB.
     */
    @NonNull
    @DataClass.PluralOf("trainingExampleRecord")
    private List<TrainingExampleRecord> mTrainingExampleRecords = Collections.emptyList();

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExamplesOutput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    @DataClass.Generated.Member
    /* package-private */ TrainingExamplesOutput(
            @NonNull List<TrainingExampleRecord> trainingExampleRecords) {
        this.mTrainingExampleRecords = trainingExampleRecords;
        AnnotationValidations.validate(NonNull.class, null, mTrainingExampleRecords);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The list of training example byte arrays. The format is a binary serialized <a
     * href="https://github.com/tensorflow/tensorflow/blob/master/tensorflow/core/example/example.proto">
     * tensorflow.Example</a> proto. The maximum allowed example size is 50KB.
     */
    @DataClass.Generated.Member
    public @NonNull List<TrainingExampleRecord> getTrainingExampleRecords() {
        return mTrainingExampleRecords;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(TrainingExamplesOutput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        TrainingExamplesOutput that = (TrainingExamplesOutput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mTrainingExampleRecords, that.mTrainingExampleRecords);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mTrainingExampleRecords);
        return _hash;
    }

    /** A builder for {@link TrainingExamplesOutput} */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull List<TrainingExampleRecord> mTrainingExampleRecords;

        private long mBuilderFieldsSet = 0L;

        public Builder() {}

        /**
         * The list of training example byte arrays. The format is a binary serialized <a
         * href="https://github.com/tensorflow/tensorflow/blob/master/tensorflow/core/example/example.proto">
         * tensorflow.Example</a> proto. The maximum allowed example size is 50KB.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTrainingExampleRecords(
                @NonNull List<TrainingExampleRecord> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mTrainingExampleRecords = value;
            return this;
        }

        /**
         * @see #setTrainingExampleRecords
         */
        @DataClass.Generated.Member
        public @NonNull Builder addTrainingExampleRecord(@NonNull TrainingExampleRecord value) {
            if (mTrainingExampleRecords == null)
                setTrainingExampleRecords(new java.util.ArrayList<>());
            mTrainingExampleRecords.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull TrainingExamplesOutput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mTrainingExampleRecords = Collections.emptyList();
            }
            TrainingExamplesOutput o = new TrainingExamplesOutput(mTrainingExampleRecords);
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
            time = 1704915709729L,
            codegenVersion = "1.0.23",
            sourceFile =
                    "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExamplesOutput.java",
            inputSignatures =
                    "private @android.annotation.NonNull"
                        + " @com.android.ondevicepersonalization.internal.util.DataClass.PluralOf(\"trainingExampleRecord\")"
                        + " java.util.List<android.adservices.ondevicepersonalization.TrainingExampleRecord>"
                        + " mTrainingExampleRecords\n"
                        + "class TrainingExamplesOutput extends java.lang.Object implements []\n"
                        + "@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true,"
                        + " genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}

    // @formatter:on
    // End of generated code

}
