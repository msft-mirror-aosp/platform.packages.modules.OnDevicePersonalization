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

package com.android.federatedcompute.services.encryption;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRACE_EVENT_REPORTED;

import com.android.federatedcompute.services.statsd.FederatedComputeStatsdLogger;
import com.android.federatedcompute.services.statsd.TraceEventStats;
import com.android.odp.module.common.EventLogger;

/** The helper function to log {@link TraceEventStats} in statsd. */
public class BackgroundKeyFetchJobEventLogger implements EventLogger {
    public static int ENCRYPTION_KEY_FETCH_START_EVENT = 1;
    public static int ENCRYPTION_KEY_FETCH_FAIL_EVENT = 2;
    public static int ENCRYPTION_KEY_FETCH_TIMEOUT_EVENT = 3;
    public static int ENCRYPTION_KEY_FETCH_EMPTY_URI_EVENT = 4;
    public static int ENCRYPTION_KEY_FETCH_REQUEST_FAIL_EVENT = 5;
    public static int ENCRYPTION_KEY_FETCH_INVALID_PAYLOAD_EVENT = 6;
    @Override
    public void logEncryptionKeyFetchFailEventKind() {
        TraceEventStats traceEventStats = new TraceEventStats.Builder().setEventType(
                FEDERATED_COMPUTE_TRACE_EVENT_REPORTED).setStatus(
                ENCRYPTION_KEY_FETCH_FAIL_EVENT).build();
        FederatedComputeStatsdLogger.getInstance().logTraceEventStats(traceEventStats);
    }

    @Override
    public void logEncryptionKeyFetchStartEventKind() {
        TraceEventStats traceEventStats = new TraceEventStats.Builder().setEventType(
                FEDERATED_COMPUTE_TRACE_EVENT_REPORTED).setStatus(
                ENCRYPTION_KEY_FETCH_START_EVENT).build();
        FederatedComputeStatsdLogger.getInstance().logTraceEventStats(traceEventStats);
    }

    @Override
    public void logEncryptionKeyFetchTimeoutEventKind() {
        TraceEventStats traceEventStats = new TraceEventStats.Builder().setEventType(
                FEDERATED_COMPUTE_TRACE_EVENT_REPORTED).setStatus(
                ENCRYPTION_KEY_FETCH_TIMEOUT_EVENT).build();
        FederatedComputeStatsdLogger.getInstance().logTraceEventStats(traceEventStats);
    }

    @Override
    public void logEncryptionKeyFetchEmptyUriEventKind() {
        TraceEventStats traceEventStats = new TraceEventStats.Builder().setEventType(
                FEDERATED_COMPUTE_TRACE_EVENT_REPORTED).setStatus(
                ENCRYPTION_KEY_FETCH_EMPTY_URI_EVENT).build();
        FederatedComputeStatsdLogger.getInstance().logTraceEventStats(traceEventStats);
    }

    @Override
    public void logEncryptionKeyFetchRequestFailEventKind() {
        TraceEventStats traceEventStats = new TraceEventStats.Builder().setEventType(
                FEDERATED_COMPUTE_TRACE_EVENT_REPORTED).setStatus(
                ENCRYPTION_KEY_FETCH_REQUEST_FAIL_EVENT).build();
        FederatedComputeStatsdLogger.getInstance().logTraceEventStats(traceEventStats);
    }

    @Override
    public void logEncryptionKeyFetchInvalidPayloadEventKind() {
        TraceEventStats traceEventStats = new TraceEventStats.Builder().setEventType(
                FEDERATED_COMPUTE_TRACE_EVENT_REPORTED).setStatus(
                ENCRYPTION_KEY_FETCH_INVALID_PAYLOAD_EVENT).build();
        FederatedComputeStatsdLogger.getInstance().logTraceEventStats(traceEventStats);
    }
}
