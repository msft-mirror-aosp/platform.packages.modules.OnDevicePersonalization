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

package com.android.odp.module.common.data;

import android.content.Context;

import androidx.datastore.guava.GuavaDataStore;

import com.android.adservices.shared.datastore.ProtoSerializer;
import com.android.odp.module.common.proto.ErrorReportingMetadata;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.Timestamp;

import java.util.concurrent.Executor;

public class ErrorReportingMetadataProtoDataStore implements ErrorReportingMetadataStore {

    @VisibleForTesting static final String FILE_NAME = "error_reporting_metadata.binarypb";

    private static volatile ErrorReportingMetadataStore sInstance = null;

    private final GuavaDataStore<ErrorReportingMetadata> mErrorReportingMetadataStore;

    @VisibleForTesting
    ErrorReportingMetadataProtoDataStore(
            Context context, Executor backgroundExecutor, String fileName) {
        mErrorReportingMetadataStore =
                new GuavaDataStore.Builder(
                                context,
                                fileName,
                                new ProtoSerializer<ErrorReportingMetadata>(
                                        ErrorReportingMetadata.getDefaultInstance(),
                                        ExtensionRegistryLite.getEmptyRegistry()))
                        .setExecutor(backgroundExecutor)
                        .build();
    }

    /**
     * @return The instance of {@link ErrorReportingMetadataStore}.
     */
    public static ErrorReportingMetadataStore getInstance(
            Context context, Executor backgroundExecutor) {
        if (sInstance == null) {
            synchronized (ErrorReportingMetadataProtoDataStore.class) {
                if (sInstance == null) {
                    sInstance =
                            new ErrorReportingMetadataProtoDataStore(
                                    context, backgroundExecutor, FILE_NAME);
                }
            }
        }
        return sInstance;
    }

    /**
     * Set the error reporting metadata.
     *
     * @param metadata The metadata to persist.
     * @return A {@link ListenableFuture} that resolves when the set operation succeeds.
     */
    @Override
    public ListenableFuture<ErrorReportingMetadata> set(ErrorReportingMetadata metadata) {
        return mErrorReportingMetadataStore.updateDataAsync(currentDevSession -> metadata);
    }

    /**
     * Get the dev session state.
     *
     * @return A future when the operation is complete, containing the current state.
     */
    @Override
    public ListenableFuture<ErrorReportingMetadata> get() {
        return mErrorReportingMetadataStore.getDataAsync();
    }

    /** Returns whether the provided {@link ErrorReportingMetadata} is unset/uninitialized. */
    public static boolean isErrorReportingMetadataUninitialized(ErrorReportingMetadata metadata) {
        return ErrorReportingMetadata.getDefaultInstance().equals(metadata);
    }

    /**
     * Returns a {@link ErrorReportingMetadata} object created from the provided current time-stamp.
     *
     * @param currentEpochTime the seconds since epoch in UTC
     * @return corresponding {@link ErrorReportingMetadata}.
     */
    public static ErrorReportingMetadata getMetadata(long currentEpochTime) {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(currentEpochTime).build();
        return ErrorReportingMetadata.newBuilder().setLastSuccessfulUpload(timestamp).build();
    }
}
