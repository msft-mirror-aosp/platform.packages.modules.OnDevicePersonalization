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

package com.android.ondevicepersonalization.services.data.errors;

import android.annotation.Nullable;

import com.android.odp.module.common.encryption.OdpEncryptionKey;

import com.google.common.util.concurrent.ListenableFuture;

interface ReportingProtocol {
    /**
     * Report the exception data for this vendor based on error data and URL provided during
     * construction.
     *
     * @return a {@link ListenableFuture} that resolves with true/false when reporting is
     *     successful/failed.
     */
    ListenableFuture<Boolean> reportExceptionData(@Nullable OdpEncryptionKey encryptionKey);
}
