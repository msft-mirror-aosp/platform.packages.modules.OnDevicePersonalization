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

package com.android.ondevicepersonalization.services.sharedlibrary.spe;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Unit tests for {@link OdpJobServiceFactory}. */
public final class OdpJobServiceFactoryTest {
    private static final Executor sExecutor = Executors.newCachedThreadPool();
    private static final Map<Integer, String> sJobIdToNameMap = Map.of();

    private OdpJobServiceFactory mFactory;

    @Mock private JobServiceLogger mMockJobServiceLogger;

    @Mock private ModuleJobPolicy mMockModuleJobPolicy;

    @Mock private ClientErrorLogger mMockErrorLogger;

    @Mock private Flags mMockFlags;

    @Mock private JobSchedulingLogger mMockJobSchedulingLogger;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mFactory =
                new OdpJobServiceFactory(
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
        int notConfiguredJobId = 1000;

        assertThat(mFactory.getJobWorkerInstance(notConfiguredJobId)).isNull();
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_notConfiguredJob() {
        int notConfiguredJobId = 1000;

        mFactory.rescheduleJobWithLegacyMethod(notConfiguredJobId);
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
