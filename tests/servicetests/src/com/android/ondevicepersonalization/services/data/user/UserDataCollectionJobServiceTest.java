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

package com.android.ondevicepersonalization.services.data.user;

import static android.app.job.JobScheduler.RESULT_FAILURE;
import static android.app.job.JobScheduler.RESULT_SUCCESS;

import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import static com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.sharedlibrary.spe.OdpJobScheduler;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class UserDataCollectionJobServiceTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .spyStatic(UserPrivacyStatus.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);
    private UserDataCollector mUserDataCollector;
    private UserDataCollectionJobService mService;
    private UserPrivacyStatus mUserPrivacyStatus;
    @Mock private Flags mMockFlags;

    @Before
    public void setup() throws Exception {
        mUserPrivacyStatus = spy(UserPrivacyStatus.getInstance());
        mUserDataCollector = UserDataCollector.getInstanceForTest(mContext);
        mService = spy(new UserDataCollectionJobService(new TestInjector()));
        doNothing().when(mService).jobFinished(any(), anyBoolean());
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getSpeOnUserDataCollectionJobEnabled()).thenReturn(false);
    }

    @After
    public void cleanUp() {
        mUserDataCollector.clearUserData(RawUserData.getInstance());
        mUserDataCollector.clearMetadata();
    }

    @Test
    public void testDefaultNoArgConstructor() {
        UserDataCollectionJobService instance =
                new UserDataCollectionJobService(new TestInjector());
        assertNotNull("default no-arg constructor is required by JobService", instance);
    }

    @Test
    public void onStartJobTest() throws Exception {
        doReturn(mContext.getPackageManager()).when(mService).getPackageManager();
        doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(false).when(mUserPrivacyStatus)
                .isProtectedAudienceAndMeasurementBothDisabled();

        boolean result = mService.onStartJob(mock(JobParameters.class));
        assertTrue(result);
        Thread.sleep(2000);
        verify(mService, times(1)).jobFinished(any(), eq(false));
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        doReturn(mJobScheduler).when(mService).getSystemService(JobScheduler.class);
        mService.schedule(mContext, /* forceSchedule */ false);
        assertNotNull(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID));

        boolean result = mService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        assertNull(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID));
    }

    @Test
    @MockStatic(OdpJobScheduler.class)
    @MockStatic(FlagsFactory.class)
    public void onStartJobTestSpeEnabled() {
        // Enable SPE on UserDataCollectionJob & UserDataCollectionJobService
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        when(mMockFlags.getSpeOnUserDataCollectionJobEnabled()).thenReturn(true);

        // Mock OdpJobScheduler to not actually schedule the job.
        OdpJobScheduler mockedScheduler = mock(OdpJobScheduler.class);
        doReturn(mockedScheduler).when(() -> OdpJobScheduler.getInstance(any()));

        assertThat(mService.onStartJob(mock(JobParameters.class))).isFalse();

        // Verify SPE scheduler has rescheduled the job.
        verify(mockedScheduler).schedule(any(), any());
    }

    @Test
    public void testSchedule_scheduleSuccessful_resultCodeSuccess() {
        Context spyContext = getSpyContext();
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        doReturn(mockJobScheduler).when(spyContext).getSystemService(JobScheduler.class);
        doReturn(/* jobInfo */ null).when(mockJobScheduler).getPendingJob(USER_DATA_COLLECTION_ID);

        // Schedule successful
        doReturn(RESULT_SUCCESS).when(mockJobScheduler).schedule(any());

        int resultCode = mService.schedule(spyContext, /* forceSchedule */ false);

        assertThat(resultCode).isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testSchedule_scheduleFailure_resultCodeFailed() {
        Context spyContext = getSpyContext();
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        doReturn(mockJobScheduler).when(spyContext).getSystemService(JobScheduler.class);
        doReturn(/* jobInfo */ null).when(mockJobScheduler).getPendingJob(USER_DATA_COLLECTION_ID);

        // Schedule failure
        doReturn(RESULT_FAILURE).when(mockJobScheduler).schedule(any());

        int resultCode = mService.schedule(spyContext, /* forceSchedule */ false);

        assertThat(resultCode).isEqualTo(SCHEDULING_RESULT_CODE_FAILED);
    }

    @Test
    public void testSchedule_pendingJobForceSchedule_resultCodeSuccess() {
        Context spyContext = getSpyContext();
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        doReturn(mockJobScheduler).when(spyContext).getSystemService(JobScheduler.class);

        // Pending job
        doReturn(mock(JobInfo.class)).when(mockJobScheduler)
                .getPendingJob(USER_DATA_COLLECTION_ID);
        doReturn(RESULT_SUCCESS).when(mockJobScheduler).schedule(any());

        int resultCode = mService.schedule(spyContext, /* forceSchedule */ true);

        assertThat(resultCode).isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testSchedule_nullJobScheduler_resultCodeFailed() {
        Context spyContext = getSpyContext();

        // Null scheduler
        doReturn(/* jobScheduler */ null).when(spyContext).getSystemService(JobScheduler.class);

        int resultCode = mService.schedule(spyContext, /* forceSchedule */ false);

        assertThat(resultCode).isEqualTo(SCHEDULING_RESULT_CODE_FAILED);
    }

    @Test
    public void testSchedule_pendingJob_resultCodeSkipped() {
        Context spyContext = getSpyContext();
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        doReturn(mockJobScheduler).when(spyContext).getSystemService(JobScheduler.class);

        // Pending job
        doReturn(/* jobInfo */ mock(JobInfo.class)).when(mockJobScheduler)
                .getPendingJob(USER_DATA_COLLECTION_ID);

        int resultCode = mService.schedule(spyContext, /* forceSchedule */ false);

        assertThat(resultCode).isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);
    }

    @Test
    public void onStartJobTestUserControlRevoked() throws Exception {
        mUserDataCollector.updateUserData(RawUserData.getInstance());
        assertTrue(mUserDataCollector.isInitialized());
        doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(true).when(mUserPrivacyStatus)
                .isProtectedAudienceAndMeasurementBothDisabled();

        boolean result = mService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        Thread.sleep(2000);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        assertFalse(mUserDataCollector.isInitialized());
    }

    @Test
    public void onStopJobTest() {
        assertTrue(mService.onStopJob(mock(JobParameters.class)));
    }

    private Context getSpyContext() {
        return spy(ApplicationProvider.getApplicationContext());
    }

    private class TestInjector extends UserDataCollectionJobService.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        Flags getFlags() {
            return mMockFlags;
        }
    }
}
