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

import java.time.Duration;

/** Training interval settings required for federated computation jobs. */
@DataClass(genBuilder = true, genHiddenConstDefs = true, genEqualsHashCode = true)
public final class TrainingInterval {
    /** The scheduling mode for a one-off task. */
    public static final int SCHEDULING_MODE_ONE_TIME = 1;

    /** The scheduling mode for a task that will be rescheduled after each run. */
    public static final int SCHEDULING_MODE_RECURRENT = 2;

    /**
     * The scheduling mode for this task, either {@link #SCHEDULING_MODE_ONE_TIME} or {@link
     * #SCHEDULING_MODE_RECURRENT}. The default scheduling mode is {@link #SCHEDULING_MODE_ONE_TIME}
     * if unspecified.
     */
    @SchedulingMode private int mSchedulingMode = SCHEDULING_MODE_ONE_TIME;

    /**
     * Sets the minimum time interval between two training runs.
     *
     * <p>This field will only be used when the scheduling mode is {@link
     * #SCHEDULING_MODE_RECURRENT}. The value has be greater than zero.
     *
     * <p>Please also note this value is advisory, which does not guarantee the job will be run
     * immediately after the interval expired. Federated compute will still enforce a minimum
     * required interval and training constraints to ensure system health. The current training
     * constraints are device on unmetered network, idle and battery not low.
     */
    @NonNull private Duration mMinimumInterval = Duration.ZERO;

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingInterval.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    /** @hide */
    @android.annotation.IntDef(
            prefix = "SCHEDULING_MODE_",
            value = {SCHEDULING_MODE_ONE_TIME, SCHEDULING_MODE_RECURRENT})
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface SchedulingMode {}

    /** @hide */
    @DataClass.Generated.Member
    public static String schedulingModeToString(@SchedulingMode int value) {
        switch (value) {
            case SCHEDULING_MODE_ONE_TIME:
                return "SCHEDULING_MODE_ONE_TIME";
            case SCHEDULING_MODE_RECURRENT:
                return "SCHEDULING_MODE_RECURRENT";
            default:
                return Integer.toHexString(value);
        }
    }

    @DataClass.Generated.Member
    /* package-private */ TrainingInterval(
            @SchedulingMode int schedulingMode, @NonNull Duration minimumInterval) {
        this.mSchedulingMode = schedulingMode;

        if (!(mSchedulingMode == SCHEDULING_MODE_ONE_TIME)
                && !(mSchedulingMode == SCHEDULING_MODE_RECURRENT)) {
            throw new java.lang.IllegalArgumentException(
                    "schedulingMode was "
                            + mSchedulingMode
                            + " but must be one of: "
                            + "SCHEDULING_MODE_ONE_TIME("
                            + SCHEDULING_MODE_ONE_TIME
                            + "), "
                            + "SCHEDULING_MODE_RECURRENT("
                            + SCHEDULING_MODE_RECURRENT
                            + ")");
        }

        this.mMinimumInterval = minimumInterval;
        AnnotationValidations.validate(NonNull.class, null, mMinimumInterval);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The scheduling mode for this task, either {@link #SCHEDULING_MODE_ONE_TIME} or {@link
     * #SCHEDULING_MODE_RECURRENT}. The default scheduling mode is {@link #SCHEDULING_MODE_ONE_TIME}
     * if unspecified.
     */
    @DataClass.Generated.Member
    public @SchedulingMode int getSchedulingMode() {
        return mSchedulingMode;
    }

    /**
     * Sets the minimum time interval between two training runs.
     *
     * <p>This field will only be used when the scheduling mode is {@link
     * #SCHEDULING_MODE_RECURRENT}. Only positive values are accepted, zero or negative values will
     * result in IllegalArgumentException.
     *
     * <p>Please also note this value is advisory, which does not guarantee the job will be run
     * immediately after the interval expired. Federated compute will still enforce a minimum
     * required interval and training constraints to ensure system health. The current training
     * constraints are device on unmetered network, idle and battery not low.
     */
    @DataClass.Generated.Member
    public @NonNull Duration getMinimumInterval() {
        return mMinimumInterval;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(TrainingInterval other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        TrainingInterval that = (TrainingInterval) o;
        //noinspection PointlessBooleanExpression
        return true
                && mSchedulingMode == that.mSchedulingMode
                && java.util.Objects.equals(mMinimumInterval, that.mMinimumInterval);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mSchedulingMode;
        _hash = 31 * _hash + java.util.Objects.hashCode(mMinimumInterval);
        return _hash;
    }

    /** A builder for {@link TrainingInterval} */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @SchedulingMode int mSchedulingMode;
        private @NonNull Duration mMinimumInterval;

        private long mBuilderFieldsSet = 0L;

        public Builder() {}

        /**
         * The scheduling mode for this task, either {@link #SCHEDULING_MODE_ONE_TIME} or {@link
         * #SCHEDULING_MODE_RECURRENT}. The default scheduling mode is {@link
         * #SCHEDULING_MODE_ONE_TIME} if unspecified.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setSchedulingMode(@SchedulingMode int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mSchedulingMode = value;
            return this;
        }

        /**
         * Sets the minimum time interval between two training runs.
         *
         * <p>This field will only be used when the scheduling mode is {@link
         * #SCHEDULING_MODE_RECURRENT}. Only positive values are accepted, zero or negative values
         * will result in IllegalArgumentException.
         *
         * <p>Please also note this value is advisory, which does not guarantee the job will be run
         * immediately after the interval expired. Federated compute will still enforce a minimum
         * required interval and training constraints to ensure system health. The current training
         * constraints are device on unmetered network, idle and battery not low.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setMinimumInterval(@NonNull Duration value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mMinimumInterval = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull TrainingInterval build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mSchedulingMode = SCHEDULING_MODE_ONE_TIME;
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mMinimumInterval = Duration.ZERO;
            }
            TrainingInterval o = new TrainingInterval(mSchedulingMode, mMinimumInterval);
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
            time = 1697653739724L,
            codegenVersion = "1.0.23",
            sourceFile =
                    "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/TrainingInterval.java",
            inputSignatures =
                    "public static final  int SCHEDULING_MODE_ONE_TIME\npublic static final  int SCHEDULING_MODE_RECURRENT\nprivate @android.adservices.ondevicepersonalization.TrainingInterval.SchedulingMode int mSchedulingMode\nprivate @android.annotation.NonNull java.time.Duration mMinimumInterval\nclass TrainingInterval extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genHiddenConstDefs=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}

    // @formatter:on
    // End of generated code

}
