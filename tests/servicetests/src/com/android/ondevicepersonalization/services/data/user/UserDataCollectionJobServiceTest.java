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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;

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
    public void onStartJobTest() {
        doReturn(mContext.getPackageManager()).when(mService).getPackageManager();
        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        ExtendedMockito.doReturn(true).when(mUserPrivacyStatus).isProtectedAudienceEnabled();
        ExtendedMockito.doReturn(true).when(mUserPrivacyStatus).isMeasurementEnabled();

        boolean result = mService.onStartJob(mock(JobParameters.class));
        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
    }

    @Test
    public void onStartJobTestKillSwitchEnabled() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        doReturn(mJobScheduler).when(mService).getSystemService(JobScheduler.class);
        mService.schedule(mContext);
        assertNotNull(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID));

        boolean result = mService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        assertNull(
                mJobScheduler.getPendingJob(OnDevicePersonalizationConfig.USER_DATA_COLLECTION_ID));
    }

    @Test
    public void onStartJobTestUserControlRevoked() {
        mUserDataCollector.updateUserData(RawUserData.getInstance());
        assertTrue(mUserDataCollector.isInitialized());
        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        ExtendedMockito.doReturn(false).when(mUserPrivacyStatus).isMeasurementEnabled();
        ExtendedMockito.doReturn(false).when(mUserPrivacyStatus).isProtectedAudienceEnabled();

        boolean result = mService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mService, times(1)).jobFinished(any(), eq(false));
        assertFalse(mUserDataCollector.isInitialized());
    }

    @Test
    public void onStopJobTest() {
        assertTrue(mService.onStopJob(mock(JobParameters.class)));
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
