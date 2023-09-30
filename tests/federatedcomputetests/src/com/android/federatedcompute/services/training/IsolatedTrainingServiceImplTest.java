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

package com.android.federatedcompute.services.training;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.federatedcompute.common.ClientConstants;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FileUtils;
import com.android.federatedcompute.services.testutils.FakeExampleStoreIterator;
import com.android.federatedcompute.services.testutils.TrainingTestUtil;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.RetryInfo;
import com.google.intelligence.fcp.client.engine.TaskRetry;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public final class IsolatedTrainingServiceImplTest {
    private static final String POPULATION_NAME = "population_name";
    private static final long RUN_ID = 12345L;
    private static final FakeExampleStoreIterator FAKE_EXAMPLE_STORE_ITERATOR =
            new FakeExampleStoreIterator(ImmutableList.of());
    private static final ExampleSelector EXAMPLE_SELECTOR =
            ExampleSelector.newBuilder().setCollectionUri("collection_uri").build();
    private static final TaskRetry TASK_RETRY =
            TaskRetry.newBuilder().setRetryToken("foobar").build();
    private static final FLRunnerResult FL_RUNNER_SUCCESS_RESULT =
            FLRunnerResult.newBuilder()
                    .setContributionResult(ContributionResult.SUCCESS)
                    .setRetryInfo(
                            RetryInfo.newBuilder()
                                    .setRetryToken(TASK_RETRY.getRetryToken())
                                    .build())
                    .build();
    private static final FLRunnerResult FL_RUNNER_FAIL_RESULT =
            FLRunnerResult.newBuilder()
                    .setContributionResult(ContributionResult.FAIL)
                    .setRetryInfo(
                            RetryInfo.newBuilder()
                                    .setRetryToken(TASK_RETRY.getRetryToken())
                                    .build())
                    .build();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private IsolatedTrainingServiceImpl mIsolatedTrainingService;
    private Bundle mCallbackResult;
    @Mock private ComputationRunner mComputationRunner;
    private MockitoSession mStaticMockSession;
    private ParcelFileDescriptor mInputCheckpointFd;
    private ParcelFileDescriptor mOutputCheckpointFd;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FederatedComputeExecutors.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService())
                .when(FederatedComputeExecutors::getBackgroundExecutor);
        ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService())
                .when(FederatedComputeExecutors::getLightweightExecutor);

        mIsolatedTrainingService = new IsolatedTrainingServiceImpl(mComputationRunner);
        mInputCheckpointFd = getInputCheckpointFd();
        mOutputCheckpointFd = getOutputCheckpointFd();
    }

    @After
    public void tearDown() throws Exception {
        mStaticMockSession.finishMocking();
        mInputCheckpointFd.close();
        mOutputCheckpointFd.close();
    }

    @Test
    public void runFlTrainingSuccess() throws Exception {
        when(mComputationRunner.runTaskWithNativeRunner(
                        anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(FL_RUNNER_SUCCESS_RESULT);

        Bundle bundle = buildInputBundle();
        mIsolatedTrainingService.runFlTraining(bundle, new TestServiceCallback());

        byte[] flRunnerResultBytes = mCallbackResult.getByteArray(Constants.EXTRA_FL_RUNNER_RESULT);
        FLRunnerResult flRunnerResult = FLRunnerResult.parseFrom(flRunnerResultBytes);
        assertThat(flRunnerResult).isEqualTo(FL_RUNNER_SUCCESS_RESULT);
    }

    @Test
    public void runFlTrainingFailure() throws Exception {
        when(mComputationRunner.runTaskWithNativeRunner(
                        anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(FL_RUNNER_FAIL_RESULT);

        Bundle bundle = buildInputBundle();
        mIsolatedTrainingService.runFlTraining(bundle, new TestServiceCallback());

        byte[] flRunnerResultBytes = mCallbackResult.getByteArray(Constants.EXTRA_FL_RUNNER_RESULT);
        FLRunnerResult flRunnerResult = FLRunnerResult.parseFrom(flRunnerResultBytes);
        assertThat(flRunnerResult).isEqualTo(FL_RUNNER_FAIL_RESULT);
    }

    @Test
    public void runFlTrainingMissingExampleSelector_returnsFailure() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);

        assertThrows(
                NullPointerException.class,
                () -> mIsolatedTrainingService.runFlTraining(bundle, new TestServiceCallback()));
    }

    @Test
    public void runFlTrainingInvalidExampleSelector_returnsFailure() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);

        bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, "exampleselector".getBytes());

        assertThrows(
                IllegalArgumentException.class,
                () -> mIsolatedTrainingService.runFlTraining(bundle, new TestServiceCallback()));
    }

    @Test
    public void runFlTrainingNullPlan_returnsFailure() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);
        bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, EXAMPLE_SELECTOR.toByteArray());

        assertThrows(
                NullPointerException.class,
                () -> mIsolatedTrainingService.runFlTraining(bundle, new TestServiceCallback()));
    }

    @Test
    public void runCancelFlTraining() {
        assertThat(mIsolatedTrainingService.mInterruptState.get()).isFalse();
        mIsolatedTrainingService.cancelTraining(RUN_ID);

        assertThat(mIsolatedTrainingService.mInterruptState.get()).isTrue();
    }

    private Bundle buildInputBundle() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, EXAMPLE_SELECTOR.toByteArray());
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);
        ClientOnlyPlan clientOnlyPlan = TrainingTestUtil.createFederatedAnalyticClientPlan();
        String clientPlanFile =
                FileUtils.createTempFile(Constants.EXTRA_CLIENT_ONLY_PLAN_FD, ".pb");
        FileUtils.writeToFile(clientPlanFile, clientOnlyPlan.toByteArray());
        bundle.putParcelable(
                Constants.EXTRA_CLIENT_ONLY_PLAN_FD,
                FileUtils.createTempFileDescriptor(
                        clientPlanFile, ParcelFileDescriptor.MODE_READ_ONLY));
        return bundle;
    }

    private ParcelFileDescriptor getInputCheckpointFd() throws Exception {
        File inputCheckpointFile = File.createTempFile("input", ".ckp");
        return ParcelFileDescriptor.open(inputCheckpointFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private ParcelFileDescriptor getOutputCheckpointFd() throws Exception {
        File outputCheckpointFile = File.createTempFile("output", ".ckp");
        return ParcelFileDescriptor.open(
                outputCheckpointFile, ParcelFileDescriptor.MODE_WRITE_ONLY);
    }

    class TestServiceCallback extends ITrainingResultCallback.Stub {
        @Override
        public void onResult(Bundle result) {
            mCallbackResult = result;
            mLatch.countDown();
        }
    }
}
