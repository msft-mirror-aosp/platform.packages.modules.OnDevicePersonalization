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

import com.android.odp.module.common.proto.ErrorReportingMetadata;

import com.google.common.util.concurrent.ListenableFuture;

/** Provides ability to get or set the {@link ErrorReportingMetadata} */
public interface ErrorReportingMetadataStore {

    /**
     * Set the error reporting metadata.
     *
     * @param metadata The metadata to persist.
     * @return A {@link ListenableFuture} that resolves when the set operation succeeds, the future
     *     resolves with the persisted {@link ErrorReportingMetadata}
     */
    ListenableFuture<ErrorReportingMetadata> set(ErrorReportingMetadata metadata);

    /**
     * Get the error reporting metadata.
     *
     * @return A {@link ListenableFuture} that resolves with an instance of {@link
     *     ErrorReportingMetadata} that has been persisted.
     */
    ListenableFuture<ErrorReportingMetadata> get();
}
