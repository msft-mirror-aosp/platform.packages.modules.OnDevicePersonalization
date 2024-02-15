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

package com.android.ondevicepersonalization.services.serviceflow;

import android.os.Bundle;

import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.process.IsolatedServiceInfo;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.process.ProcessRunnerImpl;
import com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.HashMap;
import java.util.Map;

/** Orchestrator that coordinate service flows through process runners. */
public class ServiceFlowOrchestrator {

    private static final Map<ServiceFlowType, ServiceFlow> sServiceFlowMap = new HashMap<>();
    // All service flows should use the same process runner until main process restarts.
    private static final ProcessRunner sProcessRunner =
            FlagsFactory.getFlags().isSharedIsolatedProcessFeatureEnabled()
                    ? SharedIsolatedProcessRunner.getInstance()
                    : ProcessRunnerImpl.getInstance();
    private static final ListeningExecutorService sExecutor =
            OnDevicePersonalizationExecutors.getBackgroundExecutor();

    ServiceFlowOrchestrator() {}

    private static class ServiceFlowOrchestratorLazyInstanceHolder {
        static final ServiceFlowOrchestrator LAZY_INSTANCE =
                new ServiceFlowOrchestrator();
    }

    /** Returns the global ServiceFlowOrchestrator. */
    public static ServiceFlowOrchestrator getInstance() {
        return ServiceFlowOrchestratorLazyInstanceHolder.LAZY_INSTANCE;
    }

    /** Registers a given service flow with the orchestrator. */
    public void register(ServiceFlowType serviceFlowType, Object... args) {
        sServiceFlowMap.put(
                serviceFlowType, ServiceFlowFactory.createInstance(serviceFlowType, args));
    }

    /** Runs the given service flow. */
    public void run(ServiceFlowType serviceFlowType) {
        ServiceFlow serviceFlow = sServiceFlowMap.get(serviceFlowType);

        if (!serviceFlow.isServiceFlowReady()) return;

        ListenableFuture<IsolatedServiceInfo> loadServiceFuture =
                sProcessRunner.loadIsolatedService(
                        serviceFlowType.getTaskName(), serviceFlow.getService());

        ListenableFuture<Bundle> runServiceFuture = FluentFuture.from(loadServiceFuture)
                .transformAsync(
                        isolatedServiceInfo ->
                                sProcessRunner
                                        .runIsolatedService(
                                                isolatedServiceInfo,
                                                serviceFlowType.getOperationCode(),
                                                serviceFlow.getServiceParams()),
                        sExecutor);

        serviceFlow.uploadServiceFlowMetrics(runServiceFuture);

        ListenableFuture<?> serviceFlowResultFuture =
                serviceFlow.getServiceFlowResultFuture(runServiceFuture);

        serviceFlow.returnResultThroughCallback(serviceFlowResultFuture);

        var unused =
                Futures.whenAllComplete(loadServiceFuture, serviceFlowResultFuture)
                        .callAsync(
                                () -> {
                                    serviceFlow.cleanUpServiceParams();
                                    return sProcessRunner.unloadIsolatedService(
                                            loadServiceFuture.get());
                                }, sExecutor);
    }
}
