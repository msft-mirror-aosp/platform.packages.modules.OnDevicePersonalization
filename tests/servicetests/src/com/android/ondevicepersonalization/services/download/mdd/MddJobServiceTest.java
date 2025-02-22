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

package com.android.ondevicepersonalization.services.download.mdd;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.ondevicepersonalization.services.download.mdd.MddTaskScheduler.MDD_TASK_TAG_KEY;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJob;
import com.android.ondevicepersonalization.services.statsd.joblogging.OdpJobServiceLogger;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class MddJobServiceTest {
    private static final int TIMEOUT_MILLIS = 5000;
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private JobScheduler mMockJobScheduler;
    private MddJobService mSpyService;
    private UserPrivacyStatus mUserPrivacyStatus;

    @Mock
    private Flags mMockFlags;
    @Mock
    private OdpJobServiceLogger mMockOdpJobServiceLogger;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(OnDevicePersonalizationDownloadProcessingJob.class)
            .mockStatic(OdpJobServiceLogger.class)
            .spyStatic(UserPrivacyStatus.class)
            .spyStatic(OnDevicePersonalizationExecutors.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    private class TestInjector extends MddJobService.Injector {
        @Override
        ListeningExecutorService getBackgroundExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        Flags getFlags() {
            return mMockFlags;
        }
    }

    @Before
    public void setup() throws Exception {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        mUserPrivacyStatus = spy(UserPrivacyStatus.getInstance());
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        MobileDataDownloadFactory.getMdd(mContext, executorService, executorService);

        mSpyService = spy(new MddJobService(new TestInjector()));
        mMockJobScheduler = mock(JobScheduler.class);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(null).when(mMockJobScheduler).getPendingJob(DOWNLOAD_PROCESSING_TASK_JOB_ID);
        doReturn(0).when(mMockJobScheduler).schedule(any());
        doReturn(mContext.getPackageName()).when(mSpyService).getPackageName();
        doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(mMockOdpJobServiceLogger).when(() -> OdpJobServiceLogger.getInstance(any()));
    }

    @Test
    public void testDefaultNoArgConstructor() {
        MddJobService instance = new MddJobService();
        assertNotNull("default no-arg constructor is required by JobService", instance);
    }

    @Test
    public void onStartJobTest() throws Exception {
        doReturn(MoreExecutors.newDirectExecutorService()).when(
                OnDevicePersonalizationExecutors::getBackgroundExecutor);
        doReturn(false).when(mUserPrivacyStatus)
                .isProtectedAudienceAndMeasurementBothDisabled();

        JobParameters jobParameters = createDefaultMockJobParameters();
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
        doReturn(extras).when(jobParameters).getExtras();

        boolean result = mSpyService.onStartJob(jobParameters);
        assertTrue(result);
        verify(mSpyService, timeout(TIMEOUT_MILLIS)).jobFinished(any(), eq(false));
        verify(() -> OnDevicePersonalizationDownloadProcessingJob.schedule(any()));
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
        JobInfo jobInfo = new JobInfo.Builder(
                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                new ComponentName(mContext, MddJobService.class))
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(21_600_000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setExtras(extras)
                .build();
        mJobScheduler.schedule(jobInfo);
        assertTrue(mJobScheduler.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID) != null);
        doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        JobParameters jobParameters = createDefaultMockJobParameters();
        doReturn(extras).when(jobParameters).getExtras();
        boolean result = mSpyService.onStartJob(jobParameters);
        assertTrue(result);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(0)).schedule(any());
        verify(() -> OnDevicePersonalizationDownloadProcessingJob.schedule(any()), never());
        assertTrue(mJobScheduler.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID) == null);
    }

    @Test
    public void onStartJobTestUserControlRevoked() throws Exception {
        doReturn(true).when(mUserPrivacyStatus)
                .isProtectedAudienceAndMeasurementBothDisabled();
        JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
        JobInfo jobInfo = new JobInfo.Builder(
                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                new ComponentName(mContext, MddJobService.class))
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(21_600_000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setExtras(extras)
                .build();
        mJobScheduler.schedule(jobInfo);
        assertTrue(mJobScheduler.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID) != null);
        doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        JobParameters jobParameters = createDefaultMockJobParameters();
        doReturn(extras).when(jobParameters).getExtras();
        boolean result = mSpyService.onStartJob(jobParameters);
        assertTrue(result);
        verify(mSpyService, timeout(TIMEOUT_MILLIS)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, never()).schedule(any());
        verify(() -> OnDevicePersonalizationDownloadProcessingJob.schedule(any()), never());
    }

    @Test
    public void onStartJob_withNoTaskTagTest_logJobFailure() {
        doReturn(false).when(mUserPrivacyStatus).isProtectedAudienceAndMeasurementBothDisabled();

        mSpyService.onStartJob(createDefaultMockJobParameters());

        verify(mSpyService, timeout(TIMEOUT_MILLIS)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(0)).schedule(any());
        verify(mMockOdpJobServiceLogger).recordJobFinished(
                anyInt(),
                /* isSuccessful */ eq(false),
                anyBoolean());
    }

    @Test
    public void onStartJobFailHandleTaskTest() throws Exception {
        doReturn(false).when(mUserPrivacyStatus)
                .isProtectedAudienceAndMeasurementBothDisabled();

        JobParameters jobParameters = createDefaultMockJobParameters();
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, "INVALID_TASK_TAG_KEY");
        doReturn(extras).when(jobParameters).getExtras();

        boolean result = mSpyService.onStartJob(jobParameters);
        assertTrue(result);
        verify(mSpyService, timeout(TIMEOUT_MILLIS)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(0)).schedule(any());
    }

    @Test
    public void onStopJobTest() {
        JobParameters jobParameters = createDefaultMockJobParameters();
        PersistableBundle extras = new PersistableBundle();
        extras.putString(MDD_TASK_TAG_KEY, WIFI_CHARGING_PERIODIC_TASK);
        doReturn(extras).when(jobParameters).getExtras();

        assertTrue(mSpyService.onStopJob(jobParameters));
        verify(mMockJobScheduler, times(0)).schedule(any());
    }

    private JobParameters createDefaultMockJobParameters() {
        return createMockJobParameters(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
    }

    private JobParameters createMockJobParameters(int jobId) {
        JobParameters mockJobParameters = mock(JobParameters.class);
        when(mockJobParameters.getJobId()).thenReturn(jobId);
        return mockJobParameters;
    }
}
