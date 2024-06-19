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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLIENT_PLAN_SPEC_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FILE_DESCRIPTOR_CLOSE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ISOLATED_TRAINING_PROCESS_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;
import static com.android.federatedcompute.services.common.Constants.CLIENT_ONLY_PLAN_FILE_NAME;
import static com.android.federatedcompute.services.common.Constants.ISOLATED_TRAINING_SERVICE_NAME;
import static com.android.federatedcompute.services.common.Constants.TRACE_WORKER_RUN_FL_COMPUTATION;
import static com.android.federatedcompute.services.common.Constants.TRACE_WORKER_START_TRAINING_RUN;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getBackgroundExecutor;
import static com.android.federatedcompute.services.common.FederatedComputeExecutors.getLightweightExecutor;
import static com.android.federatedcompute.services.common.FileUtils.createTempFile;
import static com.android.federatedcompute.services.common.FileUtils.createTempFileDescriptor;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_STARTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_NOT_CONFIGURED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RUN_COMPLETE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.Trace;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.ExampleStats;
import com.android.federatedcompute.services.common.FileUtils;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingFlags;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;
import com.android.federatedcompute.services.encryption.FederatedComputeEncryptionKeyManager;
import com.android.federatedcompute.services.encryption.HpkeJniEncrypter;
import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder;
import com.android.federatedcompute.services.examplestore.ExampleStoreServiceProvider;
import com.android.federatedcompute.services.http.CheckinResult;
import com.android.federatedcompute.services.http.HttpFederatedProtocol;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;
import com.android.federatedcompute.services.security.AuthorizationContext;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.federatedcompute.services.training.aidl.IIsolatedTrainingService;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;
import com.android.federatedcompute.services.training.util.ComputationResult;
import com.android.federatedcompute.services.training.util.EligibilityResult;
import com.android.federatedcompute.services.training.util.ListenableSupplier;
import com.android.federatedcompute.services.training.util.TrainingConditionsChecker;
import com.android.federatedcompute.services.training.util.TrainingConditionsChecker.Condition;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;
import com.android.odp.module.common.PackageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.RetryInfo;
import com.google.intelligence.fcp.client.engine.TaskRetry;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.internal.federatedcompute.v1.RejectionInfo;
import com.google.internal.federatedcompute.v1.RetryWindow;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** The worker to execute federated computation jobs. */
public class FederatedComputeWorker {
    private static final String TAG = FederatedComputeWorker.class.getSimpleName();
    private static final int NUM_ACTIVE_KEYS_TO_CHOOSE_FROM = 5;
    private static volatile FederatedComputeWorker sWorker;
    private final Object mLock = new Object();
    private final AtomicBoolean mInterruptFlag = new AtomicBoolean(false);
    private final ListenableSupplier<Boolean> mInterruptSupplier =
            new ListenableSupplier<>(mInterruptFlag::get);
    private final Context mContext;
    @Nullable private final FederatedComputeJobManager mJobManager;
    @Nullable private final TrainingConditionsChecker mTrainingConditionsChecker;
    private final ComputationRunner mComputationRunner;
    private final ResultCallbackHelper mResultCallbackHelper;
    @NonNull private final Injector mInjector;

    @GuardedBy("mLock")
    @Nullable
    private TrainingRun mActiveRun = null;

    private HttpFederatedProtocol mHttpFederatedProtocol;
    private ExampleStoreServiceProvider mExampleStoreServiceProvider;
    private AbstractServiceBinder<IIsolatedTrainingService> mIsolatedTrainingServiceBinder;
    private FederatedComputeEncryptionKeyManager mEncryptionKeyManager;

    @VisibleForTesting
    public FederatedComputeWorker(
            Context context,
            FederatedComputeJobManager jobManager,
            TrainingConditionsChecker trainingConditionsChecker,
            ComputationRunner computationRunner,
            ResultCallbackHelper resultCallbackHelper,
            FederatedComputeEncryptionKeyManager keyManager,
            ExampleStoreServiceProvider exampleStoreServiceProvider,
            Injector injector) {
        this.mContext = context.getApplicationContext();
        this.mJobManager = jobManager;
        this.mTrainingConditionsChecker = trainingConditionsChecker;
        this.mComputationRunner = computationRunner;
        this.mResultCallbackHelper = resultCallbackHelper;
        this.mEncryptionKeyManager = keyManager;
        this.mInjector = injector;
        this.mExampleStoreServiceProvider = exampleStoreServiceProvider;
    }

    /** Gets an instance of {@link FederatedComputeWorker}. */
    @NonNull
    public static FederatedComputeWorker getInstance(Context context) {
        if (sWorker == null) {
            synchronized (FederatedComputeWorker.class) {
                if (sWorker == null) {
                    sWorker =
                            new FederatedComputeWorker(
                                    context,
                                    FederatedComputeJobManager.getInstance(context),
                                    TrainingConditionsChecker.getInstance(context),
                                    new ComputationRunner(context),
                                    new ResultCallbackHelper(context),
                                    FederatedComputeEncryptionKeyManager.getInstance(context),
                                    new ExampleStoreServiceProvider(),
                                    new Injector());
                }
            }
        }
        return sWorker;
    }

    /** Starts a training run with the given job Id. */
    public ListenableFuture<FLRunnerResult> startTrainingRun(
            int jobId, FederatedJobService.OnJobFinishedCallback callback) {
        LogUtil.d(TAG, "startTrainingRun() %d", jobId);
        TrainingEventLogger trainingEventLogger = mInjector.getTrainingEventLogger();
        trainingEventLogger.setClientVersion(PackageUtils.getApexVersion(this.mContext));
        return FluentFuture.from(
                        mInjector
                                .getBgExecutor()
                                .submit(
                                        () ->
                                                getTrainableTask(
                                                        jobId, trainingEventLogger, callback)))
                .transformAsync(
                        task -> {
                            if (task == null) {
                                return Futures.immediateFuture(null);
                            }
                            return startTrainingRun(jobId, task, trainingEventLogger, callback);
                        },
                        mInjector.getBgExecutor());
    }

