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

package com.android.ondevicepersonalization.services.util;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.FlagsFactory;

import java.util.concurrent.ThreadLocalRandom;

/** Util class for adding noise to returned result. */
public class NoiseUtil {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = NoiseUtil.class.getSimpleName();

    /**
     * Add noise to {@link OnDevicePersonalizationManager#executeInIsolatedService} with best value
     * option.
     */
    public int applyNoiseToBestValue(int actualValue, int maxValue, ThreadLocalRandom random) {
        if (actualValue < 0 || actualValue > maxValue) {
            sLogger.e(
                    TAG + ": returned int value %d is not in the range [0, %d].",
                    actualValue,
                    maxValue);
            return -1;
        }
        int noisedValue = actualValue;
        boolean shouldSelectRandomValue =
                random.nextDouble() < FlagsFactory.getFlags().getNoiseForExecuteBestValue();
        if (shouldSelectRandomValue) {
            while (noisedValue == actualValue) {
                noisedValue = random.nextInt(maxValue);
            }
        }
        return noisedValue;
    }
}
