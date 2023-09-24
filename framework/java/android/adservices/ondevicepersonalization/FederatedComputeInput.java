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

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * The input data for {@link FederatedComputeScheduler#schedule(FederatedComputeScheduler.Params,
 * FederatedComputeInput)}
 *
 * @hide
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public class FederatedComputeInput {
    /**
     * Population refers to a collection of devices that specific task groups can run on. It should
     * match task plan configured at remote federated computation server. TODO(b/300461799): add
     * federated compute server document.
     */
    @NonNull private final String mPopulationName;

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/FederatedComputeInput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    @DataClass.Generated.Member
    /* package-private */ FederatedComputeInput(@NonNull String populationName) {
        this.mPopulationName = populationName;
        AnnotationValidations.validate(NonNull.class, null, mPopulationName);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Population refers to a collection of devices that specific task groups can run on. It should
     * match task plan configured at remote federated computation server. TODO(b/300461799): add
     * federated compute server document.
     */
    @DataClass.Generated.Member
    public @NonNull String getPopulationName() {
        return mPopulationName;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(FederatedComputeInput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        FederatedComputeInput that = (FederatedComputeInput) o;
        //noinspection PointlessBooleanExpression
        return true && java.util.Objects.equals(mPopulationName, that.mPopulationName);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mPopulationName);
        return _hash;
    }

    /** A builder for {@link FederatedComputeInput} */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static class Builder {

        private @NonNull String mPopulationName;

        private long mBuilderFieldsSet = 0L;

        public Builder() {}

        /**
         * Population refers to a collection of devices that specific task groups can run on. It
         * should match task plan configured at remote federated computation server.
         * TODO(b/300461799): add federated compute server document.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setPopulationName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mPopulationName = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull FederatedComputeInput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            FederatedComputeInput o = new FederatedComputeInput(mPopulationName);
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
            time = 1695236318180L,
            codegenVersion = "1.0.23",
            sourceFile =
                    "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/FederatedComputeInput.java",
            inputSignatures =
                    "private final @android.annotation.NonNull java.lang.String mPopulationName\nclass FederatedComputeInput extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}

    // @formatter:on
    // End of generated code

}
