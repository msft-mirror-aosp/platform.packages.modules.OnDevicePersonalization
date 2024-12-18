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

package com.android.ondevicepersonalization.services.federatedcompute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.FederatedComputeManager;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ScheduleFederatedComputeRequest;
import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;
import android.os.OutcomeReceiver;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class FederatedComputeServiceImplTest {
    private static final String FC_SERVER_URL = "https://google.com";
    private static final String TEST_POPULATION_NAME = "population";
    private static final TrainingInterval TEST_INTERVAL =
            new TrainingInterval.Builder()
                    .setMinimumIntervalMillis(100)
                    .setSchedulingMode(1)
                    .build();
    private static final TrainingOptions TEST_OPTIONS =
            new TrainingOptions.Builder()
                    .setPopulationName(TEST_POPULATION_NAME)
                    .setTrainingInterval(TEST_INTERVAL)
                    .build();

    private static final String SERVICE_CLASS = "com.test.TestPersonalizationService";
    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();
    ArgumentCaptor<OutcomeReceiver<Object, Exception>> mCallbackCapture;
    ArgumentCaptor<ScheduleFederatedComputeRequest> mRequestCapture;
    private final TestInjector mInjector = new TestInjector();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private int mErrorCode = 0;
    private boolean mOnSuccessCalled = false;
    private boolean mOnErrorCalled = false;
    private FederatedComputeServiceImpl mServiceImpl;
    private IFederatedComputeService mServiceProxy;
    private FederatedComputeManager mMockManager;
    private ComponentName mIsolatedService;
    @Mock
    UserPrivacyStatus mUserPrivacyStatus;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .addStaticMockFixtures(TestableDeviceConfig::new)
            .spyStatic(UserPrivacyStatus.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setup() throws Exception {
        mIsolatedService = new ComponentName(mApplicationContext.getPackageName(), SERVICE_CLASS);

        mMockManager = Mockito.mock(FederatedComputeManager.class);
        mCallbackCapture = ArgumentCaptor.forClass(OutcomeReceiver.class);
        mRequestCapture = ArgumentCaptor.forClass(ScheduleFederatedComputeRequest.class);
        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(true).when(mUserPrivacyStatus).isMeasurementEnabled();
        doNothing()
                .when(mMockManager)
                .cancel(any(), any(), any(), mCallbackCapture.capture());
        doNothing()
                .when(mMockManager)
                .schedule(mRequestCapture.capture(), any(), mCallbackCapture.capture());

        mServiceImpl =
                new FederatedComputeServiceImpl(
                        ComponentName.createRelative(
                                mApplicationContext.getPackageName(),
                                AppManifestConfigHelper.getServiceNameFromOdpSettings(
                                        mApplicationContext, mApplicationContext.getPackageName())),
                        mApplicationContext,
                        mInjector);
        mServiceProxy = IFederatedComputeService.Stub.asInterface(mServiceImpl);
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    @Test
    public void testSchedule() throws Exception {
        mServiceProxy.schedule(TEST_OPTIONS, new TestCallback());
        mCallbackCapture.getValue().onResult(null);
        var request = mRequestCapture.getValue();
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertEquals(FC_SERVER_URL, request.getTrainingOptions().getServerAddress());
        assertEquals(TEST_POPULATION_NAME, request.getTrainingOptions().getPopulationName());
        assertTrue(mOnSuccessCalled);
    }

    @Test
    public void testScheduleMeasurementControlRevoked() throws Exception {
        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        ExtendedMockito.doReturn(false)
                .when(mUserPrivacyStatus).isMeasurementEnabled();

        mServiceProxy.schedule(TEST_OPTIONS, new TestCallback());
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertFalse(mOnSuccessCalled);
    }

    @Test
    public void testScheduleUrlOverride() throws Exception {
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_fc_server_url_package "
                        + mApplicationContext.getPackageName());
        String overrideUrl = "https://android.com";
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_fc_server_url " + overrideUrl);

        mServiceProxy.schedule(TEST_OPTIONS, new TestCallback());
        mCallbackCapture.getValue().onResult(null);
        var request = mRequestCapture.getValue();
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertEquals(overrideUrl, request.getTrainingOptions().getServerAddress());
        assertEquals(TEST_POPULATION_NAME, request.getTrainingOptions().getPopulationName());
        assertTrue(mOnSuccessCalled);
    }

    @Test
    public void testScheduleErr() throws Exception {
        mServiceProxy.schedule(TEST_OPTIONS, new TestCallback());
        mCallbackCapture.getValue().onError(new Exception());
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertTrue(mOnErrorCalled);
        assertEquals(ClientConstants.STATUS_INTERNAL_ERROR, mErrorCode);
    }

    @Test
    public void testCancel() throws Exception {
        EventsDao.getInstanceForTest(mApplicationContext)
                .updateOrInsertEventState(
                        new EventState.Builder()
                                .setService(mIsolatedService)
                                .setTaskIdentifier(TEST_POPULATION_NAME)
                                .setToken(new byte[] {})
                                .build());

        mServiceProxy.cancel(TEST_POPULATION_NAME, new TestCallback());
        mCallbackCapture.getValue().onResult(null);
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertTrue(mOnSuccessCalled);
    }

    @Test
    public void testCancelNoPopulation() throws Exception {
        mServiceProxy.cancel(TEST_POPULATION_NAME, new TestCallback());
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        verify(mMockManager, times(0)).cancel(any(), any(), any(), any());
        assertTrue(mOnSuccessCalled);
    }

    @Test
    public void testCancelErr() throws Exception {
        EventsDao.getInstanceForTest(mApplicationContext)
                .updateOrInsertEventState(
                        new EventState.Builder()
                                .setService(mIsolatedService)
                                .setTaskIdentifier(TEST_POPULATION_NAME)
                                .setToken(new byte[] {})
                                .build());

        mServiceProxy.cancel(TEST_POPULATION_NAME, new TestCallback());
        mCallbackCapture.getValue().onError(new Exception());
        mLatch.await(1000, TimeUnit.MILLISECONDS);

        assertTrue(mOnErrorCalled);
        assertEquals(ClientConstants.STATUS_INTERNAL_ERROR, mErrorCode);
    }

    @After
    public void cleanup() {
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_fc_server_url_package \"\"");
        ShellUtils.runShellCommand(
                "setprop debug.ondevicepersonalization.override_fc_server_url \"\"");

        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mApplicationContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    private class TestCallback extends IFederatedComputeCallback.Stub {
        @Override
        public void onSuccess() {
            mOnSuccessCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onFailure(int i) {
            mErrorCode = i;
            mOnErrorCalled = true;
            mLatch.countDown();
        }
    }

    private class TestInjector extends FederatedComputeServiceImpl.Injector {

        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        FederatedComputeManager getFederatedComputeManager(Context context) {
            return mMockManager;
        }

        EventsDao getEventsDao(Context context) {
            return EventsDao.getInstanceForTest(context);
        }
    }
}
