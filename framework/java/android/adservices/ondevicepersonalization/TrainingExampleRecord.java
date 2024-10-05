/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * One record of {@link TrainingExamplesOutput}.
 */
@DataClass(genBuilder = true, genAidl = false)
public final class TrainingExampleRecord implements Parcelable {
    /**
     * Training example byte arrays. The format is a binary serialized <a
     * href="https://github.com/tensorflow/tensorflow/blob/master/tensorflow/core/example/example.proto">
     * tensorflow.Example</a> proto. The maximum allowed example size is 50KB.
     */
    @DataClass.MaySetToNull
    @Nullable private byte[] mTrainingExample = null;

    /**
     * The resumption token byte arrays corresponding to training examples. The last processed
     * example's corresponding resumption token will be passed to {@link
     * IsolatedWorker#onTrainingExamples} to support resumption.
     */
    @DataClass.MaySetToNull
    @Nullable private byte[] mResumptionToken = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExampleRecord.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ TrainingExampleRecord(
            @Nullable byte[] trainingExample,
            @Nullable byte[] resumptionToken) {
        this.mTrainingExample = trainingExample;
        this.mResumptionToken = resumptionToken;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Training example byte arrays. The format is a binary serialized <a
     * href="https://github.com/tensorflow/tensorflow/blob/master/tensorflow/core/example/example.proto">
     * tensorflow.Example</a> proto. The maximum allowed example size is 50KB.
     */
    @DataClass.Generated.Member
    public @Nullable byte[] getTrainingExample() {
        return mTrainingExample;
    }

    /**
     * The resumption token byte arrays corresponding to training examples. The last processed
     * example's corresponding resumption token will be passed to {@link
     * IsolatedWorker#onTrainingExamples} to support resumption.
     */
    @DataClass.Generated.Member
    public @Nullable byte[] getResumptionToken() {
        return mResumptionToken;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeByteArray(mTrainingExample);
        dest.writeByteArray(mResumptionToken);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ TrainingExampleRecord(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte[] trainingExample = in.createByteArray();
        byte[] resumptionToken = in.createByteArray();

        this.mTrainingExample = trainingExample;
        this.mResumptionToken = resumptionToken;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<TrainingExampleRecord> CREATOR
            = new Parcelable.Creator<TrainingExampleRecord>() {
        @Override
        public TrainingExampleRecord[] newArray(int size) {
            return new TrainingExampleRecord[size];
        }

        @Override
        public TrainingExampleRecord createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new TrainingExampleRecord(in);
        }
    };

    /**
     * A builder for {@link TrainingExampleRecord}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @Nullable byte[] mTrainingExample;
        private @Nullable byte[] mResumptionToken;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Training example byte arrays. The format is a binary serialized <a
         * href="https://github.com/tensorflow/tensorflow/blob/master/tensorflow/core/example/example.proto">
         * tensorflow.Example</a> proto. The maximum allowed example size is 50KB.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setTrainingExample(@Nullable byte... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mTrainingExample = value;
            return this;
        }

        /**
         * The resumption token byte arrays corresponding to training examples. The last processed
         * example's corresponding resumption token will be passed to {@link
         * IsolatedWorker#onTrainingExamples} to support resumption.
         */
        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setResumptionToken(@Nullable byte... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mResumptionToken = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @android.annotation.NonNull TrainingExampleRecord build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mTrainingExample = null;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mResumptionToken = null;
            }
            TrainingExampleRecord o = new TrainingExampleRecord(
                    mTrainingExample,
                    mResumptionToken);
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
            time = 1707253849218L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExampleRecord.java",
            inputSignatures = "private @com.android.ondevicepersonalization.internal.util.DataClass.MaySetToNull @android.annotation.Nullable byte[] mTrainingExample\nprivate @com.android.ondevicepersonalization.internal.util.DataClass.MaySetToNull @android.annotation.Nullable byte[] mResumptionToken\nclass TrainingExampleRecord extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genAidl=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
