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

package com.android.federatedcompute.services.sharedlibrary.spe;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.federatedcompute.services.common.FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.scheduling.DeleteExpiredJob;
import com.android.federatedcompute.services.scheduling.DeleteExpiredJobService;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Unit tests for {@link FederatedComputeJobServiceFactory}. */
@MockStatic(DeleteExpiredJobService.class)
public final class FederatedComputeJobServiceFactoryTest {
    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Rule(order = 1)
    public final Expect expect = Expect.create();

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor sExecutor = Executors.newCachedThreadPool();
    private static final Map<Integer, String> sJobIdToNameMap = Map.of();

    private FederatedComputeJobServiceFactory mFactory;

    @Mock private JobServiceLogger mMockJobServiceLogger;

    @Mock private ModuleJobPolicy mMockModuleJobPolicy;

    @Mock private ClientErrorLogger mMockErrorLogger;

    @Mock private Flags mMockFlags;

    @Mock private JobSchedulingLogger mMockJobSchedulingLogger;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mFactory =
                new FederatedComputeJobServiceFactory(
                        mMockJobServiceLogger,
                        mMockJobSchedulingLogger,
                        mMockModuleJobPolicy,
                        mMockErrorLogger,
                        sJobIdToNameMap,
                        sExecutor,
                        mMockFlags);
    }

    @Test
    public void testGetJobInstance_notConfiguredJob() {
        int notConfiguredJobId = -1;

        assertThat(mFactory.getJobWorkerInstance(notConfiguredJobId)).isNull();
    }

    @Test
    public void testGetJobInstance() {
        expect.withMessage("getJobWorkerInstance() for DeleteExpiredJob")
                .that(mFactory.getJobWorkerInstance(DELETE_EXPIRED_JOB_ID))
                .isInstanceOf(DeleteExpiredJob.class);
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_notConfiguredJob() {
        int notConfiguredJobId = -1;

        mFactory.rescheduleJobWithLegacyMethod(sContext, notConfiguredJobId);
    }

    @Test
    public void testRescheduleJobWithLegacyMethod() {
        boolean forceSchedule = true;

        mFactory.rescheduleJobWithLegacyMethod(sContext, DELETE_EXPIRED_JOB_ID);
        verify(
                () ->
                        DeleteExpiredJobService.scheduleJobIfNeeded(
                                sContext, mMockFlags, forceSchedule));
    }

    @Test
    public void testGetJobIdToNameMap() {
        assertThat(mFactory.getJobIdToNameMap()).isSameInstanceAs(sJobIdToNameMap);
    }

    @Test
    public void testGetJobServiceLogger() {
        assertThat(mFactory.getJobServiceLogger()).isSameInstanceAs(mMockJobServiceLogger);
    }

    @Test
    public void testGetJobSchedulingLogger() {
        assertThat(mFactory.getJobSchedulingLogger()).isSameInstanceAs(mMockJobSchedulingLogger);
    }

    @Test
    public void testGetErrorLogger() {
        assertThat(mFactory.getErrorLogger()).isSameInstanceAs(mMockErrorLogger);
    }

    @Test
    public void testGetExecutor() {
        assertThat(mFactory.getBackgroundExecutor()).isSameInstanceAs(sExecutor);
    }

    @Test
    public void testGetModuleJobPolicy() {
        assertThat(mFactory.getModuleJobPolicy()).isSameInstanceAs(mMockModuleJobPolicy);
    }

    @Test
    public void testGetFlags() {
        assertThat(mFactory.getFlags()).isSameInstanceAs(mMockFlags);
    }
}
