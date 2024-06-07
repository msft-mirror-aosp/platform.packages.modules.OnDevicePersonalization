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

package com.android.ondevicepersonalization.services.reset;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.app.job.JobParameters;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class ResetDataJobServiceTest {
    private ResetDataJobService mSpyService;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .addStaticMockFixtures(TestableDeviceConfig::new)
            .spyStatic(OnDevicePersonalizationExecutors.class)
            .spyStatic(ResetDataTask.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setup() throws Exception {
        mSpyService = spy(new ResetDataJobService());
    }

    @Test
    public void onStartJobTest() {
        doNothing().when(ResetDataTask::deleteMeasurementData);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(MoreExecutors.newDirectExecutorService()).when(
                OnDevicePersonalizationExecutors::getBackgroundExecutor);

        boolean result = mSpyService.onStartJob(mock(JobParameters.class));
        assertTrue(result);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(() -> ResetDataTask.deleteMeasurementData());
    }

    @Test
    public void onStopJobTest() {
        assertTrue(mSpyService.onStopJob(mock(JobParameters.class)));
    }
}
