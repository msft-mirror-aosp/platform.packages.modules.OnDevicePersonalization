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

package com.android.ondevicepersonalization.services.process;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_TRUSTED_PARTNER_APPS_LIST;
import static com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner.TRUSTED_PARTNER_APPS_SIP;
import static com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner.UNKNOWN_APPS_SIP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.StableFlags;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(JUnit4.class)
public class SharedIsolatedProcessRunnerTest {

    private static final SharedIsolatedProcessRunner sSipRunner =
            SharedIsolatedProcessRunner.getInstance();

    private static final String TRUSTED_APP_NAME = "trusted_app_name";
    private static final int CALLBACK_TIMEOUT_SECONDS = 60;
    @Mock
    private Flags mFlags;

    @Mock private IsolatedServiceInfo mIsolatedServiceInfo = null;
    @Mock private AbstractServiceBinder mAbstractServiceBinder = null;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(FlagsFactory.class)
            .spyStatic(StableFlags.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    private final SharedIsolatedProcessRunner.Injector mTestInjector =
            new SharedIsolatedProcessRunner.Injector();

    private SharedIsolatedProcessRunner mInstanceUnderTest;
    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
    private final FutureCallback<Object> mTestCallback =
            new FutureCallback<Object>() {
                @Override
                public void onSuccess(Object result) {
                    mCountDownLatch.countDown();
                }

                @Override
                public void onFailure(Throwable t) {
                    mCountDownLatch.countDown();
                }
            };

    @Before
    public void setup() throws Exception {
        assumeTrue(DeviceSupportHelper.isDeviceSupported());
        PhFlagsTestUtil.setUpDeviceConfigPermissions();

        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(TRUSTED_APP_NAME).when(
                () -> StableFlags.get(KEY_TRUSTED_PARTNER_APPS_LIST));
        ExtendedMockito.doReturn(true).when(
                () -> StableFlags.get(KEY_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED));
        mInstanceUnderTest =
                new SharedIsolatedProcessRunner(
                        ApplicationProvider.getApplicationContext(), mTestInjector);
    }

    @Test
    public void testGetSipInstanceName_artImageLoadingOptimizationEnabled() {
        ExtendedMockito.doReturn(true).when(
                () -> StableFlags.get(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED));
        assertThat(sSipRunner.getSipInstanceName(TRUSTED_APP_NAME))
                .isEqualTo(TRUSTED_PARTNER_APPS_SIP + "_disable_art_image_");
    }

    @Test
    public void testGetSipInstanceName_trustedApp() {
        ExtendedMockito.doReturn(false).when(
                () -> StableFlags.get(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED));
        assertThat(sSipRunner.getSipInstanceName(TRUSTED_APP_NAME))
                .isEqualTo(TRUSTED_PARTNER_APPS_SIP);
    }

    @Test
    public void testGetSipInstanceName_unknownApp() {
        ExtendedMockito.doReturn(false).when(
                () -> StableFlags.get(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED));
        assertThat(sSipRunner.getSipInstanceName("unknown_app_name"))
                .isEqualTo(UNKNOWN_APPS_SIP);
    }

    @Test
    public void testCheckIsolatedService() throws Exception {
        ServiceInfo si = new ServiceInfo();
        si.flags = si.FLAG_ISOLATED_PROCESS;
        sSipRunner.checkIsolatedService(new ComponentName("a", "b"), si);  // does not throw
    }

    @Test
    public void testCheckIsolatedServiceThrowsIfIsolatedProcessTagNotInManifest()
            throws Exception {
        ServiceInfo si = new ServiceInfo();
        si.flags = 0;
        assertThrows(
                OdpServiceException.class,
                () -> sSipRunner.checkIsolatedService(new ComponentName("a", "b"), si));
    }

    @Test
    public void testIsSharedIsolatedProcessRequested() {
        assumeTrue(SdkLevel.isAtLeastU());
        ServiceInfo si = new ServiceInfo();
        si.flags = si.FLAG_ISOLATED_PROCESS;
        assertFalse(sSipRunner.isSharedIsolatedProcessRequested(si));
        si.flags |= si.FLAG_ALLOW_SHARED_ISOLATED_PROCESS;
        assertTrue(sSipRunner.isSharedIsolatedProcessRequested(si));
    }

    @Test
    public void testIsSharedIsolatedProcessRequestedAlwaysFalseOnT() {
        assumeTrue(SdkLevel.isAtLeastT() && !SdkLevel.isAtLeastU());
        ServiceInfo si = new ServiceInfo();
        si.flags = si.FLAG_ISOLATED_PROCESS;
        assertFalse(sSipRunner.isSharedIsolatedProcessRequested(si));
        si.flags |= si.FLAG_ALLOW_SHARED_ISOLATED_PROCESS;
        assertFalse(sSipRunner.isSharedIsolatedProcessRequested(si));
    }

    @Test
    @Ignore("TODO: b/342672147 - temporary disable failing tests.")
    public void testLoadIsolatedService_packageManagerNameNotFoundException_failedFuture()
            throws Exception {
        // When the package is not found during loading IsolatedService, returned future fails
        // with appropriate OdpServiceException.
        ListenableFuture<IsolatedServiceInfo> resultFuture =
                mInstanceUnderTest.loadIsolatedService(
                        "AppRequestTask",
                        new ComponentName(mContext.getPackageName(), "nonExistService"));
        Futures.addCallback(resultFuture, mTestCallback, mTestInjector.getExecutor());

        mCountDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(resultFuture.isDone()).isTrue();
        ExecutionException outException = assertThrows(ExecutionException.class, resultFuture::get);
        assertThat(outException.getCause()).isInstanceOf(OdpServiceException.class);
        assertThat(((OdpServiceException) outException.getCause()).getErrorCode())
                .isEqualTo(Constants.STATUS_ISOLATED_SERVICE_LOADING_FAILED);
    }

    @Test
    public void testRunIsolatedService_serviceBinderException_failedFutureOdpServiceException()
            throws Exception {
        // When the getting the IsolatedServiceBinder throws an exception the returned future fails
        // with the loading service failed error code.
        doThrow(new RuntimeException("Unexpected exception in binder!"))
                .when(mIsolatedServiceInfo)
                .getIsolatedServiceBinder();

        ListenableFuture<Bundle> resultFuture =
                mInstanceUnderTest.runIsolatedService(
                        mIsolatedServiceInfo, Constants.API_NAME_SERVICE_ON_EXECUTE, new Bundle());
        Futures.addCallback(resultFuture, mTestCallback, mTestInjector.getExecutor());

        mCountDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(resultFuture.isDone()).isTrue();
        ExecutionException outException = assertThrows(ExecutionException.class, resultFuture::get);
        assertThat(outException.getCause()).isInstanceOf(OdpServiceException.class);
        assertThat(((OdpServiceException) outException.getCause()).getErrorCode())
                .isEqualTo(Constants.STATUS_ISOLATED_SERVICE_LOADING_FAILED);
    }

    @Test
    public void testRunIsolatedService_serviceBinderError_failedFutureOdpServiceException()
            throws Exception {
        // When the service binder returns an isolatedServiceError code the returned future
        // fails with appropriate IsolatedServiceException
        int isolatedServiceErrorCode = 6;
        doReturn(mAbstractServiceBinder).when(mIsolatedServiceInfo).getIsolatedServiceBinder();
        doReturn(new TestServiceBinder(Constants.STATUS_SERVICE_FAILED, isolatedServiceErrorCode))
                .when(mAbstractServiceBinder)
                .getService(any());

        ListenableFuture<Bundle> resultFuture =
                mInstanceUnderTest.runIsolatedService(
                        mIsolatedServiceInfo, Constants.API_NAME_SERVICE_ON_EXECUTE, new Bundle());
        Futures.addCallback(resultFuture, mTestCallback, mTestInjector.getExecutor());

        mCountDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(resultFuture.isDone()).isTrue();
        ExecutionException outException = assertThrows(ExecutionException.class, resultFuture::get);
        assertThat(outException.getCause()).isInstanceOf(OdpServiceException.class);
        OdpServiceException odpServiceException = (OdpServiceException) outException.getCause();
        assertThat(odpServiceException.getErrorCode()).isEqualTo(Constants.STATUS_SERVICE_FAILED);
        assertThat(odpServiceException.getCause()).isInstanceOf(IsolatedServiceException.class);
        assertThat(((IsolatedServiceException) odpServiceException.getCause()).getErrorCode())
                .isEqualTo(isolatedServiceErrorCode);
    }

    @Test
    public void testRunIsolatedService_serviceBinderTimeout_failedFutureTimeoutException()
            throws Exception {
        // When the service binder times out without responding the future fails with a timeout
        // exception.
        doReturn(mAbstractServiceBinder).when(mIsolatedServiceInfo).getIsolatedServiceBinder();
        doReturn(new FakeTimeoutServiceBinder()).when(mAbstractServiceBinder).getService(any());

        ListenableFuture<Bundle> resultFuture =
                mInstanceUnderTest.runIsolatedService(
                        mIsolatedServiceInfo, Constants.API_NAME_SERVICE_ON_EXECUTE, new Bundle());
        Futures.addCallback(resultFuture, mTestCallback, mTestInjector.getExecutor());
        // For a GC to cause the callbackToFutureAdapter to throw FutureGarbageCollectedException
        forceGc();

        mCountDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(resultFuture.isDone()).isTrue();
        ExecutionException outException = assertThrows(ExecutionException.class, resultFuture::get);
        assertThat(outException.getCause()).isInstanceOf(TimeoutException.class);
    }

    private static void forceGc() {
        System.gc();
        System.runFinalization();
        System.gc();
    }

    private static final class TestServiceBinder extends IIsolatedService.Stub {
        private final int mErrorCode;
        private final int mIsolatedServiceErrorCode;

        private TestServiceBinder(int errorCode, int isolatedServiceErrorCode) {
            mErrorCode = errorCode;
            mIsolatedServiceErrorCode = isolatedServiceErrorCode;
        }

        @Override
        public void onRequest(
                int operationCode,
                @NonNull Bundle params,
                @NonNull IIsolatedServiceCallback resultCallback) {
            try {
                resultCallback.onError(mErrorCode, mIsolatedServiceErrorCode, null);
            } catch (Exception e) {

            }
        }
    }

    private static final class FakeTimeoutServiceBinder extends IIsolatedService.Stub {
        private FakeTimeoutServiceBinder() {}

        @Override
        public void onRequest(
                int operationCode,
                @NonNull Bundle params,
                @NonNull IIsolatedServiceCallback resultCallback) {
            // Does nothing no-op
        }
    }
}
