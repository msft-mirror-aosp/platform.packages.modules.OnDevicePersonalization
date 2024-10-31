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

import static com.android.ondevicepersonalization.services.PhFlags.KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreIteratorCallback;
import android.federatedcompute.aidl.IExampleStoreService;
import android.federatedcompute.common.ClientConstants;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.StableFlags;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class OdpExampleStoreServiceTests {
    private static final String SERVICE_CLASS = "com.test.TestPersonalizationService";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    @Mock Context mMockContext;
    @InjectMocks OdpExampleStoreService mService;

    @Mock
    UserPrivacyStatus mUserPrivacyStatus;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(UserPrivacyStatus.class)
            .spyStatic(StableFlags.class)
            .setStrictness(Strictness.LENIENT)
            .build();
    private CountDownLatch mLatch;
    private ComponentName mIsolatedService;

    private boolean mIteratorCallbackOnSuccessCalled = false;
    private boolean mIteratorCallbackOnFailureCalled = false;

    private boolean mQueryCallbackOnSuccessCalled = false;
    private boolean mQueryCallbackOnFailureCalled = false;

    private final EventsDao mEventsDao = EventsDao.getInstanceForTest(mContext);
    @Before
    public void setUp() throws Exception {
        assumeTrue(DeviceSupportHelper.isDeviceSupported());
        initMocks(this);
        when(mMockContext.getApplicationContext()).thenReturn(mContext);
        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(true).when(mUserPrivacyStatus).isMeasurementEnabled();
        doReturn(true).when(mUserPrivacyStatus).isProtectedAudienceEnabled();
        mQueryCallbackOnSuccessCalled = false;
        mQueryCallbackOnFailureCalled = false;
        mLatch = new CountDownLatch(1);
        mIsolatedService = new ComponentName(mContext.getPackageName(), SERVICE_CLASS);
        ExtendedMockito.doReturn(SdkLevel.isAtLeastU()).when(
                () -> StableFlags.get(KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED));
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
    }

    @Test
    public void testStartQuery_lessThanMinExample_failure() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier("PopulationName")
                        .setService(mIsolatedService)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        ContextData contextData =
                new ContextData(mIsolatedService.getPackageName(), mIsolatedService.getClassName());
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "PopulationName");
        input.putString(ClientConstants.EXTRA_TASK_ID, "TaskName");
        input.putString(ClientConstants.EXTRA_COLLECTION_URI, "CollectionUri");
        input.putInt(ClientConstants.EXTRA_ELIGIBILITY_MIN_EXAMPLE, 4);

        binder.startQuery(input, callback);
        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(10000, TimeUnit.MILLISECONDS));

        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testStartQuery_moreThanMinExample_success() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier("PopulationName")
                        .setService(mIsolatedService)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        ContextData contextData =
                new ContextData(mIsolatedService.getPackageName(), mIsolatedService.getClassName());
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "PopulationName");
        input.putString(ClientConstants.EXTRA_TASK_ID, "TaskName");
        input.putString(ClientConstants.EXTRA_COLLECTION_URI, "CollectionUri");
        input.putInt(ClientConstants.EXTRA_ELIGIBILITY_MIN_EXAMPLE, 2);

        binder.startQuery(input, callback);
        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(10000, TimeUnit.MILLISECONDS));

        assertTrue(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQuery() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier("PopulationName")
                        .setService(mIsolatedService)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        ContextData contextData =
                new ContextData(mIsolatedService.getPackageName(), mIsolatedService.getClassName());
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "PopulationName");
        input.putString(ClientConstants.EXTRA_TASK_ID, "TaskName");
        input.putString(ClientConstants.EXTRA_COLLECTION_URI, "CollectionUri");

        binder.startQuery(input, callback);
        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(10000, TimeUnit.MILLISECONDS));

        assertTrue(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);

        IExampleStoreIterator iterator = callback.getIterator();
        TestIteratorCallback iteratorCallback = new TestIteratorCallback();
        mLatch = new CountDownLatch(1);
        iteratorCallback.setExpected(new byte[] {10}, "token1".getBytes());
        iterator.next(iteratorCallback);
        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
        mIteratorCallbackOnSuccessCalled = false;

        mLatch = new CountDownLatch(1);
        iteratorCallback.setExpected(new byte[] {20}, "token2".getBytes());
        iterator.next(iteratorCallback);
        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQueryMeasurementControlRevoked() throws Exception {
        doReturn(false).when(mUserPrivacyStatus).isMeasurementEnabled();
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier("PopulationName")
                        .setService(mIsolatedService)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        ContextData contextData =
                new ContextData(mIsolatedService.getPackageName(), mIsolatedService.getClassName());
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "PopulationName");
        input.putString(ClientConstants.EXTRA_TASK_ID, "TaskName");

        binder.startQuery(input, callback);
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQueryNotValidJob() throws Exception {
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        ContextData contextData =
                new ContextData(mIsolatedService.getPackageName(), mIsolatedService.getClassName());
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "PopulationName");
        input.putString(ClientConstants.EXTRA_TASK_ID, "TaskName");

        ((IExampleStoreService.Stub) binder).startQuery(input, callback);
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQueryBadInput() throws Exception {
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        binder.startQuery(Bundle.EMPTY, callback);
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testFailedPermissionCheck() throws Exception {
        when(mMockContext.checkCallingOrSelfPermission(
                        eq("android.permission.BIND_EXAMPLE_STORE_SERVICE")))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));

        assertThrows(
                SecurityException.class,
                () -> binder.startQuery(Bundle.EMPTY, new TestQueryCallback()));

        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testStartQuery_isolatedServiceThrowsException() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier("throw_exception")
                        .setService(mIsolatedService)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(mContext.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        ContextData contextData =
                new ContextData(mIsolatedService.getPackageName(), mIsolatedService.getClassName());
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "throw_exception");
        input.putString(ClientConstants.EXTRA_TASK_ID, "TaskName");
        input.putString(ClientConstants.EXTRA_COLLECTION_URI, "CollectionUri");
        input.putInt(ClientConstants.EXTRA_ELIGIBILITY_MIN_EXAMPLE, 4);

        binder.startQuery(input, callback);
        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(10000, TimeUnit.MILLISECONDS));

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
            assertArrayEquals(mExpectedExample, result.getByteArray(EXTRA_EXAMPLE_ITERATOR_RESULT));
            assertArrayEquals(
                    mExpectedResumptionToken,
                    result.getByteArray(EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN));
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

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
