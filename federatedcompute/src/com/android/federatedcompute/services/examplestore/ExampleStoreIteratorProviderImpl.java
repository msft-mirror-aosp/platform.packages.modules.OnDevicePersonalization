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

package com.android.federatedcompute.services.examplestore;

import android.content.Intent;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;
import android.federatedcompute.common.ClientConstants;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Pair;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.ErrorStatusException;
import com.android.federatedcompute.services.common.Flags;

import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.internal.federated.plan.ExampleSelector;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Implementation of the ExampleStoreIterator interface. */
public class ExampleStoreIteratorProviderImpl implements ExampleStoreIteratorProvider {
    private static final String TAG = ExampleStoreIteratorProviderImpl.class.getSimpleName();
    private final ExampleStoreServiceProvider mExampleStoreServiceProvider;
    private final Flags mFlags;

    public ExampleStoreIteratorProviderImpl(
            ExampleStoreServiceProvider exampleStoreServiceProvider, Flags flags) {
        this.mExampleStoreServiceProvider = exampleStoreServiceProvider;
        this.mFlags = flags;
    }

    @Override
    public IExampleStoreIterator getExampleStoreIterator(
            String packageName, ExampleSelector exampleSelector)
            throws InterruptedException, ErrorStatusException {
        String collection = exampleSelector.getCollectionUri();
        byte[] criteria = exampleSelector.getCriteria().toByteArray();
        byte[] resumptionToken = exampleSelector.getResumptionToken().toByteArray();
        Intent intent = new Intent();
        intent.setAction(ClientConstants.EXAMPLE_STORE_ACTION).setPackage(packageName);
        intent.setData(
                new Uri.Builder().scheme("app").authority(packageName).path(collection).build());
        LogUtil.d(TAG, "Attempting to bind to example store service: %s", intent);
        if (!mExampleStoreServiceProvider.bindService(intent)) {
            LogUtil.w(TAG, "bindService failed for example store service: %s", intent);
            mExampleStoreServiceProvider.unbindService();
            return null;
        }
        IExampleStoreService exampleStoreService =
                mExampleStoreServiceProvider.getExampleStoreService();
        Bundle bundle = new Bundle();
        bundle.putString(Constants.EXTRA_COLLECTION_NAME, collection);
        bundle.putByteArray(Constants.EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN, resumptionToken);
        bundle.putByteArray(Constants.EXTRA_EXAMPLE_ITERATOR_CRITERIA, criteria);
        SettableFuture<Pair<IExampleStoreIterator, Integer>> iteratorOrFailureFuture =
                SettableFuture.create();
        try {
            try {
                exampleStoreService.startQuery(
                        bundle,
                        new IExampleStoreCallback.Stub() {
                            @Override
                            public void onStartQuerySuccess(IExampleStoreIterator iterator) {
                                LogUtil.d(TAG, "Acquire iterator");
                                iteratorOrFailureFuture.set(Pair.create(iterator, null));
                            }

                            @Override
                            public void onStartQueryFailure(int errorCode) {
                                LogUtil.e(TAG, "Could not acquire iterator: %d", errorCode);
                                iteratorOrFailureFuture.set(Pair.create(null, errorCode));
                            }
                        });
            } catch (RemoteException e) {
                LogUtil.e(TAG, "StartQuery failure: ", e);
                throw new IllegalStateException(e);
            }
            Pair<IExampleStoreIterator, Integer> iteratorOrFailure;
            try {
                iteratorOrFailure =
                        iteratorOrFailureFuture.get(
                                mFlags.getAppHostedExampleStoreTimeoutSecs(), TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                // Should not happen.
                throw new UncheckedExecutionException(e);
            } catch (TimeoutException e) {
                LogUtil.e(TAG, "startQuery timed out: ", e);
                throw new IllegalStateException(
                        String.format(
                                "startQuery timed out (%ss): %s",
                                mFlags.getAppHostedExampleStoreTimeoutSecs(), collection),
                        e);
            }
            if (iteratorOrFailure.second != null) {
                throw new IllegalStateException(
                        String.format(
                                "onStartQueryFailure collection %s error code %d",
                                collection, iteratorOrFailure.second));
            }
            LogUtil.d(TAG, "Wrapping IExampleStoreIterator");
            return iteratorOrFailure.first;
        } catch (Exception e) {
            // If any exception is thrown in try block, we first call unbindService to avoid service
            // connection hanging.
            LogUtil.d(TAG, "Unbinding from service due to exception", e);
            mExampleStoreServiceProvider.unbindService();
            throw e;
        }
    }
}
