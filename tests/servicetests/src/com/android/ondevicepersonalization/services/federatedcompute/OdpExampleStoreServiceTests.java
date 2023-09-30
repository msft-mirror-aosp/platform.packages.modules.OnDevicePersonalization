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

package com.android.ondevicepersonalization.services.federatedcompute;

import static android.federatedcompute.common.ClientConstants.EXAMPLE_STORE_ACTION;
import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESULT;
import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreIteratorCallback;
import android.federatedcompute.aidl.IExampleStoreService;
import android.federatedcompute.common.ClientConstants;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class OdpExampleStoreServiceTests {
    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private CountDownLatch mLatch = new CountDownLatch(1);

    private boolean mIteratorCallbackOnSuccessCalled = false;
    private boolean mIteratorCallbackOnFailureCalled = false;

    private boolean mQueryCallbackOnSuccessCalled = false;
    private boolean mQueryCallbackOnFailureCalled = false;

    @Test
    public void testWithStartQuery() throws Exception {
        Intent mIntent = new Intent();
        mIntent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        mIntent.setData(
                new Uri.Builder().scheme("app").authority(mContext.getPackageName())
                        .path("collection").build());
        IBinder binder = serviceRule.bindService(mIntent);
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        ContextData contextData = new ContextData(mContext.getPackageName());
        input.putByteArray(ClientConstants.EXTRA_CONTEXT_DATA,
                ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_COLLECTION_NAME, "CollectionName");
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "PopulationName");
        input.putString(ClientConstants.EXTRA_TASK_NAME, "TaskName");
        ((IExampleStoreService.Stub) binder).startQuery(input, callback);
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);

        IExampleStoreIterator iterator = callback.getIterator();
        TestIteratorCallback iteratorCallback = new TestIteratorCallback();
        mLatch = new CountDownLatch(1);
        iteratorCallback.setExpected(new byte[]{10}, "token1".getBytes());
        iterator.next(iteratorCallback);
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
        mIteratorCallbackOnSuccessCalled = false;

        mLatch = new CountDownLatch(1);
        iteratorCallback.setExpected(new byte[]{20}, "token2".getBytes());
        iterator.next(iteratorCallback);
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQueryBadInput() throws Exception {
        Intent mIntent = new Intent();
        mIntent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        mIntent.setData(
                new Uri.Builder().scheme("app").authority(mContext.getPackageName())
                        .path("collection").build());
        IBinder binder = serviceRule.bindService(mIntent);
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        ((IExampleStoreService.Stub) binder).startQuery(Bundle.EMPTY, callback);
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    public class TestIteratorCallback implements IExampleStoreIteratorCallback {
        byte[] mExpectedExample;
        byte[] mExpectedResumptionToken;

        public void setExpected(byte[] expectedExample, byte[] expectedResumptionToken) {
            mExpectedExample = expectedExample;
            mExpectedResumptionToken = expectedResumptionToken;
        }

        @Override
        public void onIteratorNextSuccess(Bundle result) throws RemoteException {
            assertArrayEquals(mExpectedExample, result.getByteArray(
                    EXTRA_EXAMPLE_ITERATOR_RESULT));
            assertArrayEquals(mExpectedResumptionToken, result.getByteArray(
                    EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN));
            mIteratorCallbackOnSuccessCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onIteratorNextFailure(int i) throws RemoteException {
            mIteratorCallbackOnFailureCalled = true;
            mLatch.countDown();
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    public class TestQueryCallback implements IExampleStoreCallback {
        private IExampleStoreIterator mIterator;

        @Override
        public void onStartQuerySuccess(IExampleStoreIterator iExampleStoreIterator)
                throws RemoteException {
            mQueryCallbackOnSuccessCalled = true;
            mIterator = iExampleStoreIterator;
            mLatch.countDown();
        }

        @Override
        public void onStartQueryFailure(int errorCode) {
            mQueryCallbackOnFailureCalled = true;
            mLatch.countDown();
        }

        @Override
        public IBinder asBinder() {
            return null;
        }

        public IExampleStoreIterator getIterator() {
            return mIterator;
        }
    }
}
