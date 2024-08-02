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

import android.annotation.NonNull;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

/** @hide */
@DataClass(genAidl = false, genBuilder = false)
public class ExecuteOptionsParcel implements Parcelable {
    /** Default value is OUTPUT_TYPE_NULL. */
    @ExecuteInIsolatedServiceRequest.OutputParams.OutputType private final int mOutputType;

    /** Optional. Only set when output option is OUTPUT_TYPE_BEST_VALUE. */
    private final int mMaxIntValue;

    public static ExecuteOptionsParcel DEFAULT = new ExecuteOptionsParcel();

    /** @hide */
    public ExecuteOptionsParcel(@NonNull ExecuteInIsolatedServiceRequest.OutputParams options) {
        this(options.getOutputType(), options.getMaxIntValue());
    }

    /**
     * Create a default instance of {@link ExecuteOptionsParcel}.
     *
     * @hide
     */
    private ExecuteOptionsParcel() {
        this(ExecuteInIsolatedServiceRequest.OutputParams.OUTPUT_TYPE_NULL, -1);
    }

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/ExecuteOptionsParcel.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    /**
     * Creates a new ExecuteOptionsParcel.
     *
     * @param outputType Default value is OUTPUT_TYPE_NULL.
     * @param maxIntValue Optional. Only set when output option is OUTPUT_TYPE_BEST_VALUE.
     */
    @DataClass.Generated.Member
    public ExecuteOptionsParcel(
            @ExecuteInIsolatedServiceRequest.OutputParams.OutputType int outputType,
            int maxIntValue) {
        this.mOutputType = outputType;
        AnnotationValidations.validate(
                ExecuteInIsolatedServiceRequest.OutputParams.OutputType.class, null, mOutputType);
        this.mMaxIntValue = maxIntValue;

        // onConstructed(); // You can define this method to get a callback
    }

    /** Default value is OUTPUT_TYPE_NULL. */
    @DataClass.Generated.Member
    public @ExecuteInIsolatedServiceRequest.OutputParams.OutputType int getOutputType() {
        return mOutputType;
    }

    /** Optional. Only set when output option is OUTPUT_TYPE_BEST_VALUE. */
    @DataClass.Generated.Member
    public int getMaxIntValue() {
        return mMaxIntValue;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mOutputType);
        dest.writeInt(mMaxIntValue);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    protected ExecuteOptionsParcel(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int outputType = in.readInt();
        int maxIntValue = in.readInt();

        this.mOutputType = outputType;
        AnnotationValidations.validate(
                ExecuteInIsolatedServiceRequest.OutputParams.OutputType.class, null, mOutputType);
        this.mMaxIntValue = maxIntValue;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<ExecuteOptionsParcel> CREATOR =
            new Parcelable.Creator<ExecuteOptionsParcel>() {
                @Override
                public ExecuteOptionsParcel[] newArray(int size) {
                    return new ExecuteOptionsParcel[size];
                }

                @Override
                public ExecuteOptionsParcel createFromParcel(@NonNull android.os.Parcel in) {
                    return new ExecuteOptionsParcel(in);
                }
            };
    // @formatter:on
    // End of generated code

}
