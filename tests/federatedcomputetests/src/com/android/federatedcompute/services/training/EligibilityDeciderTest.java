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

package com.android.federatedcompute.services.training;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_COMPLETED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ELIGIBLE;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreService;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.ExampleStats;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.TaskHistory;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.examplestore.ExampleStoreServiceProvider;
import com.android.federatedcompute.services.testutils.FakeExampleStoreIterator;

import com.google.common.collect.ImmutableList;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.ondevicepersonalization.federatedcompute.proto.DataAvailabilityPolicy;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityPolicyEvalSpec;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityTaskInfo;
import com.google.ondevicepersonalization.federatedcompute.proto.MinimumSeparationPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

public class EligibilityDeciderTest {
    private static final int JOB_ID = 123;
    private static final String TASK_ID = "task_1";
    private static final String PACKAGE_NAME = "app_package_name";
    private static final String POPULATION_NAME = "population_name";
    private static final MinimumSeparationPolicy MIN_SEP_POLICY =
            MinimumSeparationPolicy.newBuilder()
                    .setMinimumSeparation(6)
                    .setCurrentIndex(10)
                    .build();
    private static final EligibilityTaskInfo ELIGIBILITY_TASK_MIN_SEP_POLICY =
            EligibilityTaskInfo.newBuilder()
                    .addEligibilityPolicies(
                            EligibilityPolicyEvalSpec.newBuilder()
                                    .setMinSepPolicy(MIN_SEP_POLICY)
                                    .build())
                    .build();
    private static final DataAvailabilityPolicy DATA_AVAILABILITY_POLICY =
            DataAvailabilityPolicy.newBuilder().setMinExampleCount(2).build();
    private static final EligibilityTaskInfo ELIGIBILITY_TASK_DATA_AVAILABILITY_POLICY =
            EligibilityTaskInfo.newBuilder()
                    .addEligibilityPolicies(
                            EligibilityPolicyEvalSpec.newBuilder()
                                    .setDataAvailabilityPolicy(DATA_AVAILABILITY_POLICY)
                                    .build())
                    .build();

    private Context mContext;
    private FederatedTrainingTaskDao mTrainingTaskDao;
    private EligibilityDecider mEligibilityDecider;

