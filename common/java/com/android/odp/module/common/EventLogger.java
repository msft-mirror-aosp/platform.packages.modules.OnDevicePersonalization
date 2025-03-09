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

package com.android.odp.module.common;



/** The helper interface to log events in statsd. */
public interface EventLogger {

    /** Logs encryption keys fetch fail training event kind. */
    void logEncryptionKeyFetchFailEventKind();

    /** Logs encryption keys fetch start training event kind. */
    void logEncryptionKeyFetchStartEventKind();

    /** Logs encryption keys fetch timeout training event kind. */
    void logEncryptionKeyFetchTimeoutEventKind();

    /** Logs encryption keys fetch empty URI training event kind. */
    void logEncryptionKeyFetchEmptyUriEventKind();

    /** Logs encryption keys fetch failed to create request training event kind. */
    void logEncryptionKeyFetchRequestFailEventKind();

    /** Logs encryption keys fetch failed to parse response training event kind. */
    void logEncryptionKeyFetchInvalidPayloadEventKind();
}
