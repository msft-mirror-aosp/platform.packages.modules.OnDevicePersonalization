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

package com.android.federatedcompute.services.scheduling;

import static com.android.federatedcompute.services.data.fbs.SchedulingReason.SCHEDULING_REASON_NEW_TASK;
import static com.android.federatedcompute.services.scheduling.JobSchedulerHelper.TRAINING_JOB_SERVICE;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.PackageUtils;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;

import com.google.flatbuffers.FlatBufferBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FederatedComputeLearningJobScheduleOrchestratorTest {

    private static final String CALLING_PACKAGE_NAME = "callingPkg";
    private static final String CALLING_CLASS_NAME =
            "FederatedComputeLearningJobScheduleOrchestratorTest";
    private static final String POPULATION_NAME = "population";
    private static final String SERVER_ADDRESS = "https://server.uri/";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private FederatedTrainingTaskDao mTrainingTaskDao;
    private Clock mClock;

    private FederatedComputeLearningJobScheduleOrchestrator mOrchestrator;

    @Before
    public void setUp() {
        mClock =  MonotonicClock.getInstance();
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancelAll();
        mTrainingTaskDao = FederatedTrainingTaskDao.getInstanceForTest(mContext);

        mOrchestrator =
                new FederatedComputeLearningJobScheduleOrchestrator(
                        mContext, mTrainingTaskDao, new JobSchedulerHelper(mClock));
    }

    @After
    public void tearDown() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancelAll();
    }

    @Test
    public void checkAndScheduleTest() throws PackageManager.NameNotFoundException {
        long nowMillis = mClock.currentTimeMillis();
        FederatedTrainingTask task1 =
                FederatedTrainingTask.builder()
                        .jobId(123)
                        .appPackageName(CALLING_PACKAGE_NAME)
                        .ownerId(CALLING_PACKAGE_NAME + "/" + CALLING_CLASS_NAME)
                        .ownerIdCertDigest(
                                PackageUtils.getCertDigest(mContext, mContext.getPackageName()))
                        .populationName(POPULATION_NAME)
                        .serverAddress(SERVER_ADDRESS)
                        .creationTime(nowMillis)
                        .earliestNextRunTime(nowMillis + 1000000)
                        .constraints(createDefaultTrainingConstraints())
                        .lastScheduledTime(nowMillis)
                        .schedulingReason(SCHEDULING_REASON_NEW_TASK)
                        .build();
        FederatedTrainingTask task2 =
                FederatedTrainingTask.builder()
                        .jobId(213)
                        .appPackageName(CALLING_PACKAGE_NAME)
                        .ownerId(CALLING_PACKAGE_NAME + "/" + CALLING_CLASS_NAME)
                        .ownerIdCertDigest(
                                PackageUtils.getCertDigest(mContext, mContext.getPackageName()))
                        .populationName(POPULATION_NAME)
                        .serverAddress(SERVER_ADDRESS)
                        .creationTime(nowMillis)
                        .earliestNextRunTime(nowMillis + 1000000)
                        .constraints(createDefaultTrainingConstraints())
                        .lastScheduledTime(nowMillis)
                        .schedulingReason(SCHEDULING_REASON_NEW_TASK)
                        .build();
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task1);
        mTrainingTaskDao.updateOrInsertFederatedTrainingTask(task2);
        ComponentName jobComponent = new ComponentName(mContext, TRAINING_JOB_SERVICE);
        JobInfo jobInfo2 =
                new JobInfo.Builder(task2.jobId(), jobComponent)
                        .setMinimumLatency(1000000000)
                        .build();
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.schedule(jobInfo2);

        mOrchestrator.checkAndSchedule();

        //check task 1 was rescheduled
        JobInfo fetchedJobInfo1 = jobScheduler.getPendingJob(task1.jobId());
        assertNotNull(fetchedJobInfo1);
        assertEquals(task1.jobId(), fetchedJobInfo1.getId());
        assertTrue(fetchedJobInfo1.isPersisted());
        assertTrue(fetchedJobInfo1.isRequireDeviceIdle());
        //check task 2 was not rescheduled since there was already a job with same Id scheduled
        JobInfo fetchedJobInfo2 = jobScheduler.getPendingJob(task2.jobId());
        assertNotNull(fetchedJobInfo2);
        assertEquals(task2.jobId(), fetchedJobInfo2.getId());
        assertFalse(fetchedJobInfo2.isPersisted());
        assertFalse(fetchedJobInfo2.isRequireDeviceIdle());
    }

    private static byte[] createDefaultTrainingConstraints() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(TrainingConstraints.createTrainingConstraints(builder, true, true, true));
        return builder.sizedByteArray();
    }
}
