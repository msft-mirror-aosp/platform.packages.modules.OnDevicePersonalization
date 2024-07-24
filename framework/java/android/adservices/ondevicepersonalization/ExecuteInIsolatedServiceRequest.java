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
import android.annotation.Nullable;
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
    @NonNull private PersistableBundle mParams;

    @Nullable private Options mOptions;

    public static class Options {
        /**
         * By default, the output type is null. In this case, ODP provides accurate user data for
         * IsolatedWorker.
         */
        public static final int OUTPUT_TYPE_NULL = 0;

        /**
         * If set, {@link ExecuteInIsolatedServiceResponse#getBestValue()} will return an integer
         * that indicates the index of best values passed in {@link
         * ExecuteInIsolatedServiceRequest#getParams()}.
         */
        public static final int OUTPUT_TYPE_BEST_VALUE = 1;

        @IntDef(
                prefix = "OUTPUT_TYPE_",
                value = {OUTPUT_TYPE_NULL, OUTPUT_TYPE_BEST_VALUE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface OutputType {}

        /** Default value is OUTPUT_TYPE_INT. */
        @OutputType private int mOutputType = OUTPUT_TYPE_NULL;

        /** Optional. Only set when output option is OUTPUT_TYPE_INT. */
        private int mMaxIntValue = -1;

        private Options(int outputType, int maxIntValue) {
            mMaxIntValue = maxIntValue;
            mOutputType = outputType;
        }

        /**
         * Create the options to get best value out of maximum int values. If set this, caller can
         * call {@link ExecuteInIsolatedServiceResponse#getBestValue} to get result.
         *
         * @param maxIntValue the maximum value {@link IsolatedWorker} can return to caller app.
         * @hide
         */
        public static Options buildBestValueOption(int maxIntValue) {
            return new Options(OUTPUT_TYPE_BEST_VALUE, maxIntValue);
        }

        /** @hide */
        public int getOutputType() {
            return mOutputType;
        }

        /** @hide */
        public int getMaxIntValue() {
            return mMaxIntValue;
        }
    }

    ExecuteInIsolatedServiceRequest(
            @NonNull ComponentName service,
            @NonNull PersistableBundle params,
            @Nullable Options options) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(params);
        this.mService = service;
        this.mParams = params;
        this.mOptions = options;
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
    public @NonNull PersistableBundle getParams() {
        return mParams;
    }

    public @Nullable Options getOptions() {
        return mOptions;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(ExecuteIsolatedServiceRequest other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        ExecuteInIsolatedServiceRequest that = (ExecuteInIsolatedServiceRequest) o;
        //noinspection PointlessBooleanExpression
        return java.util.Objects.equals(mService, that.mService)
                && java.util.Objects.equals(mParams, that.mParams)
                && java.util.Objects.equals(mOptions, that.mOptions);
    }

    @Override
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }
        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mService);
        _hash = 31 * _hash + java.util.Objects.hashCode(mParams);
        _hash = 31 * _hash + java.util.Objects.hashCode(mOptions);
        return _hash;
    }

    /** A builder for {@link ExecuteInIsolatedServiceRequest} */
    public static class Builder {
        private @NonNull ComponentName mService;
        private @NonNull PersistableBundle mParams;
        private @Nullable Options mOptions;

        public Builder() {}

        /** The {@link ComponentName} of the {@link IsolatedService}. */
        public @NonNull Builder setService(@NonNull ComponentName value) {
            mService = value;
            return this;
        }

        /**
         * The {@link PersistableBundle} that is passed from the calling app to the {@link
         * IsolatedService}. The expected contents of this parameter are defined by the{@link
         * IsolatedService}. The platform does not interpret this parameter.
         */
        public @NonNull Builder setParams(@NonNull PersistableBundle value) {
            mParams = value;
            return this;
        }

        /**
         * Optional. If set {@link Options#buildBestValueOption}, {@link
         * ExecuteInIsolatedServiceResponse#getBestValue} will return non-negative value. Otherwise,
         * {@link ExecuteInIsolatedServiceResponse#getBestValue} will return -1.
         */
        public @Nullable Builder setOptions(@Nullable Options value) {
            mOptions = value;
            return this;
        }

        /** Builds the instance. */
        public @NonNull ExecuteInIsolatedServiceRequest build() {
            return new ExecuteInIsolatedServiceRequest(mService, mParams, mOptions);
        }
    }
}
