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

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Utilities for date/time transformations. */
final class DateTimeUtils {
    private static final String TAG = DateTimeUtils.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    /**
     * Get the day index in the UTC timezone.
     *
     * <p>Returns {@code -1} if unsuccessful.
     */
    public static int dayIndexUtc() {
        return dayIndexUtc(MonotonicClock.getInstance());
    }

    @VisibleForTesting
    static int dayIndexUtc(Clock clock) {
        // Package-private method for easier testing, allows injecting a clock in tests.
        Instant currentInstant = getCurrentInstant(clock);
        try {
            return (int) currentInstant.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
        } catch (DateTimeException e) {
            sLogger.e(TAG + " : failed to get day index.", e);
            return -1;
        }
    }

    /**
     * Get the day index in the local device's timezone.
     *
     * <p>Returns {@code -1} if unsuccessful.
     */
    public static int dayIndexLocal() {
        return dayIndexLocal(MonotonicClock.getInstance());
    }

    @VisibleForTesting
    static int dayIndexLocal(Clock clock) {
        // Package-private method for easier testing, allows injecting a clock in tests.
        Instant currentInstant = getCurrentInstant(clock);
        try {
            return (int) currentInstant.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        } catch (DateTimeException e) {
            sLogger.e(TAG + " : failed to get day index.", e);
            return -1;
        }
    }

    private static Instant getCurrentInstant(Clock clock) {
        long currentSystemTime = clock.currentTimeMillis();
        sLogger.i(TAG + ": current system time = " + currentSystemTime);
        return Instant.ofEpochMilli(currentSystemTime);
    }

    private DateTimeUtils() {}
}
