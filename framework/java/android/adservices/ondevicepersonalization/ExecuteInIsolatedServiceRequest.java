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
import android.annotation.NonNull;
import android.content.ComponentName;
import android.os.PersistableBundle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * The request of {@link OnDevicePersonalizationManager#executeInIsolatedService}.
 *
 * @hide
 */
public class ExecuteInIsolatedServiceRequest {
    /** The {@link ComponentName} of the {@link IsolatedService}. */
    @NonNull private ComponentName mService;

    /**
     * a {@link PersistableBundle} that is passed from the calling app to the {@link
     * IsolatedService}. The expected contents of this parameter are defined by the{@link
     * IsolatedService}. The platform does not interpret this parameter.
     */
    @NonNull private PersistableBundle mAppParams;

    /**
     * The set of parameters to indicate output of {@link IsolatedService}. It's mainly used by
     * platform. For example, platform calls {@link OutputParams#getOutputType} and validates the
     * result received from {@link IsolatedService}.
     */
    @NonNull private OutputParams mOutputParams;

    public static class OutputParams {
        /**
         * By default, the output type is null. In this case, ODP provides accurate user data for
         * IsolatedWorker.
         */
        public static final int OUTPUT_TYPE_NULL = 0;

        /**
         * If set, {@link ExecuteInIsolatedServiceResponse#getBestValue()} will return an integer
         * that indicates the index of best values passed in {@link
         * ExecuteInIsolatedServiceRequest#getAppParams}.
         */
        public static final int OUTPUT_TYPE_BEST_VALUE = 1;

        /** @hide */
        @IntDef(
                prefix = "OUTPUT_TYPE_",
                value = {OUTPUT_TYPE_NULL, OUTPUT_TYPE_BEST_VALUE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface OutputType {}

        /** Default value is OUTPUT_TYPE_NULL. */
        @OutputType private final int mOutputType;

        /** Optional. Only set when output option is OUTPUT_TYPE_BEST_VALUE. */
        private final int mMaxIntValue;

        @NonNull public static final OutputParams DEFAULT = new OutputParams(OUTPUT_TYPE_NULL, -1);

        private OutputParams(int outputType, int maxIntValue) {
            mMaxIntValue = maxIntValue;
            mOutputType = outputType;
        }

        /**
         * Create the output params to get best value out of maximum int values. If set this, caller
         * can call {@link ExecuteInIsolatedServiceResponse#getBestValue} to get result.
         *
         * @param maxIntValue the maximum value {@link IsolatedWorker} can return to caller app.
         */
        public @NonNull static OutputParams buildBestValueParams(int maxIntValue) {
            return new OutputParams(OUTPUT_TYPE_BEST_VALUE, maxIntValue);
        }

        /**
         * Returns the output type of {@link IsolatedService}. The default value is {@link
         * OutputParams#OUTPUT_TYPE_NULL}.
         */
        public int getOutputType() {
            return mOutputType;
        }

        /**
         * Returns the value set in {@link OutputParams#buildBestValueParams}. The default value is
         * -1.
         */
        public int getMaxIntValue() {
            return mMaxIntValue;
        }
    }

    /* package-private */ ExecuteInIsolatedServiceRequest(
            @NonNull ComponentName service,
            @NonNull PersistableBundle appParams,
            @NonNull OutputParams outputParams) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(appParams);
        Objects.requireNonNull(outputParams);
        this.mService = service;
        this.mAppParams = appParams;
        this.mOutputParams = outputParams;
    }

    /** The {@link ComponentName} of the {@link IsolatedService}. */
    public @NonNull ComponentName getService() {
        return mService;
    }

    /**
     * a {@link PersistableBundle} that is passed from the calling app to the {@link
     * IsolatedService}. The expected contents of this parameter are defined by the{@link
     * IsolatedService}. The platform does not interpret this parameter.
     */
    public @NonNull PersistableBundle getAppParams() {
        return mAppParams;
    }

    /**
     * The set of parameters to indicate output of {@link IsolatedService}. It's mainly used by
     * platform. For example, platform calls {@link OutputParams#getOutputType} and validates the
     * result received from {@link IsolatedService}.
     */
    public @NonNull OutputParams getOutputParams() {
        return mOutputParams;
    }

    @Override
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(ExecuteInIsolatedServiceRequest other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        ExecuteInIsolatedServiceRequest that = (ExecuteInIsolatedServiceRequest) o;
        //noinspection PointlessBooleanExpression
        return java.util.Objects.equals(mService, that.mService)
                && java.util.Objects.equals(mAppParams, that.mAppParams)
                && java.util.Objects.equals(mOutputParams, that.mOutputParams);
    }

    @Override
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mService);
        _hash = 31 * _hash + java.util.Objects.hashCode(mAppParams);
        _hash = 31 * _hash + java.util.Objects.hashCode(mOutputParams);
        return _hash;
    }

    /** A builder for {@link ExecuteInIsolatedServiceRequest} */
    @SuppressWarnings("WeakerAccess")
    public static class Builder {

        private @NonNull ComponentName mService;
        private @NonNull PersistableBundle mAppParams = PersistableBundle.EMPTY;
        private @NonNull OutputParams mOutputParams = OutputParams.DEFAULT;

        /**
         * Creates a new Builder.
         *
         * @param service The {@link ComponentName} of the {@link IsolatedService}.
         */
        public Builder(@NonNull ComponentName service) {
            Objects.requireNonNull(service);
            mService = service;
        }

        /**
         * a {@link PersistableBundle} that is passed from the calling app to the {@link
         * IsolatedService}. The expected contents of this parameter are defined by the{@link
         * IsolatedService}. The platform does not interpret this parameter.
         */
        public @NonNull Builder setAppParams(@NonNull PersistableBundle value) {
            mAppParams = value;
            return this;
        }

        /**
         * The set of parameters to indicate output of {@link IsolatedService}. It's mainly used by
         * platform. For example, platform calls {@link OutputParams#getOutputType} and validates
         * the result received from {@link IsolatedService}.
         */
        public @NonNull Builder setOutputParams(@NonNull OutputParams value) {
            mOutputParams = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull ExecuteInIsolatedServiceRequest build() {
            ExecuteInIsolatedServiceRequest o =
                    new ExecuteInIsolatedServiceRequest(mService, mAppParams, mOutputParams);
            return o;
        }
    }
}
