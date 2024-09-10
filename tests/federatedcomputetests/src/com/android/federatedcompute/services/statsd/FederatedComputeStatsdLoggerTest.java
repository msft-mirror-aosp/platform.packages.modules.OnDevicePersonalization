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

package com.android.federatedcompute.services.statsd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.RateLimiter;

import org.junit.Test;

public class FederatedComputeStatsdLoggerTest {
    @Test
    public void logWithRateLimit_1QPS() throws Exception {
        ExampleIteratorLatency metric =
                new ExampleIteratorLatency.Builder()
                        .setTaskId(123)
                        .setClientVersion(345)
                        .setGetNextLatencyNanos(131000)
                        .build();

        FederatedComputeStatsdLogger logger =
                new FederatedComputeStatsdLogger(RateLimiter.create(1));

        assertTrue(logger.recordExampleIteratorLatencyMetrics(metric));
        assertFalse(logger.recordExampleIteratorLatencyMetrics(metric));
        Thread.sleep(1000L);
        assertTrue(logger.recordExampleIteratorLatencyMetrics(metric));
    }

    @Test
    public void logWithRateLimit_3QPS() throws Exception {
        ExampleIteratorLatency metric =
                new ExampleIteratorLatency.Builder()
                        .setTaskId(123)
                        .setClientVersion(345)
                        .setGetNextLatencyNanos(131000)
                        .build();

        FederatedComputeStatsdLogger logger =
                new FederatedComputeStatsdLogger(RateLimiter.create(3));

        assertTrue(logger.recordExampleIteratorLatencyMetrics(metric));
        assertFalse(logger.recordExampleIteratorLatencyMetrics(metric));
        Thread.sleep(350L);
        assertTrue(logger.recordExampleIteratorLatencyMetrics(metric));
        assertFalse(logger.recordExampleIteratorLatencyMetrics(metric));
    }
}
