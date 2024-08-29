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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.odp.module.common.Clock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.TimeZone;

@RunWith(JUnit4.class)
public class DateTimeUtilsTest {
    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    // PST: Friday, August 23, 2024 10:59:11 PM
    private static final long DEFAULT_CURRENT_TIME_MILLIS = 1724479151000L;
    private static final int CURRENT_DAYS_EPOCH_PST = 19958;
    private Context mContext;

    @Mock private Clock mMockClock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();

        TimeZone pstTime = TimeZone.getTimeZone("GMT-08:00");
        TimeZone.setDefault(pstTime);
        when(mMockClock.currentTimeMillis()).thenReturn(DEFAULT_CURRENT_TIME_MILLIS);
    }

    @Test
    public void testDayIndexUtc() {
        // UTC day is into the next day, Aug 24th 2024.
        int dayEpoch = DateTimeUtils.dayIndexUtc(mMockClock);

        assertEquals(CURRENT_DAYS_EPOCH_PST + 1, dayEpoch);
    }

    @Test
    public void testDayIndexLocal() {
        int dayEpoch = DateTimeUtils.dayIndexLocal(mMockClock);

        assertEquals(CURRENT_DAYS_EPOCH_PST, dayEpoch);
    }
}
