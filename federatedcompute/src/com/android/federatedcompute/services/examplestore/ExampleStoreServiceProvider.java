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

package com.android.federatedcompute.services.examplestore;

import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;
import android.federatedcompute.common.ClientConstants;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.ExampleStats;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.FederatedTrainingTask;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.internal.federated.plan.ExampleSelector;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Provides {@link IExampleStoreService}. */
public class ExampleStoreServiceProvider {
    private static final String TAG = ExampleStoreServiceProvider.class.getSimpleName();
    private AbstractServiceBinder<IExampleStoreService> mExampleStoreServiceBinder;

    /** Returns {@link IExampleStoreService}. */
    public IExampleStoreService getExampleStoreService(String packageName, Context context) {
        mExampleStoreServiceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        context,
                        ClientConstants.EXAMPLE_STORE_ACTION,
                        packageName,
                        IExampleStoreService.Stub::asInterface);
        return mExampleStoreServiceBinder.getService(Runnable::run);
    }

    /** Unbind from {@link IExampleStoreService}. */
    public void unbindFromExampleStoreService() {
        mExampleStoreServiceBinder.unbindFromService();
    }

    /** Returns an {@link IExampleStoreIterator} implemented by client app. */
    public ListenableFuture<IExampleStoreIterator> getExampleStoreIterator(
            IExampleStoreService exampleStoreService,
            FederatedTrainingTask task,
            String taskId,
            ExampleSelector exampleSelector,
            ExampleStats exampleStats) {
        try {
            long startTimeNanos = SystemClock.elapsedRealtimeNanos();
            Bundle bundle = new Bundle();
            bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, task.populationName());
            bundle.putString(ClientConstants.EXTRA_TASK_ID, taskId);
            bundle.putByteArray(ClientConstants.EXTRA_CONTEXT_DATA, task.contextData());
            if (exampleSelector != null) {
                byte[] criteria = exampleSelector.getCriteria().toByteArray();
                byte[] resumptionToken = exampleSelector.getResumptionToken().toByteArray();
                bundle.putByteArray(
                        ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN, resumptionToken);
                bundle.putByteArray(ClientConstants.EXTRA_EXAMPLE_ITERATOR_CRITERIA, criteria);
            }
            return runExampleStoreStartQuery(
                    exampleStoreService, bundle, exampleStats, startTimeNanos);
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Got exception when StartQuery");
            return Futures.immediateFailedFuture(e);
        }
    }

    /** Returns an {@link IExampleStoreIterator} implemented by client app in synchronized call. */
    public IExampleStoreIterator getExampleIterator(
            IExampleStoreService exampleStoreService, FederatedTrainingTask task, String taskName) {
        try {
            Bundle bundle = new Bundle();
            bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, task.populationName());
            bundle.putString(ClientConstants.EXTRA_TASK_ID, taskName);
            bundle.putByteArray(ClientConstants.EXTRA_CONTEXT_DATA, task.contextData());
            BlockingQueue<CallbackResult> asyncResult = new ArrayBlockingQueue<>(1);
            exampleStoreService.startQuery(
                    bundle,
                    new IExampleStoreCallback.Stub() {
                        @Override
                        public void onStartQuerySuccess(IExampleStoreIterator iterator) {
                            LogUtil.d(TAG, "Acquire iterator");
                            asyncResult.add(new CallbackResult(iterator, 0));
                        }

                        @Override
                        public void onStartQueryFailure(int errorCode) {
                            LogUtil.e(TAG, "Could not acquire iterator: " + errorCode);
                            asyncResult.add(new CallbackResult(null, errorCode));
                        }
                    });
            CallbackResult callbackResult =
                    asyncResult.poll(
                            FlagsFactory.getFlags().getExampleStoreServiceCallbackTimeoutSec(),
                            TimeUnit.SECONDS);
            if (callbackResult.mErrorCode != 0) {
                return null;
            }
            return callbackResult.mIterator;
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Got exception when StartQuery");
            return null;
        }
    }

    private static class CallbackResult {
        final IExampleStoreIterator mIterator;
        final int mErrorCode;

        CallbackResult(IExampleStoreIterator iterator, int errorCode) {
            mIterator = iterator;
            mErrorCode = errorCode;
        }
    }

    private ListenableFuture<IExampleStoreIterator> runExampleStoreStartQuery(
            IExampleStoreService exampleStoreService,
            Bundle input,
            ExampleStats exampleStats,
            long startCallTimeNanos) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    try {
                        exampleStoreService.startQuery(
                                input,
                                new IExampleStoreCallback.Stub() {
                                    @Override
                                    public void onStartQuerySuccess(
                                            IExampleStoreIterator iterator) {
                                        LogUtil.d(TAG, "Acquire iterator");
                                        exampleStats.mStartQueryLatencyNanos.addAndGet(
                                                SystemClock.elapsedRealtimeNanos()
                                                        - startCallTimeNanos);
                                        completer.set(iterator);
                                    }

                                    @Override
                                    public void onStartQueryFailure(int errorCode) {
                                        LogUtil.e(TAG, "Could not acquire iterator: " + errorCode);
                                        exampleStats.mStartQueryLatencyNanos.addAndGet(
                                                SystemClock.elapsedRealtimeNanos()
                                                        - startCallTimeNanos);
                                        completer.setException(
                                                new IllegalStateException(
                                                        "StartQuery failed: " + errorCode));
                                    }
                                });
                    } catch (Exception e) {
                        completer.setException(e);
                    }
                    return "runExampleStoreStartQuery";
                });
    }
}