    private ListenableFuture<FLRunnerResult> startTrainingRun(
            int jobId,
            FederatedTrainingTask trainingTask,
            TrainingEventLogger trainingEventLogger,
            FederatedJobService.OnJobFinishedCallback callback) {
        synchronized (mLock) {
            // Only allows one concurrent job running.
            Trace.beginAsyncSection(TRACE_WORKER_START_TRAINING_RUN, jobId);
            TrainingRun run = new TrainingRun(jobId, trainingTask, trainingEventLogger, callback);
            mActiveRun = run;
            long startTimeMs = SystemClock.elapsedRealtime();
            ListenableFuture<FLRunnerResult> runCompletedFuture = doTraining(run);
            var unused =
                    Futures.whenAllComplete(runCompletedFuture)
                            .call(
                                    () -> {
                                        long duration = SystemClock.elapsedRealtime() - startTimeMs;
                                        run.mTrainingEventLogger.logEventWithDuration(
                                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RUN_COMPLETE,
                                                duration);
                                        unBindServicesIfNecessary(run);
                                        Trace.endAsyncSection(
                                                TRACE_WORKER_START_TRAINING_RUN, jobId);
                                        return null;
                                    },
                                    mInjector.getBgExecutor());
            run.mFuture = runCompletedFuture;
            return runCompletedFuture;
        }
    }

    @Nullable
    private FederatedTrainingTask getTrainableTask(
            int jobId,
            TrainingEventLogger trainingEventLogger,
            FederatedJobService.OnJobFinishedCallback callback) {
        FederatedTrainingTask trainingTask = mJobManager.onTrainingStarted(jobId);
        if (trainingTask == null) {
            LogUtil.i(TAG, "Could not find task to run for job ID %s", jobId);
            callback.callJobFinished(/* isSuccessful= */ false);
            return null;
        }
        trainingEventLogger.setPopulationName(trainingTask.populationName());
        String taskCallingPackageName = trainingTask.ownerPackageName();
        if (taskCallingPackageName != null) {
            trainingEventLogger.setSdkPackageName(taskCallingPackageName);
        }
        if (!checkTrainingConditions(trainingTask.getTrainingConstraints())) {
            trainingEventLogger.logTaskNotStarted();
            performFinishRoutines(
                    callback,
                    ContributionResult.FAIL,
                    jobId,
                    trainingTask.populationName(),
                    trainingTask.getTrainingIntervalOptions(),
                    /* taskRetry= */ null,
                    false);
            LogUtil.i(TAG, "Training conditions not satisfied (before bindService)!");
            return null;
        }
        synchronized (mLock) {
            // Only allows one concurrent job running.
            if (mActiveRun != null) {
                LogUtil.i(
                        TAG,
                        "Delaying %d/%s another run is already active!",
                        jobId,
                        trainingTask.populationName());
                performFinishRoutines(
                        callback,
                        ContributionResult.FAIL,
                        jobId,
                        trainingTask.populationName(),
                        trainingTask.getTrainingIntervalOptions(),
                        /* taskRetry= */ null,
                        false);
                return null;
            }
            return trainingTask;
        }
    }

