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

package com.android.federatedcompute.services.statsd;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.EXAMPLE_ITERATOR_NEXT_LATENCY_REPORTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRACE_EVENT_REPORTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED;

import com.android.federatedcompute.services.stats.FederatedComputeStatsLog;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.RateLimiter;

/** Log API stats and client error stats to StatsD. */
public class FederatedComputeStatsdLogger {
    private static volatile FederatedComputeStatsdLogger sFCStatsdLogger = null;
    private final RateLimiter mRateLimiter;

    @VisibleForTesting
    FederatedComputeStatsdLogger(RateLimiter rateLimiter) {
        mRateLimiter = rateLimiter;
    }

    /** Returns an instance of {@link FederatedComputeStatsdLogger}. */
    public static FederatedComputeStatsdLogger getInstance() {
        if (sFCStatsdLogger == null) {
            synchronized (FederatedComputeStatsdLogger.class) {
                if (sFCStatsdLogger == null) {
                    sFCStatsdLogger =
                            new FederatedComputeStatsdLogger(
                                    // Android metrics team recommend the atom logging frequency
                                    // should not exceed once per 10 milliseconds.
                                    RateLimiter.create(100));
                }
            }
        }
        return sFCStatsdLogger;
    }

    /** Log API call stats e.g. response code, API name etc. */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        if (mRateLimiter.tryAcquire()) {
            FederatedComputeStatsLog.write(
                    FEDERATED_COMPUTE_API_CALLED,
                    apiCallStats.getApiClass(),
                    apiCallStats.getApiName(),
                    apiCallStats.getLatencyMillis(),
                    apiCallStats.getResponseCode(),
                    apiCallStats.getSdkPackageName());
        }
    }

    /** Log trace event stats. */
    public void logTraceEventStats(TraceEventStats traceEventStats) {
        if (mRateLimiter.tryAcquire()) {
            FederatedComputeStatsLog.write(
                    FEDERATED_COMPUTE_TRACE_EVENT_REPORTED,
                    traceEventStats.getEventType(),
                    traceEventStats.getStatus(),
                    traceEventStats.getLatencyMillis());
        }
    }

    /**
     * Log FederatedComputeTrainingEventReported to track each stage of federated computation job
     * execution.
     */
    public void logTrainingEventReported(TrainingEventReported trainingEvent) {
        if (mRateLimiter.tryAcquire()) {
            FederatedComputeStatsLog.write(
                    FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED,
                    trainingEvent.getClientVersion(),
                    trainingEvent.getEventKind(),
                    trainingEvent.getTaskId(),
                    trainingEvent.getDurationInMillis(),
                    trainingEvent.getExampleSize(),
                    trainingEvent.getDataTransferDurationMillis(),
                    trainingEvent.getBytesUploaded(),
                    trainingEvent.getBytesDownloaded(),
                    trainingEvent.getKeyAttestationLatencyMillis(),
                    trainingEvent.getExampleStoreBindLatencyNanos(),
                    trainingEvent.getExampleStoreStartQueryLatencyNanos(),
                    trainingEvent.getPopulationId(),
                    trainingEvent.getExampleCount(),
                    trainingEvent.getSdkPackageName());
        }
    }

    /** This method is only used to test if rate limiter is applied when logging to statsd. */
    @VisibleForTesting
    boolean recordExampleIteratorLatencyMetrics(ExampleIteratorLatency iteratorLatency) {
        if (mRateLimiter.tryAcquire()) {
            FederatedComputeStatsLog.write(
                    EXAMPLE_ITERATOR_NEXT_LATENCY_REPORTED,
                    iteratorLatency.getClientVersion(),
                    iteratorLatency.getTaskId(),
                    iteratorLatency.getGetNextLatencyNanos());
            return true;
        }
        return false;
    }

    /**
     * Log ExampleIteratorNextLatencyReported to track the latency of ExampleStoreIterator.next
     * called.
     */
    public void logExampleIteratorNextLatencyReported(ExampleIteratorLatency iteratorLatency) {
        var unused = recordExampleIteratorLatencyMetrics(iteratorLatency);
    }
}
