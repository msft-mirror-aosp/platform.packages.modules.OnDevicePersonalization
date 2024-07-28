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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ThreadLocalRandom;

public class NoiseUtilTest {
    @Mock ThreadLocalRandom mMockRandom;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void applyNoise_ToBestValue_returnActualValue() {
        when(mMockRandom.nextDouble()).thenReturn(0.2);
        int output = NoiseUtil.applyNoiseToBestValue(5, 10, mMockRandom);
        assertThat(output).isEqualTo(5);
    }

    @Test
    public void applyNoise_ToBestValue_returnFakeValue() {
        when(mMockRandom.nextDouble()).thenReturn(0.02);
        when(mMockRandom.nextInt(anyInt())).thenReturn(6);
        int output = NoiseUtil.applyNoiseToBestValue(5, 10, mMockRandom);
        assertThat(output).isEqualTo(6);
    }

    @Test
    public void invalidActualValue() {
        int output = NoiseUtil.applyNoiseToBestValue(11, 10, mMockRandom);
        assertThat(output).isEqualTo(-1);
    }

    @Test
    public void invalidNegativeValue() {
        int output = NoiseUtil.applyNoiseToBestValue(-2, 10, mMockRandom);
        assertThat(output).isEqualTo(-1);
    }

    @Test
    public void applyNoise_ToBestValue_returnNotActualFakeValue() {
        when(mMockRandom.nextDouble()).thenReturn(0.02);
        when(mMockRandom.nextInt(anyInt())).thenReturn(5, 7);
        int output = NoiseUtil.applyNoiseToBestValue(5, 10, mMockRandom);
        assertThat(output).isEqualTo(7);
    }
}