    private ListenableFuture<FLRunnerResult> doTraining(TrainingRun run) {
        try {
            // 1. Communicate with remote federated compute server to start task assignment and
            // download client plan and initial model checkpoint. Note: use bLocking executors for
            // http requests.
            mHttpFederatedProtocol =
                    mInjector.getHttpFederatedProtocol(
                            run.mTask.serverAddress(),
                            PackageUtils.getApexVersion(mContext),
                            run.mTask.populationName(),
                            run.mTrainingEventLogger);
            // By default, the 401 (UNAUTHENTICATED) response is allowed. When receiving 401
            // (UNAUTHENTICATED). The second would not allow 401 (UNAUTHENTICATED).
            AuthorizationContext authContext =
                    mInjector.createAuthContext(
                            mContext,
                            ComponentName.createRelative(
                                            run.mTask.ownerPackageName(),
                                            run.mTask.ownerClassName())
                                    .flattenToString(),
                            run.mTask.ownerIdCertDigest());
            return FluentFuture.from(mHttpFederatedProtocol.createTaskAssignment(authContext))
                    .transformAsync(
                            taskAssignmentResponse -> {
                                if (taskAssignmentResponse.hasRejectionInfo()) {
                                    LogUtil.d(
                                            TAG,
                                            "job %d was rejected during check in, reason %s",
                                            run.mTask.jobId(),
                                            taskAssignmentResponse.getRejectionInfo().getReason());
                                    if (taskAssignmentResponse
                                            .getRejectionInfo()
                                            .hasAuthMetadata()) {
                                        return handleUnauthenticatedRejection(
                                                run, taskAssignmentResponse, authContext);
                                    } else if (taskAssignmentResponse
                                            .getRejectionInfo()
                                            .hasRetryWindow()) {
                                        return handleRetryRejection(
                                                run, taskAssignmentResponse, false);
                                    }
                                    return Futures.immediateFailedFuture(
                                            new IllegalStateException(
                                                    "Unknown rejection Info from FCP server"));
                                } else {
                                    return runEligibilityCheckAndDoFlTraining(
                                            taskAssignmentResponse, run);
                                }
                            },
                            mInjector.getBgExecutor());
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @NonNull
    private ListenableFuture<FLRunnerResult> handleUnauthenticatedRejection(
            TrainingRun run,
            CreateTaskAssignmentResponse createTaskAssignmentResponse,
            AuthorizationContext authContext) {
        // Generate attestation record and make 2nd try.
        authContext.updateAuthState(
                createTaskAssignmentResponse.getRejectionInfo().getAuthMetadata(),
                run.mTrainingEventLogger);
        return FluentFuture.from(mHttpFederatedProtocol.createTaskAssignment(authContext))
                .transformAsync(
                        taskAssignmentOnUnauthenticated -> {
                            if (taskAssignmentOnUnauthenticated.hasRejectionInfo()) {
                                // This function is called only when the device received
                                // 401 (unauthenticated). Only retry rejection is allowed.
                                if (taskAssignmentOnUnauthenticated
                                        .getRejectionInfo()
                                        .hasRetryWindow()) {
                                    return handleRetryRejection(
                                            run, taskAssignmentOnUnauthenticated, true);
                                } else {
                                    // TODO: b/322880077 Cancel job when it fails authentication
                                    return Futures.immediateFailedFuture(
                                            new IllegalStateException(
                                                    "Unknown rejection Info from FCP server when "
                                                            + "solving authentication challenge"));
                                }
                            } else {
                                return runEligibilityCheckAndDoFlTraining(
                                        taskAssignmentOnUnauthenticated, run);
                            }
                        },
                        mInjector.getBgExecutor());
    }

    @NonNull
    private ListenableFuture<FLRunnerResult> handleRetryRejection(
            TrainingRun run,
            CreateTaskAssignmentResponse taskAssignmentResponse,
            boolean enableFailuresTracking) {
        performFinishRoutines(
                run.mCallback,
                ContributionResult.FAIL,
                run.mTask.jobId(),
                run.mTask.populationName(),
                run.mTask.getTrainingIntervalOptions(),
                buildTaskRetry(taskAssignmentResponse.getRejectionInfo()),
                enableFailuresTracking);
        return Futures.immediateFuture(null);
    }

    private ListenableFuture<FLRunnerResult> runEligibilityCheckAndDoFlTraining(
            CreateTaskAssignmentResponse createTaskAssignmentResponse, TrainingRun run) {
        String taskId = createTaskAssignmentResponse.getTaskAssignment().getTaskId();
        Preconditions.checkArgument(!taskId.isEmpty(), "Task id should not be empty");
        synchronized (mLock) {
            mActiveRun.mTaskId = taskId;
        }

        // 2. Execute eligibility task if applicable.
        if (!createTaskAssignmentResponse.getTaskAssignment().hasEligibilityTaskInfo()) {
            run.mTrainingEventLogger.logEventKind(
                    FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_ELIGIBILITY_EVAL_NOT_CONFIGURED);
        }
        EligibilityResult eligibleResult;
        if (createTaskAssignmentResponse.getTaskAssignment().hasEligibilityTaskInfo()
                && mInjector.isEligibilityTaskEnabled()) {
            eligibleResult = checkEligibility(createTaskAssignmentResponse, run);
            // If device is not eligible to execute task, report failure result to server.
            if (!eligibleResult.isEligible()) {
                reportFailureResultToServer(
                        new ComputationResult(
                                null,
                                FLRunnerResult.newBuilder()
                                        .setContributionResult(ContributionResult.FAIL)
                                        .setErrorStatus(FLRunnerResult.ErrorStatus.NOT_ELIGIBLE)
                                        .build(),
                                null),
                        AuthorizationContext.create(
                                mContext,
                                ComponentName.createRelative(
                                                run.mTask.ownerPackageName(),
                                                run.mTask.ownerClassName())
                                        .flattenToString(),
                                run.mTask.ownerIdCertDigest()),
                        run.mTrainingEventLogger);
                // Reschedule the job.
                performFinishRoutines(
                        run.mCallback,
                        ContributionResult.FAIL,
                        run.mTask.jobId(),
                        run.mTask.populationName(),
                        run.mTask.getTrainingIntervalOptions(),
                        /* taskRetry= */ null,
                        /* enableFailuresTracking= */ false);
                return Futures.immediateFuture(null);
            }
        } else {
            eligibleResult = null;
        }

        return FluentFuture.from(
                        mHttpFederatedProtocol.downloadTaskAssignment(
                                createTaskAssignmentResponse.getTaskAssignment()))
                .transformAsync(
                        checkinResult -> {
                            if (checkinResult == null) {
                                LogUtil.w(TAG, "Failed to acquire checkin result!");
                                // Reschedule the job.
                                performFinishRoutines(
                                        run.mCallback,
                                        ContributionResult.FAIL,
                                        run.mTask.jobId(),
                                        run.mTask.populationName(),
                                        run.mTask.getTrainingIntervalOptions(),
                                        /* taskRetry= */ null,
                                        /* enableFailuresTracking= */ true);
                                return Futures.immediateFuture(null);
                            }
                            return doFederatedComputation(run, checkinResult, eligibleResult);
                        },
                        getBackgroundExecutor());
    }

    private EligibilityResult checkEligibility(
            CreateTaskAssignmentResponse createTaskAssignmentResponse, TrainingRun run) {
        TaskAssignment taskAssignment = createTaskAssignmentResponse.getTaskAssignment();
        LogUtil.d(
                TAG,
                "start eligibility task %s %s ",
                run.mTask.populationName(),
                taskAssignment.getTaskId());
        EligibilityDecider eligibilityDecider = mInjector.getEligibilityDecider(this.mContext);
        EligibilityResult eligibleResult =
                eligibilityDecider.computeEligibility(
                        run.mTask,
                        taskAssignment.getTaskId(),
                        taskAssignment.getEligibilityTaskInfo(),
                        this.mContext,
                        run.mTrainingEventLogger,
                        taskAssignment.getExampleSelector());
        LogUtil.d(
                TAG,
                "eligibility task result %s %b",
                taskAssignment.getTaskId(),
                eligibleResult.isEligible());
        return eligibleResult;
    }

    @NonNull
    private ListenableFuture<FLRunnerResult> doFederatedComputation(
            TrainingRun run, CheckinResult checkinResult, EligibilityResult eligibilityResult) {
        // 3. Fetch Active keys to encrypt the computation result.
        List<FederatedComputeEncryptionKey> activeKeys =
                mEncryptionKeyManager.getOrFetchActiveKeys(
                        FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION,
                        NUM_ACTIVE_KEYS_TO_CHOOSE_FROM);
        // select a random key
        FederatedComputeEncryptionKey encryptionKey =
                activeKeys.isEmpty()
                        ? null
                        : activeKeys.get(new Random().nextInt(activeKeys.size()));
        if (encryptionKey == null) {
            // no active keys to encrypt the FL/FA computation results, stop the computation run.
            reportFailureResultToServer(run);
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No active key available on device."));
        }

        // 4. Bind to client app implemented ExampleStoreService based on ExampleSelector if we
        // didn't get ExampleIterator from eligibility task (not configured).
        IExampleStoreIterator iterator;
        if (eligibilityResult != null && eligibilityResult.getExampleStoreIterator() != null) {
            iterator = eligibilityResult.getExampleStoreIterator();
        } else {
            iterator =
                    getExampleStoreIterator(
                            run, checkinResult.getTaskAssignment().getExampleSelector());
        }

        if (iterator == null) {
            reportFailureResultToServer(run);
            return Futures.immediateFailedFuture(
                    new IllegalStateException(
                            String.format(
                                    "Can't get ExampleIterator for %s %s.",
                                    run.mTask.populationName(), run.mTaskId)));
        }

        // 5. Run federated learning or federated analytic depends on task type. Federated
        // learning job will start a new isolated process to run TFLite training.
        ListenableFuture<ComputationResult> computationResultFuture =
                runFederatedComputation(checkinResult, run, iterator);

        // Report failure to server if computation failed with any exception.
        ListenableFuture<ComputationResult> computationResultAndCallbackFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            String ownerId =
                                    ComponentName.createRelative(
                                                    run.mTask.ownerPackageName(),
                                                    run.mTask.ownerClassName())
                                            .flattenToString();
                            Futures.addCallback(
                                    computationResultFuture,
                                    new ReportFailureToServerCallback(run.mTrainingEventLogger)
                                            .getServerFailureReportCallback(
                                                    completer,
                                                    AuthorizationContext.create(
                                                            mContext,
                                                            ownerId,
                                                            run.mTask.ownerIdCertDigest())),
                                    getLightweightExecutor());
                            return "Report computation result failure to the server.";
                        });

        // 6. Report computation result to federated compute server.
        ListenableFuture<RejectionInfo> reportToServerFuture =
                Futures.transformAsync(
                        computationResultAndCallbackFuture,
                        result -> {
                            String ownerId =
                                    ComponentName.createRelative(
                                                    run.mTask.ownerPackageName(),
                                                    run.mTask.ownerClassName())
                                            .flattenToString();
                            return reportResultWithAuthentication(
                                    result,
                                    encryptionKey,
                                    mInjector.createAuthContext(
                                            mContext, ownerId, run.mTask.ownerIdCertDigest()),
                                    run.mTrainingEventLogger);
                        },
                        getLightweightExecutor());

        return Futures.whenAllSucceed(reportToServerFuture, computationResultAndCallbackFuture)
                .call(
                        () -> {
                            ComputationResult computationResult =
                                    Futures.getDone(computationResultFuture);
                            RejectionInfo reportToServer = Futures.getDone(reportToServerFuture);
                            // report to Server will hold null in case of success, or rejection info
                            // in case server answered with rejection
                            if (reportToServer != null) {
                                ComputationResult failedReportComputationResult =
                                        new ComputationResult(
                                                null,
                                                FLRunnerResult.newBuilder()
                                                        .mergeFrom(
                                                                computationResult
                                                                        .getFlRunnerResult())
                                                        .setContributionResult(
                                                                ContributionResult.FAIL)
                                                        .build(),
                                                null);
                                var unused =
                                        mResultCallbackHelper.callHandleResult(
                                                run.mTaskId,
                                                run.mTask,
                                                failedReportComputationResult);
                                performFinishRoutines(
                                        run.mCallback,
                                        ContributionResult.FAIL,
                                        run.mTask.jobId(),
                                        run.mTask.populationName(),
                                        run.mTask.getTrainingIntervalOptions(),
                                        buildTaskRetry(reportToServer),
                                        /* enableFailuresTracking= */ false);
                                return null;
                            }

                            // 7. store success contribution in TaskHistory table. It will be used
                            // in evaluation eligibility task.
                            if (computationResult.getFlRunnerResult().getContributionResult()
                                    == ContributionResult.SUCCESS) {
                                mJobManager.recordSuccessContribution(
                                        run.mJobId,
                                        run.mTask.populationName(),
                                        checkinResult.getTaskAssignment());
                            }

                            // 8. Publish computation result and consumed
                            // examples to client implemented
                            // ResultHandlingService.
                            var unused =
                                    mResultCallbackHelper.callHandleResult(
                                            run.mTaskId, run.mTask, computationResult);
                            return computationResult.getFlRunnerResult();
                        },
                        mInjector.getBgExecutor());
    }

