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

import static android.app.job.JobScheduler.RESULT_FAILURE;
import static android.app.job.JobScheduler.RESULT_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationDownloadProcessingJobServiceTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() throws Exception {
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancel(OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID);
    }

    @Test
    public void testSuccessfulScheduling() {
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        assertEquals(RESULT_SUCCESS,
                OnDevicePersonalizationDownloadProcessingJobService.schedule(mContext));
        assertTrue(jobScheduler.getPendingJob(
                OnDevicePersonalizationConfig.DOWNLOAD_PROCESSING_TASK_JOB_ID) != null);
        assertEquals(RESULT_FAILURE,
                OnDevicePersonalizationDownloadProcessingJobService.schedule(mContext));
    }
}
