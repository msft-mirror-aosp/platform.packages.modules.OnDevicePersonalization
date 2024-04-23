/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.federatedcompute.services;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.federatedcompute.services.common.DeviceUtils;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.PhFlagsTestUtil;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class FederatedComputeBroadcastReceiverTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .addStaticMockFixtures(TestableDeviceConfig::new)
                    .spyStatic(DeviceUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        ExtendedMockito.doReturn(true).when(() -> DeviceUtils.isFcpSupported(any()));
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancel(FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID);
        jobScheduler.cancel(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID);
    }

    @Test
    public void testOnReceive() {
        FederatedComputeBroadcastReceiver receiver = new FederatedComputeBroadcastReceiver();

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        assertTrue(
                jobScheduler.getPendingJob(FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID)
                        != null);
        assertTrue(
                jobScheduler.getPendingJob(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID) != null);
    }

    @Test
    public void testOnReceiveKillSwitchOn() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        FederatedComputeBroadcastReceiver receiver = new FederatedComputeBroadcastReceiver();

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        assertTrue(
                jobScheduler.getPendingJob(FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID) == null);
    }

    @Test
    public void testOnReceiveDeviceNotSupported() {
        ExtendedMockito.doReturn(false).when(() -> DeviceUtils.isFcpSupported(any()));
        FederatedComputeBroadcastReceiver receiver = new FederatedComputeBroadcastReceiver();

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        assertTrue(
                jobScheduler.getPendingJob(FederatedComputeJobInfo.ENCRYPTION_KEY_FETCH_JOB_ID)
                        == null);
        assertTrue(
                jobScheduler.getPendingJob(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID) == null);
    }
}
