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

package com.android.federatedcompute.services.common;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** The collections of examples usage statistics. */
public class ExampleStats {
    public final AtomicInteger mExampleCount = new AtomicInteger(0);
    public final AtomicLong mExampleSizeBytes = new AtomicLong(0);

    public final AtomicLong mStartQueryLatencyNanos = new AtomicLong(0);

    public final AtomicLong mBindToExampleStoreLatencyNanos = new AtomicLong(0);
}
