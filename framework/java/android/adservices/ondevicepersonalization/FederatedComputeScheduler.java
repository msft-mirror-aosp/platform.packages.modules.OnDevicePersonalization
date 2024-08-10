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

package android.adservices.ondevicepersonalization;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.federatedcompute.common.TrainingOptions;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handles scheduling federated compute jobs. See {@link
 * IsolatedService#getFederatedComputeScheduler}.
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
public class FederatedComputeScheduler {
    private static final String TAG = FederatedComputeScheduler.class.getSimpleName();

    private static final int FEDERATED_COMPUTE_SCHEDULE_TIMEOUT_SECONDS = 30;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private final IFederatedComputeService mFcService;
    private final IDataAccessService mDataAccessService;

    /** @hide */
    public FederatedComputeScheduler(
            IFederatedComputeService binder, IDataAccessService dataService) {
        mFcService = binder;
        mDataAccessService = dataService;
    }

    // TODO(b/300461799): add federated compute server document.
    // TODO(b/269665435): add sample code snippet.
    /**
     * Schedules a federated compute job. In {@link IsolatedService#onRequest}, the app can call
     * {@link IsolatedService#getFederatedComputeScheduler} to pass the scheduler when constructing
     * the {@link IsolatedWorker}.
     *
     * @param params parameters related to job scheduling.
     * @param input the configuration of the federated computation. It should be consistent with the
     *     federated compute server setup.
     */
    @WorkerThread
    public void schedule(@NonNull Params params, @NonNull FederatedComputeInput input) {
        if (mFcService == null) {
            throw new IllegalStateException(
                    "FederatedComputeScheduler not available for this instance.");
        }
        final long startTimeMillis = System.currentTimeMillis();
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName(input.getPopulationName())
                        .setTrainingInterval(convertTrainingInterval(params.getTrainingInterval()))
                        .build();

        CountDownLatch latch = new CountDownLatch(1);
        final int[] err = {0};
        int responseCode = Constants.STATUS_INTERNAL_ERROR;
        try {
            mFcService.schedule(
                    trainingOptions,
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(int i) {
                            err[0] = i;
                            latch.countDown();
                        }
                    });

            boolean countedDown =
                    latch.await(FEDERATED_COMPUTE_SCHEDULE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (err[0] != 0) {
                sLogger.e(
                        TAG + " : Internal failure occurred while scheduling job, error code %d",
                        err[0]);
                responseCode = Constants.STATUS_INTERNAL_ERROR;
                return;
            } else if (!countedDown) {
                sLogger.d(TAG + " : timed out waiting for schedule operation to complete.");
                responseCode = Constants.STATUS_INTERNAL_ERROR;
                return;
            }
            responseCode = Constants.STATUS_SUCCESS;
        } catch (RemoteException | InterruptedException e) {
            sLogger.e(TAG + ": Failed to schedule federated compute job", e);
            throw new IllegalStateException(e);
        } finally {
            logApiCallStats(
                    Constants.API_NAME_FEDERATED_COMPUTE_SCHEDULE,
                    System.currentTimeMillis() - startTimeMillis,
                    responseCode);
        }
    }

    /**
     * Schedules a federated compute job. In {@link IsolatedService#onRequest}, the app can call
     * {@link IsolatedService#getFederatedComputeScheduler} to pass the scheduler when constructing
     * the {@link IsolatedWorker}.
     *
     * @param federatedComputeScheduleRequest input parameters related to job scheduling.
     * @param outcomeReceiver This either returns a {@link FederatedComputeScheduleResponse} on
     *     success, or {@link Exception} on failure. The exception type is {@link
     *     OnDevicePersonalizationException} with error code {@link
     *     OnDevicePersonalizationException#ERROR_INVALID_TRAINING_MANIFEST} if the manifest is
     *     missing the federated compute server URL or {@link
     *     OnDevicePersonalizationException#ERROR_SCHEDULE_TRAINING_FAILED} when scheduling fails
     *     for other reasons.
     * @hide
     */
    @WorkerThread
    public void schedule(
            @NonNull FederatedComputeScheduleRequest federatedComputeScheduleRequest,
            @NonNull OutcomeReceiver<FederatedComputeScheduleResponse, Exception> outcomeReceiver) {
        if (mFcService == null) {
            outcomeReceiver.onError(
                    new IllegalStateException(
                            "FederatedComputeScheduler not available for this instance."));
        }

        final long startTimeMillis = System.currentTimeMillis();
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder()
                        .setPopulationName(federatedComputeScheduleRequest.getPopulationName())
                        .setTrainingInterval(
                                convertTrainingInterval(
                                        federatedComputeScheduleRequest
                                                .getParams()
                                                .getTrainingInterval()))
                        .build();
        try {
            mFcService.schedule(
                    trainingOptions,
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            logApiCallStats(
                                    Constants.API_NAME_FEDERATED_COMPUTE_SCHEDULE,
                                    System.currentTimeMillis() - startTimeMillis,
                                    Constants.STATUS_SUCCESS);
                            outcomeReceiver.onResult(new FederatedComputeScheduleResponse());
                        }

                        @Override
                        public void onFailure(int i) {
                            logApiCallStats(
                                    Constants.API_NAME_FEDERATED_COMPUTE_SCHEDULE,
                                    System.currentTimeMillis() - startTimeMillis,
                                    Constants.STATUS_INTERNAL_ERROR);
                            outcomeReceiver.onError(
                                    new OnDevicePersonalizationException(translateErrorCode(i)));
                        }
                    });
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Failed to schedule federated compute job", e);
            outcomeReceiver.onError(e);
        }
    }

    /**
     * Translate the failed error code from the {@link IFederatedComputeService} to appropriate API
     * surface error code.
     */
    private static int translateErrorCode(int i) {
        // Currently there are just two types of failures within the schedule call, generic failure
        // and invalid/missing manifest.
        return i == Constants.STATUS_FCP_MANIFEST_INVALID
                ? OnDevicePersonalizationException.ERROR_INVALID_TRAINING_MANIFEST
                : OnDevicePersonalizationException.ERROR_SCHEDULE_TRAINING_FAILED;
    }

    /**
     * Cancels a federated compute job with input training params. In {@link
     * IsolatedService#onRequest}, the app can call {@link
     * IsolatedService#getFederatedComputeScheduler} to pass scheduler when constructing the {@link
     * IsolatedWorker}.
     *
     * @param input the configuration of the federated compute. It should be consistent with the
     *     federated compute server setup.
     */
    @WorkerThread
    public void cancel(@NonNull FederatedComputeInput input) {
        final long startTimeMillis = System.currentTimeMillis();
        int responseCode = Constants.STATUS_INTERNAL_ERROR;
        if (mFcService == null) {
            throw new IllegalStateException(
                    "FederatedComputeScheduler not available for this instance.");
        }
        CountDownLatch latch = new CountDownLatch(1);
        final int[] err = {0};
        try {
            mFcService.cancel(
                    input.getPopulationName(),
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(int i) {
                            err[0] = i;
                            latch.countDown();
                        }
                    });
            boolean countedDown =
                    latch.await(FEDERATED_COMPUTE_SCHEDULE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (err[0] != 0) {
                sLogger.e("Internal failure occurred while cancelling job, error code %d", err[0]);
                responseCode = Constants.STATUS_INTERNAL_ERROR;
                // Fail silently for now. TODO(b/346827691): update schedule/cancel API to return
                // error status to caller.
                return;
            } else if (!countedDown) {
                sLogger.d(TAG + " : timed out waiting for cancel operation to complete.");
                responseCode = Constants.STATUS_INTERNAL_ERROR;
                return;
            }
            responseCode = Constants.STATUS_SUCCESS;
        } catch (RemoteException | InterruptedException e) {
            sLogger.e(TAG + ": Failed to cancel federated compute job", e);
            throw new IllegalStateException(e);
        } finally {
            logApiCallStats(
                    Constants.API_NAME_FEDERATED_COMPUTE_CANCEL,
                    System.currentTimeMillis() - startTimeMillis,
                    responseCode);
        }
    }

    private static android.federatedcompute.common.TrainingInterval convertTrainingInterval(
            TrainingInterval interval) {
        return new android.federatedcompute.common.TrainingInterval.Builder()
                .setMinimumIntervalMillis(interval.getMinimumInterval().toMillis())
                .setSchedulingMode(convertSchedulingMode(interval))
                .build();
    }

    private static @android.federatedcompute.common.TrainingInterval.SchedulingMode int
            convertSchedulingMode(TrainingInterval interval) {
        switch (interval.getSchedulingMode()) {
            case TrainingInterval.SCHEDULING_MODE_ONE_TIME:
                return android.federatedcompute.common.TrainingInterval.SCHEDULING_MODE_ONE_TIME;
            case TrainingInterval.SCHEDULING_MODE_RECURRENT:
                return android.federatedcompute.common.TrainingInterval.SCHEDULING_MODE_RECURRENT;
            default:
                throw new IllegalStateException(
                        "Unsupported scheduling mode " + interval.getSchedulingMode());
        }
    }

    /** Helper method to log call stats based on response code. */
    private void logApiCallStats(int apiName, long duration, int responseCode) {
        try {
            mDataAccessService.logApiCallStats(apiName, duration, responseCode);
        } catch (Exception e) {
            sLogger.d(e, TAG + ": failed to log metrics");
        }
    }

    /** The parameters related to job scheduling. */
    public static class Params {
        /**
         * If training interval is scheduled for recurrent tasks, the earliest time this task could
         * start is after the minimum training interval expires. E.g. If the task is set to run
         * maximum once per day, the first run of this task will be one day after this task is
         * scheduled. When a one time job is scheduled, the earliest next runtime is calculated
         * based on federated compute default interval.
         */
        @NonNull private final TrainingInterval mTrainingInterval;

        public Params(@NonNull TrainingInterval trainingInterval) {
            mTrainingInterval = trainingInterval;
        }

        @NonNull
        public TrainingInterval getTrainingInterval() {
            return mTrainingInterval;
        }
    }
}
