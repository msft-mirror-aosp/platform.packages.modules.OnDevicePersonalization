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
import android.content.ComponentName;
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
    /**
     * The task name to be provided to the federated compute server during checkin. The field is
     * required and should not be empty.
     */
    @NonNull private String mPopulationName = "";

    /**
     * The remote federated compute server address that federated compute client need to checkin.
     * The field is required and should not be empty.
     */
    @NonNull private String mServerAddress = "";

    /**
     * Indicated the component of the application requesting federated learning. The field is
     * required and should not be empty.
     */
    @Nullable private ComponentName mOwnerComponentName = null;

    /** Indicated the certificate digest of the application requesting federated learning. */
    @NonNull private String mOwnerIdentifierCertDigest = "";

    @Nullable private TrainingInterval mTrainingInterval = null;

    /**
     * The context data that federatedcompute will pass back to client when bind to
     * ExampleStoreService and ResultHandlingService.
     */
    @Nullable private final byte[] mContextData;

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen
    // $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/federatedcompute/common/TrainingOptions.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    // @formatter:off

    @DataClass.Generated.Member
    /* package-private */ TrainingOptions(
            @NonNull String populationName,
            @NonNull String serverAddress,
            @Nullable ComponentName ownerComponentName,
            @NonNull String ownerIdentifierCertDigest,
            @Nullable TrainingInterval trainingInterval,
            @Nullable byte[] contextData) {
        this.mPopulationName = populationName;
        AnnotationValidations.validate(NonNull.class, null, mPopulationName);
        this.mServerAddress = serverAddress;
        AnnotationValidations.validate(NonNull.class, null, mServerAddress);
        this.mOwnerComponentName = ownerComponentName;
        this.mOwnerIdentifierCertDigest = ownerIdentifierCertDigest;
        AnnotationValidations.validate(NonNull.class, null, mOwnerIdentifierCertDigest);
        this.mTrainingInterval = trainingInterval;
        this.mContextData = contextData;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The task name to be provided to the federated compute server during checkin. The field is
     * required and should not be empty.
     */
    @DataClass.Generated.Member
    public @NonNull String getPopulationName() {
        return mPopulationName;
    }

    /**
     * The remote federated compute server address that federated compute client need to checkin.
     * The field is required and should not be empty.
     */
    @DataClass.Generated.Member
    public @NonNull String getServerAddress() {
        return mServerAddress;
    }

    /**
     * Indicated the component of the application requesting federated learning. The field is
     * required and should not be empty.
     */
    @DataClass.Generated.Member
    public @Nullable ComponentName getOwnerComponentName() {
        return mOwnerComponentName;
    }

    /** Indicated the certificate digest of the application requesting federated learning. */
    @DataClass.Generated.Member
    public @NonNull String getOwnerIdentifierCertDigest() {
        return mOwnerIdentifierCertDigest;
    }

    @DataClass.Generated.Member
    public @Nullable TrainingInterval getTrainingInterval() {
        return mTrainingInterval;
    }

    /**
     * The context data that federatedcompute will pass back to client when bind to
     * ExampleStoreService and ResultHandlingService.
     */
    @DataClass.Generated.Member
    public @Nullable byte[] getContextData() {
        return mContextData;
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
                && java.util.Objects.equals(mPopulationName, that.mPopulationName)
                && java.util.Objects.equals(mServerAddress, that.mServerAddress)
                && java.util.Objects.equals(mOwnerComponentName, that.mOwnerComponentName)
                && java.util.Objects.equals(
                        mOwnerIdentifierCertDigest, that.mOwnerIdentifierCertDigest)
                && java.util.Objects.equals(mTrainingInterval, that.mTrainingInterval)
                && java.util.Arrays.equals(mContextData, that.mContextData);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mPopulationName);
        _hash = 31 * _hash + java.util.Objects.hashCode(mServerAddress);
        _hash = 31 * _hash + java.util.Objects.hashCode(mOwnerComponentName);
        _hash = 31 * _hash + java.util.Objects.hashCode(mOwnerIdentifierCertDigest);
        _hash = 31 * _hash + java.util.Objects.hashCode(mTrainingInterval);
        _hash = 31 * _hash + java.util.Arrays.hashCode(mContextData);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mOwnerComponentName != null) flg |= 0x4;
        if (mTrainingInterval != null) flg |= 0x10;
        dest.writeByte(flg);
        dest.writeString(mPopulationName);
        dest.writeString(mServerAddress);
        if (mOwnerComponentName != null) dest.writeTypedObject(mOwnerComponentName, flags);
        dest.writeString(mOwnerIdentifierCertDigest);
        if (mTrainingInterval != null) dest.writeTypedObject(mTrainingInterval, flags);
        dest.writeByteArray(mContextData);
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
        String populationName = in.readString();
        String serverAddress = in.readString();
        ComponentName ownerComponentName =
                (flg & 0x4) == 0 ? null : (ComponentName) in.readTypedObject(ComponentName.CREATOR);
        String ownerIdentifierCertDigest = in.readString();
        TrainingInterval trainingInterval =
                (flg & 0x10) == 0
                        ? null
                        : (TrainingInterval) in.readTypedObject(TrainingInterval.CREATOR);
        byte[] contextData = in.createByteArray();

        this.mPopulationName = populationName;
        AnnotationValidations.validate(NonNull.class, null, mPopulationName);
        this.mServerAddress = serverAddress;
        AnnotationValidations.validate(NonNull.class, null, mServerAddress);
        this.mOwnerComponentName = ownerComponentName;
        this.mOwnerIdentifierCertDigest = ownerIdentifierCertDigest;
        AnnotationValidations.validate(NonNull.class, null, mOwnerIdentifierCertDigest);
        this.mTrainingInterval = trainingInterval;
        this.mContextData = contextData;

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

        private @NonNull String mPopulationName;
        private @NonNull String mServerAddress;
        private @Nullable ComponentName mOwnerComponentName;
        private @NonNull String mOwnerIdentifierCertDigest;
        private @Nullable TrainingInterval mTrainingInterval;
        private @Nullable byte[] mContextData;

        private long mBuilderFieldsSet = 0L;

        public Builder() {}

        /**
         * The task name to be provided to the federated compute server during checkin. The field is
         * required and should not be empty.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setPopulationName(@NonNull String value) {
            checkNotUsed();
            Preconditions.checkStringNotEmpty(value);
            mBuilderFieldsSet |= 0x1;
            mPopulationName = value;
            return this;
        }

        /**
         * The remote federated compute server address that federated compute client need to
         * checkin. The field is required and should not be empty.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setServerAddress(@NonNull String value) {
            checkNotUsed();
            Preconditions.checkStringNotEmpty(value);
            mBuilderFieldsSet |= 0x2;
            mServerAddress = value;
            return this;
        }

        /**
         * Indicated the component of the application requesting federated learning. The field is
         * required and should not be empty.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setOwnerComponentName(@NonNull ComponentName value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mOwnerComponentName = value;
            return this;
        }

        /** Indicated the certificate digest of the application requesting federated learning. */
        @DataClass.Generated.Member
        public @NonNull Builder setOwnerIdentifierCertDigest(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mOwnerIdentifierCertDigest = value;
            return this;
        }

        @DataClass.Generated.Member
        public @NonNull Builder setTrainingInterval(@NonNull TrainingInterval value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mTrainingInterval = value;
            return this;
        }

        /**
         * The context data that federatedcompute will pass back to client when bind to
         * ExampleStoreService and ResultHandlingService.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setContextData(@NonNull byte... value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x20;
            mContextData = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull TrainingOptions build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x40; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mPopulationName = "";
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mServerAddress = "";
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mOwnerComponentName = null;
            }
            if ((mBuilderFieldsSet & 0x8) == 0) {
                mOwnerIdentifierCertDigest = "";
            }
            if ((mBuilderFieldsSet & 0x10) == 0) {
                mTrainingInterval = null;
            }
            TrainingOptions o =
                    new TrainingOptions(
                            mPopulationName,
                            mServerAddress,
                            mOwnerComponentName,
                            mOwnerIdentifierCertDigest,
                            mTrainingInterval,
                            mContextData);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x40) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1704928887201L,
            codegenVersion = "1.0.23",
            sourceFile =
                    "packages/modules/OnDevicePersonalization/framework/java/android/federatedcompute/common/TrainingOptions.java",
            inputSignatures =
                    "private @android.annotation.NonNull java.lang.String mPopulationName\nprivate @android.annotation.NonNull java.lang.String mServerAddress\nprivate @android.annotation.Nullable android.content.ComponentName mOwnerComponentName\nprivate @android.annotation.NonNull java.lang.String mOwnerIdentifierCertDigest\nprivate @android.annotation.Nullable android.federatedcompute.common.TrainingInterval mTrainingInterval\nprivate final @android.annotation.Nullable byte[] mContextData\nclass TrainingOptions extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}

    // @formatter:on
    // End of generated code

}
