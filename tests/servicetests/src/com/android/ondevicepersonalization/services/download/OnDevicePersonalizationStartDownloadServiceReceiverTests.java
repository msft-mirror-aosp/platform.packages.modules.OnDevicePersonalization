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

import static org.junit.Assert.assertTrue;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;
import com.android.ondevicepersonalization.services.download.mdd.LocalFileDownloader;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationStartDownloadServiceReceiverTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() throws Exception {
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancel(OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(
                OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
    }

    @Test
    public void testOnReceive() throws Exception {
        // Use direct executor to keep all work sequential for the tests
        ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
        MobileDataDownloadFactory.getMdd(mContext, new LocalFileDownloader(
                        MobileDataDownloadFactory.getFileStorage(mContext), executorService,
                        mContext),
                executorService);

        OnDevicePersonalizationStartDownloadServiceReceiver receiver =
                new OnDevicePersonalizationStartDownloadServiceReceiver(
                        executorService);

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        // MDD tasks
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID) != null);
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID) != null);
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID) != null);
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID) != null);
    }

    @Test
    public void testOnReceiveInvalidIntent() throws Exception {
        OnDevicePersonalizationStartDownloadServiceReceiver receiver =
                new OnDevicePersonalizationStartDownloadServiceReceiver();

        Intent intent = new Intent(Intent.ACTION_DIAL_EMERGENCY);
        receiver.onReceive(mContext, intent);
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        // MDD tasks
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID) == null);
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID) == null);
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID) == null);
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID) == null);
    }
}