    private void reportFailureResultToServer(TrainingRun run) {
        ComputationResult failedComputationResult =
                new ComputationResult(
                        null,
                        FLRunnerResult.newBuilder()
                                .setContributionResult(ContributionResult.FAIL)
                                .build(),
                        null);
        try {
            reportFailureResultToServer(
                    failedComputationResult,
                    AuthorizationContext.create(
                            mContext,
                            ComponentName.createRelative(
                                            run.mTask.ownerPackageName(),
                                            run.mTask.ownerClassName())
                                    .flattenToString(),
                            run.mTask.ownerIdCertDigest()),
                    run.mTrainingEventLogger);
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Failed to report failure result to server.");
        }
    }

    private static TaskRetry buildTaskRetry(RejectionInfo rejectionInfo) {
        TaskRetry.Builder taskRetryBuilder = TaskRetry.newBuilder();
        if (rejectionInfo.hasRetryWindow()) {
            RetryWindow retryWindow = rejectionInfo.getRetryWindow();
            Duration delayMin = retryWindow.getDelayMin();
            // convert rejection info seconds and nanoseconds to
            // retry milliseconds
            taskRetryBuilder.setDelayMin(
                    delayMin.getSeconds() * 1000 + delayMin.getNanos() / 1000000);
            Duration delayMax = retryWindow.getDelayMax();
            taskRetryBuilder.setDelayMax(
                    delayMax.getSeconds() * 1000 + delayMax.getNanos() / 1000000);
        }
        return taskRetryBuilder.build();
    }

