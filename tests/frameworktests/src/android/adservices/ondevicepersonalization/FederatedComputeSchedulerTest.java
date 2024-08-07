/*
 * Copyright 2022 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.federatedcompute.common.TrainingOptions;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

/** Unit Tests for {@link FederatedComputeScheduler}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FederatedComputeSchedulerTest {

    private static final String VALID_POPULATION_NAME = "population";
    private static final String ERROR_POPULATION_NAME = "err";

    private static final TrainingInterval TEST_TRAINING_INTERVAL =
            new TrainingInterval.Builder()
                    .setMinimumInterval(Duration.ofHours(10))
                    .setSchedulingMode(TrainingInterval.SCHEDULING_MODE_ONE_TIME)
                    .build();

    private static final FederatedComputeScheduler.Params TEST_SCHEDULER_PARAMS =
            new FederatedComputeScheduler.Params(TEST_TRAINING_INTERVAL);

    private static final FederatedComputeInput TEST_FC_INPUT =
            new FederatedComputeInput.Builder().setPopulationName(VALID_POPULATION_NAME).build();
    private static final FederatedComputeScheduleRequest TEST_SCHEDULE_INPUT =
            new FederatedComputeScheduleRequest.Builder(TEST_SCHEDULER_PARAMS)
                    .setPopulationName(VALID_POPULATION_NAME)
                    .build();

    private final FederatedComputeScheduler mFederatedComputeScheduler =
            new FederatedComputeScheduler(
                    IFederatedComputeService.Stub.asInterface(new FederatedComputeService()),
                    IDataAccessService.Stub.asInterface(new TestDataService()));

    private boolean mCancelCalled = false;
    private boolean mScheduleCalled = false;
    private boolean mLogApiCalled = false;

    @Test
    public void testScheduleSuccess() {
        mFederatedComputeScheduler.schedule(TEST_SCHEDULER_PARAMS, TEST_FC_INPUT);

        assertThat(mScheduleCalled).isTrue();
        assertThat(mLogApiCalled).isTrue();
    }

    @Test
    public void testSchedule_withOutcomeReceiver_success() throws Exception {
        var receiver = new ResultReceiver();

        mFederatedComputeScheduler.schedule(TEST_SCHEDULE_INPUT, receiver);

        assertNotNull(receiver.getResult());
        assertTrue(receiver.isSuccess());
        assertThat(mScheduleCalled).isTrue();
        assertThat(mLogApiCalled).isTrue();
    }

    @Test
    public void testSchedule_withOutcomeReceiver_error() throws Exception {
        FederatedComputeInput input =
                new FederatedComputeInput.Builder()
                        .setPopulationName(ERROR_POPULATION_NAME)
                        .build();
        FederatedComputeScheduleRequest scheduleInput =
                new FederatedComputeScheduleRequest.Builder(TEST_SCHEDULER_PARAMS)
                        .setPopulationName(ERROR_POPULATION_NAME)
                        .build();
        var receiver = new ResultReceiver();

        mFederatedComputeScheduler.schedule(scheduleInput, receiver);

        assertNull(receiver.getResult());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(
                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED_TRAINING,
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        assertThat(mScheduleCalled).isTrue();
        assertThat(mLogApiCalled).isTrue();
    }

    @Test
    public void testScheduleNull() {
        FederatedComputeScheduler fcs = new FederatedComputeScheduler(null, new TestDataService());

        assertThrows(
                IllegalStateException.class,
                () -> fcs.schedule(TEST_SCHEDULER_PARAMS, TEST_FC_INPUT));
    }

    @Test
    public void testScheduleError() {
        FederatedComputeInput input =
                new FederatedComputeInput.Builder()
                        .setPopulationName(ERROR_POPULATION_NAME)
                        .build();

        mFederatedComputeScheduler.schedule(TEST_SCHEDULER_PARAMS, input);

        assertThat(mScheduleCalled).isTrue();
        assertThat(mLogApiCalled).isTrue();
    }

    @Test
    public void testCancelSuccess() {
        mFederatedComputeScheduler.cancel(TEST_FC_INPUT);

        assertThat(mCancelCalled).isTrue();
        assertThat(mLogApiCalled).isTrue();
    }

    @Test
    public void testCancelNull() {
        FederatedComputeScheduler fcs = new FederatedComputeScheduler(null, new TestDataService());

        assertThrows(IllegalStateException.class, () -> fcs.cancel(TEST_FC_INPUT));
    }

    @Test
    public void testCancelError() {
        FederatedComputeInput input =
                new FederatedComputeInput.Builder()
                        .setPopulationName(ERROR_POPULATION_NAME)
                        .build();

        mFederatedComputeScheduler.cancel(input);

        assertThat(mCancelCalled).isTrue();
        assertThat(mLogApiCalled).isTrue();
    }

    private class FederatedComputeService extends IFederatedComputeService.Stub {
        @Override
        public void schedule(
                TrainingOptions trainingOptions,
                IFederatedComputeCallback iFederatedComputeCallback)
                throws RemoteException {
            mScheduleCalled = true;
            if (trainingOptions.getPopulationName().equals(ERROR_POPULATION_NAME)) {
                iFederatedComputeCallback.onFailure(1);
                return;
            }
            iFederatedComputeCallback.onSuccess();
        }

        @Override
        public void cancel(String s, IFederatedComputeCallback iFederatedComputeCallback)
                throws RemoteException {
            mCancelCalled = true;
            if (s.equals(ERROR_POPULATION_NAME)) {
                iFederatedComputeCallback.onFailure(1);
                return;
            }
            iFederatedComputeCallback.onSuccess();
        }
    }

    private class TestDataService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {}

        @Override
        public void logApiCallStats(int apiName, long latencyMillis, int responseCode) {
            mLogApiCalled = true;
        }
    }
}
