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
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
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
    private static final Context APPLICATION_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final ComponentName ISOLATED_SERVICE_COMPONENT =
            new ComponentName(APPLICATION_CONTEXT.getPackageName(), SERVICE_CLASS);
    private static final ContextData TEST_CONTEXT_DATA =
            new ContextData(
                    ISOLATED_SERVICE_COMPONENT.getPackageName(),
                    ISOLATED_SERVICE_COMPONENT.getClassName());
    private static final String TEST_POPULATION_NAME = "PopulationName";
    private static final String TEST_TASK_NAME = "TaskName";
    private static final String TEST_COLLECTION_URI = "CollectionUri";
    private static final int LATCH_LONG_TIMEOUT_MILLIS = 10000;
    private static final int LATCH_SHORT_TIMEOUT_MILLIS = 1000;

    @Mock Context mMockContext;
    @InjectMocks OdpExampleStoreService mService;

    @Mock UserPrivacyStatus mMockUserPrivacyStatus;

    @Mock Clock mMockClock;

    private Flags mStubFlags = new Flags() {
        @Override public boolean getGlobalKillSwitch() {
            return false;
        }
    };

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .spyStatic(UserPrivacyStatus.class)
                    .mockStatic(FlagsFactory.class)
                    .spyStatic(StableFlags.class)
                    .spyStatic(MonotonicClock.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private CountDownLatch mLatch;



    private boolean mIteratorCallbackOnSuccessCalled = false;
    private boolean mIteratorCallbackOnFailureCalled = false;

    private boolean mQueryCallbackOnSuccessCalled = false;
    private boolean mQueryCallbackOnFailureCalled = false;

    private final EventsDao mEventsDao = EventsDao.getInstanceForTest(APPLICATION_CONTEXT);

    @Before
    public void setUp() throws Exception {
        assumeTrue(DeviceSupportHelper.isDeviceSupported());
        initMocks(this);
        when(mMockContext.getApplicationContext()).thenReturn(APPLICATION_CONTEXT);
        ExtendedMockito.doReturn(mMockUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        ExtendedMockito.doReturn(mMockClock).when(MonotonicClock::getInstance);
        doReturn(true).when(mMockUserPrivacyStatus).isMeasurementEnabled();
        doReturn(true).when(mMockUserPrivacyStatus).isProtectedAudienceEnabled();
        doReturn(200L).when(mMockClock).currentTimeMillis();
        doReturn(1000L).when(mMockClock).elapsedRealtime();
        mQueryCallbackOnSuccessCalled = false;
        mQueryCallbackOnFailureCalled = false;
        mLatch = new CountDownLatch(1);

        ExtendedMockito.doReturn(mStubFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(SdkLevel.isAtLeastU()).when(
                () -> StableFlags.get(KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED));
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
    }

    @Test
    public void testStartQuery_lessThanMinExample_failure() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier(TEST_POPULATION_NAME)
                        .setService(ISOLATED_SERVICE_COMPONENT)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = getTestInputBundle(/* eligibilityMinExample= */ 4);

        binder.startQuery(input, callback);

        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(LATCH_LONG_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testStartQuery_moreThanMinExample_success() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier(TEST_POPULATION_NAME)
                        .setService(ISOLATED_SERVICE_COMPONENT)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = getTestInputBundle(/* eligibilityMinExample= */ 2);

        binder.startQuery(input, callback);

        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(LATCH_LONG_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        assertTrue(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQuery() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier(TEST_POPULATION_NAME)
                        .setService(ISOLATED_SERVICE_COMPONENT)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(TEST_CONTEXT_DATA));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, TEST_POPULATION_NAME);
        input.putString(ClientConstants.EXTRA_TASK_ID, TEST_TASK_NAME);
        input.putString(ClientConstants.EXTRA_COLLECTION_URI, TEST_COLLECTION_URI);

        binder.startQuery(input, callback);

        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(LATCH_LONG_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        assertTrue(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);

        IExampleStoreIterator iterator = callback.getIterator();
        TestIteratorCallback iteratorCallback = new TestIteratorCallback();
        mLatch = new CountDownLatch(1);
        iteratorCallback.setExpected(new byte[] {10}, "token1".getBytes());
        iterator.next(iteratorCallback);

        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(LATCH_SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);

        mIteratorCallbackOnSuccessCalled = false;
        mLatch = new CountDownLatch(1);
        iteratorCallback.setExpected(new byte[] {20}, "token2".getBytes());
        iterator.next(iteratorCallback);

        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(LATCH_SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQueryMeasurementControlRevoked() throws Exception {
        doReturn(false).when(mMockUserPrivacyStatus).isMeasurementEnabled();
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier(TEST_POPULATION_NAME)
                        .setService(ISOLATED_SERVICE_COMPONENT)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(TEST_CONTEXT_DATA));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, TEST_POPULATION_NAME);
        input.putString(ClientConstants.EXTRA_TASK_ID, TEST_TASK_NAME);

        binder.startQuery(input, callback);

        mLatch.await(LATCH_SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQueryNotValidJob() throws Exception {
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(TEST_CONTEXT_DATA));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, TEST_POPULATION_NAME);
        input.putString(ClientConstants.EXTRA_TASK_ID, TEST_TASK_NAME);

        ((IExampleStoreService.Stub) binder).startQuery(input, callback);

        mLatch.await(LATCH_SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testWithStartQueryBadInput() throws Exception {
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();

        binder.startQuery(Bundle.EMPTY, callback);

        mLatch.await(LATCH_SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
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
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));

        assertThrows(
                SecurityException.class,
                () -> binder.startQuery(Bundle.EMPTY, new TestQueryCallback()));

        mLatch.await(LATCH_SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertFalse(mQueryCallbackOnFailureCalled);
    }

    @Test
    public void testStartQuery_isolatedServiceThrowsException() throws Exception {
        mEventsDao.updateOrInsertEventState(
                new EventState.Builder()
                        .setTaskIdentifier("throw_exception")
                        .setService(ISOLATED_SERVICE_COMPONENT)
                        .setToken()
                        .build());
        mService.onCreate();
        Intent intent = new Intent();
        intent.setAction(EXAMPLE_STORE_ACTION).setPackage(APPLICATION_CONTEXT.getPackageName());
        IExampleStoreService binder =
                IExampleStoreService.Stub.asInterface(mService.onBind(intent));
        assertNotNull(binder);
        TestQueryCallback callback = new TestQueryCallback();
        Bundle input = new Bundle();
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(TEST_CONTEXT_DATA));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "throw_exception");
        input.putString(ClientConstants.EXTRA_TASK_ID, TEST_TASK_NAME);
        input.putString(ClientConstants.EXTRA_COLLECTION_URI, TEST_COLLECTION_URI);
        input.putInt(ClientConstants.EXTRA_ELIGIBILITY_MIN_EXAMPLE, 4);

        binder.startQuery(input, callback);

        assertTrue(
                "timeout reached while waiting for countdownlatch!",
                mLatch.await(LATCH_LONG_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        assertFalse(mQueryCallbackOnSuccessCalled);
        assertTrue(mQueryCallbackOnFailureCalled);
    }

    private static Bundle getTestInputBundle(int eligibilityMinExample) throws Exception {
        Bundle input = new Bundle();
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(TEST_CONTEXT_DATA));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, TEST_POPULATION_NAME);
        input.putString(ClientConstants.EXTRA_TASK_ID, TEST_TASK_NAME);
        input.putString(ClientConstants.EXTRA_COLLECTION_URI, TEST_COLLECTION_URI);
        input.putInt(ClientConstants.EXTRA_ELIGIBILITY_MIN_EXAMPLE, eligibilityMinExample);
        return input;
    }

    private class TestIteratorCallback implements IExampleStoreIteratorCallback {
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

    private class TestQueryCallback implements IExampleStoreCallback {
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
                OnDevicePersonalizationDbHelper.getInstanceForTest(APPLICATION_CONTEXT);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