    private IExampleStoreIterator getExampleStoreIterator(
            TrainingRun run, ExampleSelector exampleSelector) {
        try {
            long startTimeNanos = SystemClock.elapsedRealtimeNanos();
            IExampleStoreService exampleStoreService =
                    mExampleStoreServiceProvider.getExampleStoreService(
                            run.mTask.appPackageName(), mContext);
            if (exampleStoreService == null) {
                run.mTrainingEventLogger.logComputationExampleIteratorError(new ExampleStats());
                return null;
            }
            run.mExampleStats.mBindToExampleStoreLatencyNanos.addAndGet(
                    SystemClock.elapsedRealtimeNanos() - startTimeNanos);
            run.mExampleStoreService = exampleStoreService;
            startTimeNanos = SystemClock.elapsedRealtimeNanos();

            IExampleStoreIterator iterator =
                    mExampleStoreServiceProvider.getExampleIterator(
                            run.mExampleStoreService, run.mTask, run.mTaskId, 0, exampleSelector);
            run.mExampleStats.mStartQueryLatencyNanos.addAndGet(
                    SystemClock.elapsedRealtimeNanos() - startTimeNanos);
            return iterator;
        } catch (Exception e) {
            run.mTrainingEventLogger.logComputationExampleIteratorError(new ExampleStats());
            LogUtil.e(TAG, "StartQuery failure: " + e.getMessage());
            return null;
        }
    }

    /**
     * Completes the running job , schedule recurrent job, and unbind from ExampleStoreService and
     * ResultHandlingService etc.
     */
    public void finish(FLRunnerResult flRunnerResult) {
        TaskRetry taskRetry = null;
        ContributionResult contributionResult = ContributionResult.UNSPECIFIED;
        if (flRunnerResult != null) {
            contributionResult = flRunnerResult.getContributionResult();
            if (flRunnerResult.hasRetryInfo()) {
                RetryInfo retryInfo = flRunnerResult.getRetryInfo();
                long delay = retryInfo.getMinimumDelay().getSeconds() * 1000L;
                taskRetry =
                        TaskRetry.newBuilder()
                                .setRetryToken(retryInfo.getRetryToken())
                                .setDelayMin(delay)
                                .setDelayMax(delay)
                                .build();
                LogUtil.i(TAG, "Finished with task retry= %s", taskRetry);
            }
        }
        finish(taskRetry, contributionResult, true);
    }

    /**
     * Cancel the current running job, schedule recurrent job, unbind from ExampleStoreService and
     * ResultHandlingService etc.
     */
    public void finish(
            TaskRetry taskRetry, ContributionResult contributionResult, boolean cancelFuture) {
        TrainingRun runToFinish;
        synchronized (mLock) {
            if (mActiveRun == null) {
                return;
            }

            runToFinish = mActiveRun;
            mActiveRun = null;
            if (cancelFuture) {
                runToFinish.mFuture.cancel(true);
            }
        }

        performFinishRoutines(
                runToFinish.mCallback,
                contributionResult,
                runToFinish.mJobId,
                runToFinish.mTask.populationName(),
                runToFinish.mTask.getTrainingIntervalOptions(),
                taskRetry);
    }

    /** To clean up active run for subsequent executions. */
    public void cleanUpActiveRun() {
        synchronized (mLock) {
            if (mActiveRun == null) {
                return;
            }

            mActiveRun = null;
        }
    }

    private void performFinishRoutines(
            FederatedJobService.OnJobFinishedCallback callback,
            ContributionResult contributionResult,
            int jobId,
            String populationName,
            TrainingIntervalOptions trainingIntervalOptions,
            TaskRetry taskRetry) {
        performFinishRoutines(
                callback,
                contributionResult,
                jobId,
                populationName,
                trainingIntervalOptions,
                taskRetry,
                /* enableFailuresTracking= */ true);
    }

    private void performFinishRoutines(
            FederatedJobService.OnJobFinishedCallback callback,
            ContributionResult contributionResult,
            int jobId,
            String populationName,
            TrainingIntervalOptions trainingIntervalOptions,
            TaskRetry taskRetry,
            boolean enableFailuresTracking) {
        callback.callJobFinished(ContributionResult.SUCCESS.equals(contributionResult));
        mJobManager.onTrainingCompleted(
                jobId,
                populationName,
                trainingIntervalOptions,
                taskRetry,
                contributionResult,
                enableFailuresTracking);
    }

    private void unBindServicesIfNecessary(TrainingRun runToFinish) {
        if (runToFinish.mIsolatedTrainingService != null) {
            LogUtil.i(TAG, "Unbinding from IsolatedTrainingService");
            unbindFromIsolatedTrainingService();
            runToFinish.mIsolatedTrainingService = null;
        }
        if (runToFinish.mExampleStoreService != null) {
            LogUtil.i(TAG, "Unbinding from ExampleStoreService");
            mExampleStoreServiceProvider.unbindFromExampleStoreService();
            runToFinish.mExampleStoreService = null;
        }
    }

