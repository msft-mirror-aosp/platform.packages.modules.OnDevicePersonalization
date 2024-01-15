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

/** The input data for {@link IsolatedWorker#onTrainingExamples}. */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
@DataClass(genBuilder = false, genHiddenConstructor = true, genEqualsHashCode = true)
public final class TrainingExamplesInput {
    /**
     * The name of the federated compute population. It should match the population name in {@link
     * FederatedComputeInput#getPopulationName}.
     */
    @NonNull private String mPopulationName = "";

    /**
     * The name of the task within the population. It should match task plan configured at remote
     * federated compute server. One population may have multiple tasks. The task name can be used
     * to uniquely identify the job.
     */
    @NonNull private String mTaskName = "";

    /**
     * Token used to support the resumption of training. If client app wants to use resumption token
     * to track what examples are already used in previous federated compute jobs, it need set
     * {@link TrainingExamplesOutput.Builder#setResumptionTokens}, OnDevicePersonalization will
     * store it and pass it here for generating new training examples.
     */
    @Nullable private byte[] mResumptionToken = null;

    /** @hide */
    public TrainingExamplesInput(@NonNull TrainingExamplesInputParcel parcel) {
        this(parcel.getPopulationName(), parcel.getTaskName(), parcel.getResumptionToken());
    }

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExampleInput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    /**
     * Creates a new TrainingExampleInput.
     *
     * @param populationName The name of the federated compute population.
     * @param taskName The name of the task within the population. One population may have multiple
     *     tasks. The task name can be used to uniquely identify the job.
     * @param resumptionToken Token used to support the resumption of training.
     * @hide
     */
    @DataClass.Generated.Member
    public TrainingExamplesInput(
            @NonNull String populationName,
            @NonNull String taskName,
            @Nullable byte[] resumptionToken) {
        this.mPopulationName = populationName;
        AnnotationValidations.validate(NonNull.class, null, mPopulationName);
        this.mTaskName = taskName;
        AnnotationValidations.validate(NonNull.class, null, mTaskName);
        this.mResumptionToken = resumptionToken;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The name of the federated compute population. It should match the population name in {@link
     * FederatedComputeInput#getPopulationName}.
     */
    @DataClass.Generated.Member
    public @NonNull String getPopulationName() {
        return mPopulationName;
    }

    /**
     * The name of the task within the population. It should match task plan configured at remote
     * federated compute server. One population may have multiple tasks. The task name can be used
     * to uniquely identify the job.
     */
    @DataClass.Generated.Member
    public @NonNull String getTaskName() {
        return mTaskName;
    }

    /**
     * Token used to support the resumption of training. If client app wants to use resumption token
     * to track what examples are already used in previous federated compute jobs, it need set
     * {@link TrainingExamplesOutput.Builder#setResumptionTokens}, OnDevicePersonalization will
     * store it and pass it here for generating new training examples.
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
        TrainingExamplesInput that = (TrainingExamplesInput) o;
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

    @DataClass.Generated(
            time = 1699394018457L,
            codegenVersion = "1.0.23",
            sourceFile =
                    "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingExampleInput.java",
            inputSignatures =
                    "private @android.annotation.NonNull java.lang.String mPopulationName\nprivate @android.annotation.NonNull java.lang.String mTaskName\nprivate @android.annotation.Nullable byte[] mResumptionToken\nclass TrainingExampleInput extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=false, genHiddenConstructor=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}

    // @formatter:on
    // End of generated code

}