    @Spy
    private ExampleStoreServiceProvider mSpyExampleStoreProvider =
            new ExampleStoreServiceProvider();

    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Mock public TrainingEventLogger mMockTrainingEventLogger;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mTrainingTaskDao = FederatedTrainingTaskDao.getInstanceForTest(mContext);
        mEligibilityDecider = new EligibilityDecider(mTrainingTaskDao, mSpyExampleStoreProvider);
    }

    @After
    public void tearDown() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testMinSepPolicy_noRecord_eligible() {
        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        ELIGIBILITY_TASK_MIN_SEP_POLICY,
                        mContext,
                        mMockTrainingEventLogger);
        assertTrue(eligible);
        ArgumentCaptor<Integer> eventKindCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockTrainingEventLogger, times(2)).logEventKind(eventKindCaptor.capture());
        assertThat(eventKindCaptor.getAllValues())
                .containsAtLeast(
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED,
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ELIGIBLE);
    }

    @Test
    public void testMinSepPolicy_eligible() {
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(1)
                        .setContributionTime(120L)
                        .build());

        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        ELIGIBILITY_TASK_MIN_SEP_POLICY,
                        mContext,
                        mMockTrainingEventLogger);

        assertTrue(eligible);
        ArgumentCaptor<Integer> eventKindCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockTrainingEventLogger, times(2)).logEventKind(eventKindCaptor.capture());
        assertThat(eventKindCaptor.getAllValues())
                .containsAtLeast(
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED,
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ELIGIBLE);
    }

    @Test
    public void testMinSepPolicy_joinSameRound_notEligible() {
        // Device joins iteration round 1.
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(1)
                        .setContributionTime(120L)
                        .build());
        MinimumSeparationPolicy minimumSeparationPolicy =
                MinimumSeparationPolicy.newBuilder()
                        .setMinimumSeparation(1)
                        .setCurrentIndex(1)
                        .build();
        EligibilityTaskInfo eligibilityTaskInfo =
                EligibilityTaskInfo.newBuilder()
                        .addEligibilityPolicies(
                                EligibilityPolicyEvalSpec.newBuilder()
                                        .setMinSepPolicy(minimumSeparationPolicy)
                                        .build())
                        .build();

        // Device should not be able to join same round since min separate policy is 1.
        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        eligibilityTaskInfo,
                        mContext,
                        mMockTrainingEventLogger);

        assertFalse(eligible);
        verify(mMockTrainingEventLogger)
                .logEventKind(
                        eq(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED));
    }

    @Test
    public void testMinSepPolicy_joinNextRound_eligible() {
        // Device joins iteration round 1.
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(1)
                        .setContributionTime(120L)
                        .build());
        // Current iteration round is 2 and min separation policy is 1.
        MinimumSeparationPolicy minimumSeparationPolicy =
                MinimumSeparationPolicy.newBuilder()
                        .setMinimumSeparation(1)
                        .setCurrentIndex(2)
                        .build();
        EligibilityTaskInfo eligibilityTaskInfo =
                EligibilityTaskInfo.newBuilder()
                        .addEligibilityPolicies(
                                EligibilityPolicyEvalSpec.newBuilder()
                                        .setMinSepPolicy(minimumSeparationPolicy)
                                        .build())
                        .build();

        // Device should be able to join iteration 2.
        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        eligibilityTaskInfo,
                        mContext,
                        mMockTrainingEventLogger);

        assertTrue(eligible);

        ArgumentCaptor<Integer> eventKindCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockTrainingEventLogger, times(2)).logEventKind(eventKindCaptor.capture());
        assertThat(eventKindCaptor.getAllValues())
                .containsAtLeast(
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED,
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ELIGIBLE);
    }

    @Test
    public void testMinSepPolicy_notEligible() {
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(9)
                        .setContributionTime(120L)
                        .build());

        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        ELIGIBILITY_TASK_MIN_SEP_POLICY,
                        mContext,
                        mMockTrainingEventLogger);

        assertFalse(eligible);
        verify(mMockTrainingEventLogger)
                .logEventKind(
                        eq(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED));
    }

    @Test
    public void dataAvailabilityPolicy_noExample_notEligible() {
        TestExampleStoreService exampleStoreService =
                new TestExampleStoreService(new ArrayList<>());
        setUpExampleStoreService(exampleStoreService);

        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        ELIGIBILITY_TASK_DATA_AVAILABILITY_POLICY,
                        mContext,
                        mMockTrainingEventLogger);

        assertFalse(eligible);
        verify(mMockTrainingEventLogger)
                .logEventKind(
                        eq(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED));
    }

    @Test
    public void dataAvailabilityPolicy_notEligible() {
        TestExampleStoreService exampleStoreService =
                new TestExampleStoreService(ImmutableList.of("example1".getBytes()));
        setUpExampleStoreService(exampleStoreService);

        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        ELIGIBILITY_TASK_DATA_AVAILABILITY_POLICY,
                        mContext,
                        mMockTrainingEventLogger);

        assertFalse(eligible);
        verify(mSpyExampleStoreProvider).unbindFromExampleStoreService();
        verify(mMockTrainingEventLogger)
                .logEventKind(
                        eq(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED));
    }

    @Test
    public void dataAvailabilityPolicy_eligible() {
        TestExampleStoreService exampleStoreService =
                new TestExampleStoreService(
                        ImmutableList.of("example1".getBytes(), "example2".getBytes()));
        setUpExampleStoreService(exampleStoreService);

        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        createDefaultFederatedTrainingTask(),
                        TASK_ID,
                        ELIGIBILITY_TASK_DATA_AVAILABILITY_POLICY,
                        mContext,
                        mMockTrainingEventLogger);

        assertTrue(eligible);
        verify(mSpyExampleStoreProvider).unbindFromExampleStoreService();

        ArgumentCaptor<Integer> eventKindCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockTrainingEventLogger, times(2)).logEventKind(eventKindCaptor.capture());
        assertThat(eventKindCaptor.getAllValues())
                .containsAtLeast(
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_STARTED,
                        FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_ELIGIBLE);
        ArgumentCaptor<ExampleStats> exampleStatsCaptor =
                ArgumentCaptor.forClass(ExampleStats.class);
        verify(mMockTrainingEventLogger)
                .logEventWithExampleStats(
                        eq(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_COMPUTATION_COMPLETED),
                        exampleStatsCaptor.capture());
        ExampleStats stats = exampleStatsCaptor.getValue();
        assertThat(stats.mExampleCount.get()).isEqualTo(2);
        assertThat(stats.mStartQueryLatencyNanos.get()).isGreaterThan(0);
        assertThat(stats.mBindToExampleStoreLatencyNanos.get()).isGreaterThan(0);
    }

    private void setUpExampleStoreService(TestExampleStoreService exampleStoreService) {
        doReturn(exampleStoreService)
                .when(mSpyExampleStoreProvider)
                .getExampleStoreService(anyString(), any());
        doNothing().when(mSpyExampleStoreProvider).unbindFromExampleStoreService();
    }

    private static class TestExampleStoreService extends IExampleStoreService.Stub {
        private final List<byte[]> mExamples;

        TestExampleStoreService(List<byte[]> examples) {
            mExamples = examples;
        }

        @Override
        public void startQuery(Bundle params, IExampleStoreCallback callback)
                throws RemoteException {
            callback.onStartQuerySuccess(new FakeExampleStoreIterator(mExamples));
        }
    }

    private static byte[] createDefaultTrainingConstraints() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(TrainingConstraints.createTrainingConstraints(builder, true, true, true));
        return builder.sizedByteArray();
    }

    private FederatedTrainingTask createDefaultFederatedTrainingTask() {
        return FederatedTrainingTask.builder()
                .appPackageName(PACKAGE_NAME)
                .jobId(JOB_ID)
                .ownerId("ownerId")
                .ownerIdCertDigest("cert")
                .populationName(POPULATION_NAME)
                .serverAddress("serverAddress")
                .creationTime(1000L)
                .lastScheduledTime(1000L)
                .earliestNextRunTime(1200L)
                .constraints(createDefaultTrainingConstraints())
                .schedulingReason(SchedulingReason.SCHEDULING_REASON_NEW_TASK)
                .build();
    }
}
