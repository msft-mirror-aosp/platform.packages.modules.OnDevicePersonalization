/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.download;

import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationDownloadProcessingJobServiceTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    static class TestFlags implements Flags {
        boolean mGlobalKillSwitch = false;
        boolean mSpeOnOdpDownloadProcessingJobEnabled = false;
        @Override public boolean getGlobalKillSwitch() {
            return mGlobalKillSwitch;
        }

        @Override public boolean getSpeOnOdpDownloadProcessingJobEnabled() {
            return mSpeOnOdpDownloadProcessingJobEnabled;
        }
    }

    private TestFlags mSpyFlags = new TestFlags();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(FlagsFactory.class)
            .spyStatic(OnDevicePersonalizationExecutors.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    private OnDevicePersonalizationDownloadProcessingJobService mSpyService;

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        doReturn(mSpyFlags).when(FlagsFactory::getFlags);
        mSpyFlags.mGlobalKillSwitch = false;
        mSpyFlags.mSpeOnOdpDownloadProcessingJobEnabled = false;
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        MobileDataDownloadFactory.getMdd(mContext, executorService, executorService);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancel(OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID);

        mSpyService = spy(new OnDevicePersonalizationDownloadProcessingJobService());
    }

    @Test
    public void testDefaultNoArgConstructor() {
        OnDevicePersonalizationDownloadProcessingJobService instance =
                new OnDevicePersonalizationDownloadProcessingJobService();
        assertNotNull("default no-arg constructor is required by JobService", instance);
    }

    @Test
    public void onStartJobTest() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                (v) -> {
                    latch.countDown();
                    return null;
                })
                .when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mContext.getPackageManager()).when(mSpyService).getPackageManager();
        doReturn(MoreExecutors.newDirectExecutorService()).when(
                OnDevicePersonalizationExecutors::getBackgroundExecutor);
        doReturn(MoreExecutors.newDirectExecutorService()).when(
                OnDevicePersonalizationExecutors::getLightweightExecutor);

        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        latch.await();

        assertTrue(result);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        mSpyFlags.mGlobalKillSwitch = true;
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));
        assertTrue(result);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
    }

    @Test
    @MockStatic(OdpJobScheduler.class)
    @MockStatic(FlagsFactory.class)
    public void onStartJobTestSpeEnabled() {
        mSpyFlags.mSpeOnOdpDownloadProcessingJobEnabled = true;

        // Mock OdpJobScheduler to not actually schedule the job.
        OdpJobScheduler mockedScheduler = mock(OdpJobScheduler.class);
        doReturn(mockedScheduler).when(() -> OdpJobScheduler.getInstance(any()));

        assertThat(mSpyService.onStartJob(mock(JobParameters.class))).isFalse();

        // Verify SPE scheduler has rescheduled the job.
        verify(mockedScheduler).schedule(any(), any());
    }

    @Test
    public void onStopJobTest() {
        assertTrue(mSpyService.onStopJob(mock(JobParameters.class)));
    }

    @Test
    public void testSuccessfulScheduling() {
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        assertEquals(SCHEDULING_RESULT_CODE_SUCCESSFUL,
                OnDevicePersonalizationDownloadProcessingJobService
                        .schedule(mContext, /* forceSchedule */ false));
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID) != null);
        assertEquals(SCHEDULING_RESULT_CODE_SKIPPED,
                OnDevicePersonalizationDownloadProcessingJobService
                        .schedule(mContext, /* forceSchedule */ false));
    }
}
