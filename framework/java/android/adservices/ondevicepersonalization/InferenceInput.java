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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Contains all the information needed for one run of model inference.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class InferenceInput {
    /** The configuration that controls runtime interpreter behavior. */
    @NonNull private Options mOptions;

    /**
     * An array of input data. The inputs should be in the same order as inputs of the model.
     *
     * <p>For example, if a model takes multiple inputs:
     *
     * <pre>{@code
     * String[] input0 = {"foo", "bar"}; // string tensor shape is [2].
     * int[] input1 = new int[]{3, 2, 1}; // int tensor shape is [3].
     * Object[] inputData = {input0, input1, ...};
     * }</pre>
     *
     * For TFLite, this field is mapped to inputs of runForMultipleInputsOutputs:
     * https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
     */
    @NonNull private Object[] mInputData;

    /**
     * The number of input examples. Adopter can set this field to run batching inference. The batch
     * size is 1 by default. The batch size should match the input data size.
     */
    private int mBatchSize = 1;

    /**
     * The empty InferenceOutput representing the expected output structure. For TFLite, the
     * inference code will verify whether this expected output structure matches model output
     * signature.
     *
     * <p>If a model produce string tensors:
     *
     * <pre>{@code
     * String[] output = new String[3][2];  // Output tensor shape is [3, 2].
     * HashMap<Integer, Object> outputs = new HashMap<>();
     * outputs.put(0, output);
     * expectedOutputStructure = new InferenceOutput.Builder().setData(outputs).build();
     * }</pre>
     */
    @NonNull private InferenceOutput mExpectedOutputStructure;

    @DataClass(genBuilder = false, genHiddenConstructor = true, genEqualsHashCode = true)
    public static class Options {
        /**
         * A {@link KeyValueStore} where pre-trained model is stored. Only supports TFLite model
         * now.
         */
        @NonNull private KeyValueStore mKeyValueStore;

        /**
         * The key of the table where the corresponding value stores a pre-trained model. Only
         * supports TFLite model now.
         */
        @NonNull private String mModelKey;

        public static final int DELEGATE_CPU = 1;

        /**
         * The delegate to run model inference. If not specified, CPU delegate is used by default.
         *
         * @hide
         */
        @IntDef(
                prefix = "DELEGATE_",
                value = {DELEGATE_CPU})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Delegate {}

        /**
         * The delegate to run model inference. If not specified, CPU delegate is used by default.
         */
        private @Delegate int mDelegate = 1;

        /**
         * The number of threads available to the interpreter. Only set and take effective when
         * input tensors are on CPU. Setting cpuNumThread to 0 has the effect to disable
         * multithreading, which is equivalent to setting cpuNumThread to 1. If set to the value -1,
         * the number of threads used will be implementation-defined and platform-dependent.
         */
        private @IntRange(from = -1, to = 4) int mCpuNumThread = -1;

        /**
         * Creates an options that uses CPU delegate.
         *
         * @param keyValueStore the table where pre-trained model is stored. Only supports TFLite
         *     model.
         * @param modelKey the key of the table where the corresponding value stores a pre-trained
         *     model.
         * @param cpuNumThread The number of threads available to the interpreter. num_threads
         *     should be >= -1 and <= 4. Setting cpuNumThread to 0 has the effect to disable
         *     multithreading, which is equivalent to setting cpuNumThread to 1. If set to the value
         *     -1, the number of threads used will be implementation-defined and platform-dependent.
         */
        public static Options createCpuOptions(
                KeyValueStore keyValueStore,
                String modelKey,
                @IntRange(from = -1, to = 4) int cpuNumThread) {
            return new Options(keyValueStore, modelKey, DELEGATE_CPU, cpuNumThread);
        }

        // Code below generated by codegen v1.0.23.
        //
        // DO NOT MODIFY!
        // CHECKSTYLE:OFF Generated code
        //
        // To regenerate run:
        // $ codegen
        // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/InferenceInput.java
        //
        // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
        //   Settings > Editor > Code Style > Formatter Control
        // @formatter:off

        /**
         * Creates a new Options.
         *
         * @param keyValueStore A {@link KeyValueStore} where pre-trained model is stored. Only
         *     supports TFLite model now.
         * @param modelKey The key of the table where the corresponding value stores a pre-trained
         *     model. Only supports TFLite model now.
         * @param delegate The delegate to run model inference. If not specified, CPU delegate is
         *     used by default.
         * @param cpuNumThread The number of threads available to the interpreter. Only set and take
         *     effective when input tensors are on CPU. Setting cpuNumThread to 0 has the effect to
         *     disable multithreading, which is equivalent to setting cpuNumThread to 1. If set to
         *     the value -1, the number of threads used will be implementation-defined and
         *     platform-dependent.
         * @hide
         */
        @DataClass.Generated.Member
        public Options(
                @NonNull KeyValueStore keyValueStore,
                @NonNull String modelKey,
                @Delegate int delegate,
                @IntRange(from = -1, to = 4) int cpuNumThread) {
            this.mKeyValueStore = keyValueStore;
            AnnotationValidations.validate(NonNull.class, null, mKeyValueStore);
            this.mModelKey = modelKey;
            AnnotationValidations.validate(NonNull.class, null, mModelKey);
            this.mDelegate = delegate;
            AnnotationValidations.validate(Delegate.class, null, mDelegate);
            this.mCpuNumThread = cpuNumThread;
            AnnotationValidations.validate(
                    IntRange.class, null, mCpuNumThread, "from", -1, "to", 4);

            // onConstructed(); // You can define this method to get a callback
        }

        /**
         * A {@link KeyValueStore} where pre-trained model is stored. Only supports TFLite model
         * now.
         */
        @DataClass.Generated.Member
        public @NonNull KeyValueStore getKeyValueStore() {
            return mKeyValueStore;
        }

        /**
         * The key of the table where the corresponding value stores a pre-trained model. Only
         * supports TFLite model now.
         */
        @DataClass.Generated.Member
        public @NonNull String getModelKey() {
            return mModelKey;
        }

        /**
         * The delegate to run model inference. If not specified, CPU delegate is used by default.
         */
        @DataClass.Generated.Member
        public @Delegate int getDelegate() {
            return mDelegate;
        }

        /**
         * The number of threads available to the interpreter. Only set and take effective when
         * input tensors are on CPU. Setting cpuNumThread to 0 has the effect to disable
         * multithreading, which is equivalent to setting cpuNumThread to 1. If set to the value -1,
         * the number of threads used will be implementation-defined and platform-dependent.
         */
        @DataClass.Generated.Member
        public @IntRange(from = -1, to = 4) int getCpuNumThread() {
            return mCpuNumThread;
        }

        @Override
        @DataClass.Generated.Member
        public boolean equals(@Nullable Object o) {
            // You can override field equality logic by defining either of the methods like:
            // boolean fieldNameEquals(Options other) { ... }
            // boolean fieldNameEquals(FieldType otherValue) { ... }

            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            @SuppressWarnings("unchecked")
            Options that = (Options) o;
            //noinspection PointlessBooleanExpression
            return true
                    && java.util.Objects.equals(mKeyValueStore, that.mKeyValueStore)
                    && java.util.Objects.equals(mModelKey, that.mModelKey)
                    && mDelegate == that.mDelegate
                    && mCpuNumThread == that.mCpuNumThread;
        }

        @Override
        @DataClass.Generated.Member
        public int hashCode() {
            // You can override field hashCode logic by defining methods like:
            // int fieldNameHashCode() { ... }

            int _hash = 1;
            _hash = 31 * _hash + java.util.Objects.hashCode(mKeyValueStore);
            _hash = 31 * _hash + java.util.Objects.hashCode(mModelKey);
            _hash = 31 * _hash + mDelegate;
            _hash = 31 * _hash + mCpuNumThread;
            return _hash;
        }

        @DataClass.Generated(
                time = 1706910628782L,
                codegenVersion = "1.0.23",
                sourceFile =
                        "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/InferenceInput.java",
                inputSignatures =
                        "private @android.annotation.NonNull android.adservices.ondevicepersonalization.KeyValueStore mKeyValueStore\nprivate @android.annotation.NonNull java.lang.String mModelKey\npublic static final  int DELEGATE_CPU\nprivate @android.adservices.ondevicepersonalization.Options.Delegate int mDelegate\nprivate @android.annotation.IntRange int mCpuNumThread\nclass Options extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=false, genHiddenConstructor=true, genEqualsHashCode=true)")
        @Deprecated
        private void __metadata() {}

        // @formatter:on
        // End of generated code

    }

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/InferenceInput.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    @DataClass.Generated.Member
    /* package-private */ InferenceInput(
            @NonNull Options options,
            @NonNull Object[] inputData,
            int batchSize,
            @NonNull InferenceOutput expectedOutputStructure) {
        this.mOptions = options;
        AnnotationValidations.validate(NonNull.class, null, mOptions);
        this.mInputData = inputData;
        AnnotationValidations.validate(NonNull.class, null, mInputData);
        this.mBatchSize = batchSize;
        this.mExpectedOutputStructure = expectedOutputStructure;
        AnnotationValidations.validate(NonNull.class, null, mExpectedOutputStructure);

        // onConstructed(); // You can define this method to get a callback
    }

    /** The configuration that controls runtime interpreter behavior. */
    @DataClass.Generated.Member
    public @NonNull Options getOptions() {
        return mOptions;
    }

    /**
     * An array of input data. The inputs should be in the same order as inputs of the model.
     *
     * <p>For example, if a model takes multiple inputs:
     *
     * <pre>{@code
     * String[] input0 = {"foo", "bar"}; // string tensor shape is [2].
     * int[] input1 = new int[]{3, 2, 1}; // int tensor shape is [3].
     * Object[] inputData = {input0, input1, ...};
     * }</pre>
     *
     * For TFLite, this field is mapped to inputs of runForMultipleInputsOutputs:
     * https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
     */
    @DataClass.Generated.Member
    public @NonNull Object[] getInputData() {
        return mInputData;
    }

    /**
     * The number of input examples. Adopter can set this field to run batching inference. The batch
     * size is 1 by default. The batch size should match the input data size.
     */
    @DataClass.Generated.Member
    public int getBatchSize() {
        return mBatchSize;
    }

    /**
     * The empty InferenceOutput representing the expected output structure. For TFLite, the
     * inference code will verify whether this expected output structure matches model output
     * signature.
     *
     * <p>If a model produce string tensors:
     *
     * <pre>{@code
     * String[] output = new String[3][2];  // Output tensor shape is [3, 2].
     * HashMap<Integer, Object> outputs = new HashMap<>();
     * outputs.put(0, output);
     * expectedOutputStructure = new InferenceOutput.Builder().setData(outputs).build();
     * }</pre>
     */
    @DataClass.Generated.Member
    public @NonNull InferenceOutput getExpectedOutputStructure() {
        return mExpectedOutputStructure;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(InferenceInput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        InferenceInput that = (InferenceInput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mOptions, that.mOptions)
                && java.util.Arrays.equals(mInputData, that.mInputData)
                && mBatchSize == that.mBatchSize
                && java.util.Objects.equals(
                        mExpectedOutputStructure, that.mExpectedOutputStructure);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mOptions);
        _hash = 31 * _hash + java.util.Arrays.hashCode(mInputData);
        _hash = 31 * _hash + mBatchSize;
        _hash = 31 * _hash + java.util.Objects.hashCode(mExpectedOutputStructure);
        return _hash;
    }

    /** A builder for {@link InferenceInput} */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull Options mOptions;
        private @NonNull Object[] mInputData;
        private int mBatchSize;
        private @NonNull InferenceOutput mExpectedOutputStructure;

        private long mBuilderFieldsSet = 0L;

        public Builder() {}

        /** The configuration that controls runtime interpreter behavior. */
        @DataClass.Generated.Member
        public @NonNull Builder setOptions(@NonNull Options value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mOptions = value;
            return this;
        }

        /**
         * An array of input data. The inputs should be in the same order as inputs of the model.
         *
         * <p>For example, if a model takes multiple inputs:
         *
         * <pre>{@code
         * String[] input0 = {"foo", "bar"}; // string tensor shape is [2].
         * int[] input1 = new int[]{3, 2, 1}; // int tensor shape is [3].
         * Object[] inputData = {input0, input1, ...};
         * }</pre>
         *
         * For TFLite, this field is mapped to inputs of runForMultipleInputsOutputs:
         * https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
         */
        @DataClass.Generated.Member
        public @NonNull Builder setInputData(@NonNull Object... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mInputData = value;
            return this;
        }

        /**
         * The number of input examples. Adopter can set this field to run batching inference. The
         * batch size is 1 by default. The batch size should match the input data size.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setBatchSize(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mBatchSize = value;
            return this;
        }

        /**
         * The empty InferenceOutput representing the expected output structure. For TFLite, the
         * inference code will verify whether this expected output structure matches model output
         * signature.
         *
         * <p>If a model produce string tensors:
         *
         * <pre>{@code
         * String[] output = new String[3][2];  // Output tensor shape is [3, 2].
         * HashMap<Integer, Object> outputs = new HashMap<>();
         * outputs.put(0, output);
         * expectedOutputStructure = new InferenceOutput.Builder().setData(outputs).build();
         * }</pre>
         */
        @DataClass.Generated.Member
        public @NonNull Builder setExpectedOutputStructure(@NonNull InferenceOutput value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mExpectedOutputStructure = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull InferenceInput build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10; // Mark builder used

            if ((mBuilderFieldsSet & 0x4) == 0) {
                mBatchSize = 1;
            }
            InferenceInput o =
                    new InferenceInput(mOptions, mInputData, mBatchSize, mExpectedOutputStructure);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x10) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1706910628816L,
            codegenVersion = "1.0.23",
            sourceFile =
                    "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/InferenceInput.java",
            inputSignatures =
                    "private @android.annotation.NonNull android.adservices.ondevicepersonalization.Options mOptions\nprivate @android.annotation.NonNull java.lang.Object[] mInputData\nprivate  int mBatchSize\nprivate @android.annotation.NonNull android.adservices.ondevicepersonalization.InferenceOutput mExpectedOutputStructure\nclass InferenceInput extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}

    // @formatter:on
    // End of generated code

}