    private boolean checkTrainingConditions(TrainingConstraints constraints) {
        Set<Condition> conditions =
                mTrainingConditionsChecker.checkAllConditionsForFlTraining(constraints);
        for (Condition condition : conditions) {
            switch (condition) {
                case THERMALS_NOT_OK:
                    LogUtil.e(TAG, "training job service interrupt thermals not ok");
                    break;
                case BATTERY_NOT_OK:
                    LogUtil.e(TAG, "training job service interrupt battery not ok");
                    break;
                case MEM_NOT_OK:
                    LogUtil.e(TAG, "training job service interrupt memory space not ok");
                    break;
            }
        }
        return conditions.isEmpty();
    }

    @VisibleForTesting
    ListenableFuture<ComputationResult> runFlComputation(
            TrainingRun run,
            CheckinResult checkinResult,
            String outputCheckpointFile,
            IExampleStoreIterator iterator) {
        Trace.beginAsyncSection(TRACE_WORKER_RUN_FL_COMPUTATION, 0);
        long startTimeMs = SystemClock.elapsedRealtime();
        ParcelFileDescriptor outputCheckpointFd =
                createTempFileDescriptor(
                        outputCheckpointFile, ParcelFileDescriptor.MODE_READ_WRITE);
        ParcelFileDescriptor inputCheckpointFd =
                createTempFileDescriptor(
                        checkinResult.getInputCheckpointFile(),
                        ParcelFileDescriptor.MODE_READ_ONLY);
        ExampleSelector exampleSelector = checkinResult.getTaskAssignment().getExampleSelector();
        ClientOnlyPlan clientPlan = checkinResult.getPlanData();
        if (clientPlan.getTfliteGraph().isEmpty()) {
            LogUtil.e(
                    TAG,
                    "ClientOnlyPlan input tflite graph is empty."
                            + " population name: %s, task name: %s",
                    run.mTask.populationName(),
                    run.mTaskId);
            IllegalStateException ex =
                    new IllegalStateException("Client plan input tflite graph is empty");
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            ex,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLIENT_PLAN_SPEC_ERROR,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
            return Futures.immediateFailedFuture(ex);
        }

        try {
            // Write ClientOnlyPlan to file and pass ParcelFileDescriptor to isolated process to
            // avoid TransactionTooLargeException through IPC.
            String clientOnlyPlanFile = createTempFile(CLIENT_ONLY_PLAN_FILE_NAME, ".pb");
            FileUtils.writeToFile(clientOnlyPlanFile, clientPlan.toByteArray());
            ParcelFileDescriptor clientPlanFd =
                    createTempFileDescriptor(
                            clientOnlyPlanFile, ParcelFileDescriptor.MODE_READ_ONLY);
            IIsolatedTrainingService trainingService = getIsolatedTrainingService();
            if (trainingService == null) {
                LogUtil.w(TAG, "Could not bind to IsolatedTrainingService");
                throw new IllegalStateException("Could not bind to IsolatedTrainingService");
            }
            run.mIsolatedTrainingService = trainingService;

            Bundle bundle = new Bundle();
            bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, exampleSelector.toByteArray());
            bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, run.mTask.populationName());
            bundle.putString(ClientConstants.EXTRA_TASK_ID, run.mTaskId);
            bundle.putParcelable(Constants.EXTRA_CLIENT_ONLY_PLAN_FD, clientPlanFd);
            bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, inputCheckpointFd);
            bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, outputCheckpointFd);
            bundle.putBinder(Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, iterator.asBinder());
            bundle.putByteArray(Constants.EXTRA_TRAINING_FLAGS, buildTrainingFlags());

            return FluentFuture.from(runIsolatedTrainingProcess(run, bundle))
                    .transform(
                            result -> {
                                ComputationResult computationResult =
                                        processIsolatedTrainingResult(
                                                outputCheckpointFile, result, run, startTimeMs);
                                // Close opened file descriptor.
                                try {
                                    if (outputCheckpointFd != null) {
                                        outputCheckpointFd.close();
                                    }
                                    if (inputCheckpointFd != null) {
                                        inputCheckpointFd.close();
                                    }
                                } catch (IOException e) {
                                    reportCelFileDescriptorClose(e);
                                    LogUtil.e(TAG, e, "Failed to close file descriptor");
                                } finally {
                                    // Unbind from IsolatedTrainingService.
                                    LogUtil.i(TAG, "Unbinding from IsolatedTrainingService");
                                    unbindFromIsolatedTrainingService();
                                    run.mIsolatedTrainingService = null;
                                }
                                Trace.endAsyncSection(TRACE_WORKER_RUN_FL_COMPUTATION, 0);
                                return computationResult;
                            },
                            getLightweightExecutor());
        } catch (Exception e) {
            // Close opened file descriptor.
            try {
                if (outputCheckpointFd != null) {
                    outputCheckpointFd.close();
                }
                if (inputCheckpointFd != null) {
                    inputCheckpointFd.close();
                }
            } catch (IOException t) {
                reportCelFileDescriptorClose(t);
                LogUtil.e(TAG, t, "Failed to close file descriptor");
            } finally {
                reportCelIsolatedTrainingProcess(e);
                // Unbind from IsolatedTrainingService.
                LogUtil.i(TAG, "Unbinding from IsolatedTrainingService");
                unbindFromIsolatedTrainingService();
                run.mIsolatedTrainingService = null;
            }
            return Futures.immediateFailedFuture(e);
        }
    }

    private static byte[] buildTrainingFlags() {
        Flags flags = FlagsFactory.getFlags();
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingFlags.createTrainingFlags(
                        builder,
                        flags.getFcpTfErrorRescheduleSeconds(),
                        flags.getEnableClientErrorLogging()));
        return builder.sizedByteArray();
    }

    private static void reportCelIsolatedTrainingProcess(Exception e) {
        ClientErrorLogger.getInstance()
                .logErrorWithExceptionInfo(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ISOLATED_TRAINING_PROCESS_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
    }

    private static void reportCelFileDescriptorClose(IOException e) {
        ClientErrorLogger.getInstance()
                .logErrorWithExceptionInfo(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FILE_DESCRIPTOR_CLOSE_ERROR,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
    }

    private ComputationResult processIsolatedTrainingResult(
            String outputCheckpoint, Bundle result, TrainingRun run, long startTimeMs) {
        byte[] resultBytes =
                Objects.requireNonNull(result.getByteArray(Constants.EXTRA_FL_RUNNER_RESULT));
        FLRunnerResult flRunnerResult;
        try {
            flRunnerResult = FLRunnerResult.parseFrom(resultBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(e);
        }
        logComputationResult(flRunnerResult, run, startTimeMs);
        if (flRunnerResult.getContributionResult() == ContributionResult.FAIL) {
            return new ComputationResult(outputCheckpoint, flRunnerResult, new ArrayList<>());
        }
        ArrayList<ExampleConsumption> exampleList =
                result.getParcelableArrayList(
                        ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, ExampleConsumption.class);
        if (exampleList == null || exampleList.isEmpty()) {
            throw new IllegalArgumentException("example consumption list should not be empty");
        }

        return new ComputationResult(outputCheckpoint, flRunnerResult, exampleList);
    }

    private void logComputationResult(FLRunnerResult result, TrainingRun run, long startTimeMs) {
        run.mExampleStats.mExampleCount.addAndGet(result.getExampleStats().getExampleCount());
        run.mExampleStats.mExampleSizeBytes.addAndGet(
                result.getExampleStats().getExampleSizeBytes());
        if (result.getContributionResult() == ContributionResult.SUCCESS) {
            run.mTrainingEventLogger.logComputationCompleted(
                    run.mExampleStats, SystemClock.elapsedRealtime() - startTimeMs);
            return;
        }
        switch (result.getErrorStatus()) {
            case INVALID_ARGUMENT:
                run.mTrainingEventLogger.logComputationInvalidArgument(run.mExampleStats);
                break;
            case TENSORFLOW_ERROR:
                run.mTrainingEventLogger.logComputationTensorflowError(run.mExampleStats);
                break;
            case EXAMPLE_ITERATOR_ERROR:
                run.mTrainingEventLogger.logComputationExampleIteratorError(run.mExampleStats);
                break;
            default:
                break;
        }
    }

    private ListenableFuture<Bundle> runIsolatedTrainingProcess(TrainingRun run, Bundle input) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    try {
                        run.mIsolatedTrainingService.runFlTraining(
                                input,
                                new ITrainingResultCallback.Stub() {
                                    @Override
                                    public void onResult(Bundle result) {
                                        completer.set(result);
                                    }
                                });
                    } catch (Exception e) {
                        LogUtil.e(TAG, e, "Got exception when runIsolatedTrainingProcess");
                        completer.setException(e);
                    }
                    return "runIsolatedTrainingProcess";
                });
    }

    private ListenableFuture<ComputationResult> runFederatedComputation(
            CheckinResult checkinResult, TrainingRun run, IExampleStoreIterator iterator) {
        ClientOnlyPlan clientPlan = checkinResult.getPlanData();
        String outputCheckpointFile = createTempFile("output", ".ckp");
        run.mTrainingEventLogger.logEventKind(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_STARTED);

        ListenableFuture<ComputationResult> computationResultFuture;
        switch (clientPlan.getPhase().getSpecCase()) {
            case EXAMPLE_QUERY_SPEC:
                computationResultFuture =
                        runFAComputation(run, checkinResult, outputCheckpointFile, iterator);
                break;
            case TENSORFLOW_SPEC:
                computationResultFuture =
                        runFlComputation(run, checkinResult, outputCheckpointFile, iterator);
                break;
            default:
                ClientErrorLogger.getInstance()
                        .logError(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLIENT_PLAN_SPEC_ERROR,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
                return Futures.immediateFailedFuture(
                        new IllegalArgumentException(
                                String.format(
                                        "Client plan spec is not supported %s",
                                        clientPlan.getPhase().getSpecCase().toString())));
        }
        return computationResultFuture;
    }

    private ListenableFuture<ComputationResult> runFAComputation(
            TrainingRun run,
            CheckinResult checkinResult,
            String outputCheckpointFile,
            IExampleStoreIterator exampleStoreIterator) {
        try {
            ExampleSelector exampleSelector =
                    checkinResult.getTaskAssignment().getExampleSelector();
            ClientOnlyPlan clientPlan = checkinResult.getPlanData();
            // The federated analytic runs in main process which has permission to file system.
            ExampleConsumptionRecorder recorder = mInjector.getExampleConsumptionRecorder();
            long startTimeMs = SystemClock.elapsedRealtime();
            FLRunnerResult runResult =
                    mComputationRunner.runTaskWithNativeRunner(
                            run.mTaskId,
                            run.mTask.populationName(),
                            checkinResult.getInputCheckpointFile(),
                            outputCheckpointFile,
                            clientPlan,
                            exampleSelector,
                            recorder,
                            exampleStoreIterator,
                            mInterruptSupplier);
            logComputationResult(runResult, run, SystemClock.elapsedRealtime() - startTimeMs);
            ArrayList<ExampleConsumption> exampleConsumptions = recorder.finishRecordingAndGet();
            return Futures.immediateFuture(
                    new ComputationResult(outputCheckpointFile, runResult, exampleConsumptions));
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private FluentFuture<RejectionInfo> reportResultWithAuthentication(
            ComputationResult computationResult,
            FederatedComputeEncryptionKey encryptionKey,
            AuthorizationContext authContext,
            TrainingEventLogger trainingEventLogger) {
        // At most this function will make two calls to mHttpFederatedProtocol.reportResult
        // The first call would allowUnauthenticated, uplon receiving 401 (UNAUTHENTICATED), the
        // device would solve the challenge and make a second call.
        return FluentFuture.from(
                        mHttpFederatedProtocol.reportResult(
                                computationResult, encryptionKey, authContext))
                .transformAsync(
                        resp -> {
                            if (resp != null) {
                                if (authContext.isFirstAuthTry() && resp.hasAuthMetadata()) {
                                    authContext.updateAuthState(
                                            resp.getAuthMetadata(), trainingEventLogger);
                                    return reportResultWithAuthentication(
                                            computationResult,
                                            encryptionKey,
                                            authContext,
                                            trainingEventLogger);
                                } else if (resp.hasRetryWindow()) {
                                    return Futures.immediateFuture(resp);
                                } else {
                                    // TODO(b/322880077): cancel job when it fails authentication
                                    return Futures.immediateFailedFuture(
                                            new IllegalStateException(
                                                    "Unknown rejection Info from FCP server when "
                                                            + "solving authentication challenge"));
                                }
                            }
                            return Futures.immediateFuture(resp);
                        },
                        getLightweightExecutor());
    }

    @VisibleForTesting
    @Nullable
    IIsolatedTrainingService getIsolatedTrainingService() {
        mIsolatedTrainingServiceBinder =
                AbstractServiceBinder.getServiceBinderByServiceName(
                        mContext,
                        ISOLATED_TRAINING_SERVICE_NAME,
                        mContext.getPackageName(),
                        IIsolatedTrainingService.Stub::asInterface);
        return mIsolatedTrainingServiceBinder.getService(Runnable::run);
    }

    @VisibleForTesting
    void unbindFromIsolatedTrainingService() {
        mIsolatedTrainingServiceBinder.unbindFromService();
    }

    @VisibleForTesting
    static class Injector {
        ExampleConsumptionRecorder getExampleConsumptionRecorder() {
            return new ExampleConsumptionRecorder();
        }

        ListeningExecutorService getBgExecutor() {
            return getBackgroundExecutor();
        }

        TrainingEventLogger getTrainingEventLogger() {
            return new TrainingEventLogger();
        }

        HttpFederatedProtocol getHttpFederatedProtocol(
                String serverAddress,
                long clientVersion,
                String populationName,
                TrainingEventLogger trainingEventLogger) {
            return HttpFederatedProtocol.create(
                    serverAddress,
                    clientVersion,
                    populationName,
                    new HpkeJniEncrypter(),
                    trainingEventLogger);
        }

        AuthorizationContext createAuthContext(Context context, String ownerId, String ownerCert) {
            return AuthorizationContext.create(context, ownerId, ownerCert);
        }

        EligibilityDecider getEligibilityDecider(Context context) {
            return new EligibilityDecider(FederatedTrainingTaskDao.getInstance(context));
        }

        boolean isEligibilityTaskEnabled() {
            return FlagsFactory.getFlags().isEligibilityTaskEnabled();
        }
    }

    private static final class TrainingRun {
        private final int mJobId;

        private String mTaskId;
        private final FederatedTrainingTask mTask;

        private final TrainingEventLogger mTrainingEventLogger;

        private final ExampleStats mExampleStats;

        @Nullable private ListenableFuture<?> mFuture;

        @Nullable private IIsolatedTrainingService mIsolatedTrainingService = null;

        @Nullable private IExampleStoreService mExampleStoreService = null;

        private FederatedJobService.OnJobFinishedCallback mCallback;

        private TrainingRun(
                int jobId,
                FederatedTrainingTask task,
                TrainingEventLogger trainingEventLogger,
                FederatedJobService.OnJobFinishedCallback callback) {
            this.mJobId = jobId;
            this.mTask = task;
            this.mTrainingEventLogger = trainingEventLogger;
            this.mCallback = callback;
            this.mExampleStats = new ExampleStats();
        }
    }

    private class ReportFailureToServerCallback {
        private final TrainingEventLogger mLogger;

        ReportFailureToServerCallback(TrainingEventLogger logger) {
            this.mLogger = logger;
        }

        @NonNull
        public FutureCallback<ComputationResult> getServerFailureReportCallback(
                CallbackToFutureAdapter.Completer<ComputationResult> completer,
                AuthorizationContext authContext) {
            return new FutureCallback<ComputationResult>() {
                @Override
                public void onSuccess(ComputationResult result) {
                    completer.set(result);
                }

                @Override
                public synchronized void onFailure(Throwable throwable) {
                    try {
                        LogUtil.d(
                                TAG,
                                throwable,
                                "Example store or training failed. Reporting failure "
                                        + "result to server due to exception.");
                        ComputationResult failedReportComputationResult =
                                new ComputationResult(
                                        null,
                                        FLRunnerResult.newBuilder()
                                                .setContributionResult(ContributionResult.FAIL)
                                                .setErrorMessage(throwable.getMessage())
                                                .build(),
                                        null);
                        reportFailureResultToServer(
                                failedReportComputationResult, authContext, mLogger);
                        completer.setException(throwable);
                    } catch (Exception e) {
                        completer.setException(e);
                    }
                }
            };
        }
    }

    /** This function is called only when reporting a failure report to server. */
    @VisibleForTesting
    void reportFailureResultToServer(
            ComputationResult result,
            AuthorizationContext authContext,
            TrainingEventLogger trainingEventLogger) {
        var unused = reportResultWithAuthentication(result, null, authContext, trainingEventLogger);
    }
}
