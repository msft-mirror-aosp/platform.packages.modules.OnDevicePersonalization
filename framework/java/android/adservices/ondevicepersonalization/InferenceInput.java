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

import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.SuppressLint;

import com.android.internal.util.Preconditions;
import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.ByteArrayUtil;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Contains all the information needed for a run of model inference. The input of {@link
 * ModelManager#run}.
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class InferenceInput {
    /** The configuration that controls runtime interpreter behavior. */
    @NonNull private Params mParams;

    /**
     * A byte array that holds input data. The inputs should be in the same order as inputs of the
     * model.
     *
     * <p>For LiteRT, this field is mapped to inputs of runForMultipleInputsOutputs:
     * https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
     *
     * <pre>{@code
     * String[] input0 = {"foo", "bar"}; // string tensor shape is [2].
     * int[] input1 = new int[]{3, 2, 1}; // int tensor shape is [3].
     * Object[] inputData = {input0, input1, ...};
     * }</pre>
     *
     * <p>For Executorch model, this field is a serialized EValue array.
     *
     * @hide
     */
    @NonNull private byte[] mData;

    /**
     * The number of input examples. Adopter can set this field to run batching inference. The batch
     * size is 1 by default. The batch size should match the input data size.
     */
    private int mBatchSize = 1;

    /**
     * The empty InferenceOutput representing the expected output structure. For LiteRT, the
     * inference code will verify whether this expected output structure matches model output
     * signature.
     *
     * <p>If a model produce string tensors:
     *
     * <pre>{@code
     * String[] output = new String[3][2];  // Output tensor shape is [3, 2].
     * HashMap<Integer, Object> outputs = new HashMap<>();
     * outputs.put(0, output);
     * expectedOutputStructure = new InferenceOutput.Builder().setDataOutputs(outputs).build();
     * }</pre>
     */
    @NonNull private InferenceOutput mExpectedOutputStructure;

    @DataClass(genBuilder = true, genHiddenConstructor = true, genEqualsHashCode = true)
    public static class Params {
        /**
         * A {@link KeyValueStore} where pre-trained model is stored. Only supports LiteRT model
         * now.
         */
        @NonNull private KeyValueStore mKeyValueStore;

        /**
         * The key of the table where the corresponding value stores a pre-trained model. Only
         * supports LiteRT model now.
         */
        @NonNull private String mModelKey;

        /** The model inference will run on CPU. */
        public static final int DELEGATE_CPU = 1;

        /**
         * The delegate to run model inference.
         *
         * @hide
         */
        @IntDef(
                prefix = "DELEGATE_",
                value = {DELEGATE_CPU})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Delegate {}

        /**
         * The delegate to run model inference. If not set, the default value is {@link
         * #DELEGATE_CPU}.
         */
        private @Delegate int mDelegateType = DELEGATE_CPU;

        /** The model is a tensorflow lite model. */
        public static final int MODEL_TYPE_TENSORFLOW_LITE = 1;

        /**
         * The model is an executorch model.
         *
         * @hide
         */
        public static final int MODEL_TYPE_EXECUTORCH = 2;

        /**
         * The type of the model.
         *
         * @hide
         */
        @IntDef(
                prefix = "MODEL_TYPE",
                value = {MODEL_TYPE_TENSORFLOW_LITE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface ModelType {}

        /**
         * The type of the pre-trained model. If not set, the default value is {@link
         * #MODEL_TYPE_TENSORFLOW_LITE} .
         */
        private @ModelType int mModelType = MODEL_TYPE_TENSORFLOW_LITE;

        /**
         * The number of threads used for intraop parallelism on CPU, must be positive number.
         * Adopters can set this field based on model architecture. The actual thread number depends
         * on system resources and other constraints.
         */
        private @IntRange(from = 1) int mRecommendedNumThreads = 1;

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
         * Creates a new Params.
         *
         * @param keyValueStore A {@link KeyValueStore} where pre-trained model is stored. Only
         *     supports LiteRT model now.
         * @param modelKey The key of the table where the corresponding value stores a pre-trained
         *     model. Only supports LiteRT model now.
         * @param delegateType The delegate to run model inference. If not set, the default value is
         *     {@link #DELEGATE_CPU}.
         * @param modelType The type of the pre-trained model. If not set, the default value is
         *     {@link #MODEL_TYPE_TENSORFLOW_LITE} .
         * @param recommendedNumThreads The number of threads used for intraop parallelism on CPU,
         *     must be positive number. Adopters can set this field based on model architecture. The
         *     actual thread number depends on system resources and other constraints.
         * @hide
         */
        @DataClass.Generated.Member
        public Params(
                @NonNull KeyValueStore keyValueStore,
                @NonNull String modelKey,
                @Delegate int delegateType,
                @ModelType int modelType,
                @IntRange(from = 1) int recommendedNumThreads) {
            this.mKeyValueStore = keyValueStore;
            AnnotationValidations.validate(NonNull.class, null, mKeyValueStore);
            this.mModelKey = modelKey;
            AnnotationValidations.validate(NonNull.class, null, mModelKey);
            this.mDelegateType = delegateType;
            AnnotationValidations.validate(Delegate.class, null, mDelegateType);
            this.mModelType = modelType;

            if (!(mModelType == MODEL_TYPE_TENSORFLOW_LITE)
                    && !(mModelType == MODEL_TYPE_EXECUTORCH)) {
                throw new java.lang.IllegalArgumentException(
                        "modelType was "
                                + mModelType
                                + " but must be one of: "
                                + "MODEL_TYPE_TENSORFLOW_LITE("
                                + MODEL_TYPE_TENSORFLOW_LITE
                                + "), "
                                + "MODEL_TYPE_EXECUTORCH("
                                + MODEL_TYPE_EXECUTORCH
                                + ")");
            }

            this.mRecommendedNumThreads = recommendedNumThreads;
            AnnotationValidations.validate(IntRange.class, null, mRecommendedNumThreads, "from", 1);

            // onConstructed(); // You can define this method to get a callback
        }

        /**
         * A {@link KeyValueStore} where pre-trained model is stored. Only supports LiteRT model
         * now.
         */
        @DataClass.Generated.Member
        public @NonNull KeyValueStore getKeyValueStore() {
            return mKeyValueStore;
        }

        /**
         * The key of the table where the corresponding value stores a pre-trained model. Only
         * supports LiteRT model now.
         */
        @DataClass.Generated.Member
        public @NonNull String getModelKey() {
            return mModelKey;
        }

        /**
         * The delegate to run model inference. If not set, the default value is {@link
         * #DELEGATE_CPU}.
         */
        @DataClass.Generated.Member
        public @Delegate int getDelegateType() {
            return mDelegateType;
        }

        /**
         * The type of the pre-trained model. If not set, the default value is {@link
         * #MODEL_TYPE_TENSORFLOW_LITE} .
         */
        @DataClass.Generated.Member
        public @ModelType int getModelType() {
            return mModelType;
        }

        /**
         * The number of threads used for intraop parallelism on CPU, must be positive number.
         * Adopters can set this field based on model architecture. The actual thread number depends
         * on system resources and other constraints.
         */
        @DataClass.Generated.Member
        public @IntRange(from = 1) int getRecommendedNumThreads() {
            return mRecommendedNumThreads;
        }

        @Override
        @DataClass.Generated.Member
        public boolean equals(@android.annotation.Nullable Object o) {
            // You can override field equality logic by defining either of the methods like:
            // boolean fieldNameEquals(Params other) { ... }
            // boolean fieldNameEquals(FieldType otherValue) { ... }

            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            @SuppressWarnings("unchecked")
            Params that = (Params) o;
            //noinspection PointlessBooleanExpression
            return true
                    && java.util.Objects.equals(mKeyValueStore, that.mKeyValueStore)
                    && java.util.Objects.equals(mModelKey, that.mModelKey)
                    && mDelegateType == that.mDelegateType
                    && mModelType == that.mModelType
                    && mRecommendedNumThreads == that.mRecommendedNumThreads;
        }

        @Override
        @DataClass.Generated.Member
        public int hashCode() {
            // You can override field hashCode logic by defining methods like:
            // int fieldNameHashCode() { ... }

            int _hash = 1;
            _hash = 31 * _hash + java.util.Objects.hashCode(mKeyValueStore);
            _hash = 31 * _hash + java.util.Objects.hashCode(mModelKey);
            _hash = 31 * _hash + mDelegateType;
            _hash = 31 * _hash + mModelType;
            _hash = 31 * _hash + mRecommendedNumThreads;
            return _hash;
        }

        /** A builder for {@link Params} */
        @SuppressWarnings("WeakerAccess")
        @DataClass.Generated.Member
        public static final class Builder {

            private @NonNull KeyValueStore mKeyValueStore;
            private @NonNull String mModelKey;
            private @Delegate int mDelegateType;
            private @ModelType int mModelType;
            private @IntRange(from = 1) int mRecommendedNumThreads;

            private long mBuilderFieldsSet = 0L;

            /**
             * Creates a new Builder.
             *
             * @param keyValueStore A {@link KeyValueStore} where pre-trained model is stored. Only
             *     supports LiteRT model now.
             * @param modelKey The key of the table where the corresponding value stores a
             *     pre-trained model. Only supports LiteRT model now.
             */
            public Builder(@NonNull KeyValueStore keyValueStore, @NonNull String modelKey) {
                mKeyValueStore = keyValueStore;
                AnnotationValidations.validate(NonNull.class, null, mKeyValueStore);
                mModelKey = modelKey;
                AnnotationValidations.validate(NonNull.class, null, mModelKey);
            }

            /**
             * A {@link KeyValueStore} where pre-trained model is stored. Only supports LiteRT model
             * now.
             */
            @DataClass.Generated.Member
            public @NonNull Builder setKeyValueStore(@NonNull KeyValueStore value) {
                mBuilderFieldsSet |= 0x1;
                mKeyValueStore = value;
                return this;
            }

            /**
             * The key of the table where the corresponding value stores a pre-trained model. Only
             * supports LiteRT model now.
             */
            @DataClass.Generated.Member
            public @NonNull Builder setModelKey(@NonNull String value) {
                mBuilderFieldsSet |= 0x2;
                mModelKey = value;
                return this;
            }

            /**
             * The delegate to run model inference. If not set, the default value is {@link
             * #DELEGATE_CPU}.
             */
            @DataClass.Generated.Member
            public @NonNull Builder setDelegateType(@Delegate int value) {
                mBuilderFieldsSet |= 0x4;
                mDelegateType = value;
                return this;
            }

            /**
             * The type of the pre-trained model. If not set, the default value is {@link
             * #MODEL_TYPE_TENSORFLOW_LITE} .
             */
            @DataClass.Generated.Member
            public @NonNull Builder setModelType(@ModelType int value) {
                mBuilderFieldsSet |= 0x8;
                mModelType = value;
                return this;
            }

            /**
             * The number of threads used for intraop parallelism on CPU, must be positive number.
             * Adopters can set this field based on model architecture. The actual thread number
             * depends on system resources and other constraints.
             */
            @DataClass.Generated.Member
            public @NonNull Builder setRecommendedNumThreads(@IntRange(from = 1) int value) {
                mBuilderFieldsSet |= 0x10;
                mRecommendedNumThreads = value;
                return this;
            }

            /** Builds the instance. This builder should not be touched after calling this! */
            public @NonNull Params build() {
                mBuilderFieldsSet |= 0x20; // Mark builder used

                if ((mBuilderFieldsSet & 0x4) == 0) {
                    mDelegateType = DELEGATE_CPU;
                }
                if ((mBuilderFieldsSet & 0x8) == 0) {
                    mModelType = MODEL_TYPE_TENSORFLOW_LITE;
                }
                if ((mBuilderFieldsSet & 0x10) == 0) {
                    mRecommendedNumThreads = 1;
                }
                Params o =
                        new Params(
                                mKeyValueStore,
                                mModelKey,
                                mDelegateType,
                                mModelType,
                                mRecommendedNumThreads);
                return o;
            }
        }

        @DataClass.Generated(
                time = 1730932395853L,
                codegenVersion = "1.0.23",
                sourceFile =
                        "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/InferenceInput.java",
                inputSignatures =
                        "private @android.annotation.NonNull android.adservices.ondevicepersonalization.KeyValueStore mKeyValueStore\nprivate @android.annotation.NonNull java.lang.String mModelKey\npublic static final  int DELEGATE_CPU\nprivate @android.adservices.ondevicepersonalization.Params.Delegate int mDelegateType\npublic static final  int MODEL_TYPE_TENSORFLOW_LITE\npublic static final  int MODEL_TYPE_EXECUTORCH\nprivate @android.adservices.ondevicepersonalization.Params.ModelType int mModelType\nprivate @android.annotation.IntRange int mRecommendedNumThreads\nclass Params extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genHiddenConstructor=true, genEqualsHashCode=true)")
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
            @NonNull Params params,
            @NonNull byte[] data,
            int batchSize,
            @NonNull InferenceOutput expectedOutputStructure) {
        this.mParams = params;
        AnnotationValidations.validate(NonNull.class, null, mParams);
        this.mData = data;
        AnnotationValidations.validate(NonNull.class, null, mData);
        this.mBatchSize = batchSize;
        this.mExpectedOutputStructure = expectedOutputStructure;
        AnnotationValidations.validate(NonNull.class, null, mExpectedOutputStructure);

        // onConstructed(); // You can define this method to get a callback
    }

    /** The configuration that controls runtime interpreter behavior. */
    @DataClass.Generated.Member
    public @NonNull Params getParams() {
        return mParams;
    }

    /**
     * A byte array that holds input data. The inputs should be in the same order as inputs of the
     * model.
     *
     * <p>For LiteRT, this field is mapped to inputs of runForMultipleInputsOutputs:
     * https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
     *
     * <pre>{@code
     * String[] input0 = {"foo", "bar"}; // string tensor shape is [2].
     * int[] input1 = new int[]{3, 2, 1}; // int tensor shape is [3].
     * Object[] inputData = {input0, input1, ...};
     * }</pre>
     *
     * <p>For Executorch model, this field is a serialized EValue array.
     *
     * @hide
     */
    @DataClass.Generated.Member
    public @NonNull byte[] getData() {
        return mData;
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
    @SuppressLint("ArrayReturn")
    @DataClass.Generated.Member
    public @NonNull Object[] getInputData() {
        return (Object[]) ByteArrayUtil.deserializeObject(mData);
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
     * The empty InferenceOutput representing the expected output structure. For LiteRT, the
     * inference code will verify whether this expected output structure matches model output
     * signature.
     *
     * <p>If a model produce string tensors:
     *
     * <pre>{@code
     * String[] output = new String[3][2];  // Output tensor shape is [3, 2].
     * HashMap<Integer, Object> outputs = new HashMap<>();
     * outputs.put(0, output);
     * expectedOutputStructure = new InferenceOutput.Builder().setDataOutputs(outputs).build();
     * }</pre>
     */
    @DataClass.Generated.Member
    public @NonNull InferenceOutput getExpectedOutputStructure() {
        return mExpectedOutputStructure;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(InferenceInput other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        InferenceInput that = (InferenceInput) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mParams, that.mParams)
                && java.util.Arrays.equals(mData, that.mData)
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
        _hash = 31 * _hash + java.util.Objects.hashCode(mParams);
        _hash = 31 * _hash + java.util.Arrays.hashCode(mData);
        _hash = 31 * _hash + mBatchSize;
        _hash = 31 * _hash + java.util.Objects.hashCode(mExpectedOutputStructure);
        return _hash;
    }

    abstract static class BaseBuilder {
        /** @hide */
        public abstract Builder setInputData(@NonNull Object... value);
    }

    /** A builder for {@link InferenceInput} */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder extends BaseBuilder {

        private @NonNull Params mParams;
        private @NonNull byte[] mData;
        private int mBatchSize;
        private @NonNull InferenceOutput mExpectedOutputStructure =
                new InferenceOutput.Builder().build();

        private long mBuilderFieldsSet = 0L;

        /**
         * Creates a new Builder.
         *
         * @param params The configuration that controls runtime interpreter behavior.
         * @param inputData An array of input data. The inputs should be in the same order as inputs
         *     of the model.
         *     <p>For example, if a model takes multiple inputs:
         *     <pre>{@code
         * String[] input0 = {"foo", "bar"}; // string tensor shape is [2].
         * int[] input1 = new int[]{3, 2, 1}; // int tensor shape is [3].
         * Object[] inputData = {input0, input1, ...};
         *
         * }</pre>
         *     For TFLite, this field is mapped to inputs of runForMultipleInputsOutputs:
         *     https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
         * @param expectedOutputStructure The empty InferenceOutput representing the expected output
         *     structure. For TFLite, the inference code will verify whether this expected output
         *     structure matches model output signature.
         *     <p>If a model produce string tensors:
         *     <pre>{@code
         * String[] output = new String[3][2];  // Output tensor shape is [3, 2].
         * HashMap<Integer, Object> outputs = new HashMap<>();
         * outputs.put(0, output);
         * expectedOutputStructure = new InferenceOutput.Builder().setDataOutputs(outputs).build();
         *
         * }</pre>
         */
        public Builder(
                @NonNull Params params,
                @SuppressLint("ArrayReturn") @NonNull Object[] inputData,
                @NonNull InferenceOutput expectedOutputStructure) {
            mParams = params;
            AnnotationValidations.validate(NonNull.class, null, mParams);
            AnnotationValidations.validate(NonNull.class, null, inputData);
            mData = ByteArrayUtil.serializeObject(inputData);
            AnnotationValidations.validate(NonNull.class, null, expectedOutputStructure);
            mExpectedOutputStructure = expectedOutputStructure;
        }

        /** @hide */
        public Builder() {}

        /** The configuration that controls runtime interpreter behavior. */
        @DataClass.Generated.Member
        public @NonNull Builder setParams(@NonNull Params value) {
            mBuilderFieldsSet |= 0x1;
            mParams = value;
            return this;
        }

        /**
         * A byte array that holds input data. The inputs should be in the same order as inputs of
         * the model.
         *
         * <p>For LiteRT, this field is mapped to inputs of runForMultipleInputsOutputs:
         * https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
         *
         * <pre>{@code
         * String[] input0 = {"foo", "bar"}; // string tensor shape is [2].
         * int[] input1 = new int[]{3, 2, 1}; // int tensor shape is [3].
         * Object[] inputData = {input0, input1, ...};
         * }</pre>
         *
         * <p>For Executorch model, this field is a serialized EValue array.
         *
         * @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setData(@NonNull byte... value) {
            mBuilderFieldsSet |= 0x2;
            mData = value;
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
         * For LiteRT, this field is mapped to inputs of runForMultipleInputsOutputs:
         * https://www.tensorflow.org/lite/api_docs/java/org/tensorflow/lite/InterpreterApi#parameters_9
         */
        @DataClass.Generated.Member
        public @NonNull Builder setInputData(@NonNull Object... value) {
            mBuilderFieldsSet |= 0x2;
            mData = ByteArrayUtil.serializeObject(value);
            return this;
        }

        /**
         * The number of input examples. Adopter can set this field to run batching inference. The
         * batch size is 1 by default. The batch size should match the input data size.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setBatchSize(int value) {
            mBuilderFieldsSet |= 0x4;
            mBatchSize = value;
            return this;
        }

        /**
         * The empty InferenceOutput representing the expected output structure. For LiteRT, the
         * inference code will verify whether this expected output structure matches model output
         * signature.
         *
         * <p>If a model produce string tensors:
         *
         * <pre>{@code
         * String[] output = new String[3][2];  // Output tensor shape is [3, 2].
         * HashMap<Integer, Object> outputs = new HashMap<>();
         * outputs.put(0, output);
         * expectedOutputStructure = new InferenceOutput.Builder().setDataOutputs(outputs).build();
         * }</pre>
         */
        @DataClass.Generated.Member
        public @NonNull Builder setExpectedOutputStructure(@NonNull InferenceOutput value) {
            mBuilderFieldsSet |= 0x8;
            mExpectedOutputStructure = value;
            return this;
        }

        /** @hide */
        public void validateInputData() {
            Preconditions.checkArgument(
                    mData.length > 0, "Input data should not be empty for InferenceInput.");
        }

        /** @hide */
        public void validateOutputStructure() {
            // ExecuTorch model doesn't require set output structure.
            if (mParams.getModelType() != Params.MODEL_TYPE_TENSORFLOW_LITE) {
                return;
            }
            Preconditions.checkArgument(
                    !mExpectedOutputStructure.getDataOutputs().isEmpty()
                            || mExpectedOutputStructure.getData().length > 0,
                    "ExpectedOutputStructure field is required for TensorflowLite model.");
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull InferenceInput build() {

            mBuilderFieldsSet |= 0x10; // Mark builder used

            if ((mBuilderFieldsSet & 0x4) == 0) {
                mBatchSize = 1;
            }
            validateInputData();
            validateOutputStructure();
            InferenceInput o =
                    new InferenceInput(mParams, mData, mBatchSize, mExpectedOutputStructure);
            return o;
        }
    }

    @DataClass.Generated(
            time = 1730932395877L,
            codegenVersion = "1.0.23",
            sourceFile =
                    "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/InferenceInput.java",
            inputSignatures =
                    "private @android.annotation.NonNull android.adservices.ondevicepersonalization.Params mParams\nprivate @android.annotation.NonNull byte[] mData\nprivate  int mBatchSize\nprivate @android.annotation.NonNull android.adservices.ondevicepersonalization.InferenceOutput mExpectedOutputStructure\nclass InferenceInput extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}

    // @formatter:on
    // End of generated code

}
