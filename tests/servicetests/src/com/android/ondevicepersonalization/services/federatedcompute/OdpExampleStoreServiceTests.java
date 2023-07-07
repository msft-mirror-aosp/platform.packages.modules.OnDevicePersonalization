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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;
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
    private final CountDownLatch mLatch = new CountDownLatch(1);

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
        ((IExampleStoreService.Stub) binder).startQuery(Bundle.EMPTY, new TestQueryCallback());
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);
    }

    public class TestQueryCallback implements IExampleStoreCallback {
        @Override
        public void onStartQuerySuccess(IExampleStoreIterator iExampleStoreIterator)
                throws RemoteException {
            mQueryCallbackOnSuccessCalled = true;
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
    }
}
