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
package com.android.ondevicepersonalization.services;

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.NOTIFY_MEASUREMENT_EVENT;

import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;

import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.ondevicepersonalization.CalleeMetadata;
import android.adservices.ondevicepersonalization.CallerMetadata;
import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.ExecuteInIsolatedServiceRequest;
import android.adservices.ondevicepersonalization.ExecuteOptionsParcel;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.view.SurfaceControlViewHost;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.odp.module.common.DeviceUtils;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;
import com.android.ondevicepersonalization.services.data.user.UserDataCollectionJobService;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.enrollment.PartnerEnrollmentChecker;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJobService;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationManagingServiceTest {
    @Rule public final ServiceTestRule serviceRule = new ServiceTestRule();
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private OnDevicePersonalizationManagingServiceDelegate mService;
    @Mock private UserPrivacyStatus mUserPrivacyStatus;
    @Mock private MobileDataDownload mMockMdd;
    @Mock private Flags mMockFlags;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .spyStatic(UserPrivacyStatus.class)
                    .spyStatic(DeviceUtils.class)
                    .spyStatic(OnDevicePersonalizationMaintenanceJobService.class)
                    .spyStatic(UserDataCollectionJobService.class)
                    .spyStatic(MobileDataDownloadFactory.class)
                    .spyStatic(PartnerEnrollmentChecker.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setup() throws Exception {
        mService = new OnDevicePersonalizationManagingServiceDelegate(mContext, new TestInjector());
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getMaxIntValuesLimit()).thenReturn(100);
        doNothing().when(mMockFlags).setStableFlags();
        ExtendedMockito.doReturn(true).when(() -> DeviceUtils.isOdpSupported(any()));
        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(true).when(mUserPrivacyStatus).isMeasurementEnabled();
        doReturn(true).when(mUserPrivacyStatus).isProtectedAudienceEnabled();
        when(mContext.checkCallingPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        ExtendedMockito.doReturn(SCHEDULING_RESULT_CODE_SUCCESSFUL)
                .when(
                        () ->
                                OnDevicePersonalizationMaintenanceJobService.schedule(
                                        any(), anyBoolean()));
        ExtendedMockito.doReturn(1).when(() -> UserDataCollectionJobService.schedule(any()));
        ExtendedMockito.doReturn(mMockMdd).when(() -> MobileDataDownloadFactory.getMdd(any()));
        doReturn(immediateVoidFuture()).when(mMockMdd).schedulePeriodicBackgroundTasks();
        ExtendedMockito.doReturn(true)
                .when(() -> PartnerEnrollmentChecker.isCallerAppEnrolled(any()));
        ExtendedMockito.doReturn(true)
                .when(() -> PartnerEnrollmentChecker.isIsolatedServiceEnrolled(any()));
    }

    @Test
    public void testVersion() throws Exception {
        assertEquals(mService.getVersion(), "1.0");
    }

    @Test
    public void testEnabledGlobalKillSwitchOnExecute() throws Exception {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        var callback = new ExecuteCallback();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testUnsupportedDeviceOnExecute() throws Exception {
        ExtendedMockito.doReturn(false).when(() -> DeviceUtils.isOdpSupported(any()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                new ExecuteCallback()));
    }

    @Test
    public void testExecuteInvokesAppRequestFlow() throws Exception {
        var callback = new ExecuteCallback();
        mService.execute(
                mContext.getPackageName(),
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationHandler"),
                createWrappedAppParams(),
                new CallerMetadata.Builder().build(),
                ExecuteOptionsParcel.DEFAULT,
                callback);
        callback.await();
        assertTrue(callback.mWasInvoked);
    }

    @Test
    public void testExecuteInvokesAppRequestFlowWithBestValue() throws Exception {
        var callback = new ExecuteCallback();
        ExecuteOptionsParcel options =
                new ExecuteOptionsParcel(
                        ExecuteInIsolatedServiceRequest.OutputSpec.OUTPUT_TYPE_BEST_VALUE, 50);
        mService.execute(
                mContext.getPackageName(),
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationHandler"),
                createWrappedAppParams(),
                new CallerMetadata.Builder().build(),
                options,
                callback);
        callback.await();
        assertTrue(callback.mWasInvoked);
    }

    @Test
    public void testExecuteInvokesAppRequestFlowWithBestValue_exceedLimit() throws Exception {
        var callback = new ExecuteCallback();
        ExecuteOptionsParcel options =
                new ExecuteOptionsParcel(
                        ExecuteInIsolatedServiceRequest.OutputSpec.OUTPUT_TYPE_BEST_VALUE, 150);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                options,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfAppPackageNameIncorrect() throws Exception {
        var callback = new ExecuteCallback();
        assertThrows(
                SecurityException.class,
                () ->
                        mService.execute(
                                "abc",
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfAppPackageNameNull() throws Exception {
        var callback = new ExecuteCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.execute(
                                null,
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfAppPackageNameMissing() throws Exception {
        var callback = new ExecuteCallback();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mService.execute(
                                "",
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfHandlerMissing() throws Exception {
        var callback = new ExecuteCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                null,
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfServicePackageMissing() throws Exception {
        var callback = new ExecuteCallback();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName("", "ServiceClass"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfServiceClassMissing() throws Exception {
        var callback = new ExecuteCallback();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName("com.test.TestPackage", ""),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfMetadataMissing() throws Exception {
        var callback = new ExecuteCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                null,
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfCallbackMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                null));
    }

    @Test
    public void testExecuteThrowsIfCallerNotEnrolled() {
        var callback = new ExecuteCallback();
        ExtendedMockito.doReturn(false)
                .when(() -> PartnerEnrollmentChecker.isCallerAppEnrolled(any()));

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testExecuteThrowsIfIsolatedServiceNotEnrolled() {
        var callback = new ExecuteCallback();
        ExtendedMockito.doReturn(false)
                .when(() -> PartnerEnrollmentChecker.isIsolatedServiceEnrolled(any()));

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.execute(
                                mContext.getPackageName(),
                                new ComponentName(
                                        mContext.getPackageName(),
                                        "com.test.TestPersonalizationHandler"),
                                createWrappedAppParams(),
                                new CallerMetadata.Builder().build(),
                                ExecuteOptionsParcel.DEFAULT,
                                callback));
    }

    @Test
    public void testEnabledGlobalKillSwitchOnRequestSurfacePackage() throws Exception {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken",
                                new Binder(),
                                0,
                                100,
                                50,
                                new CallerMetadata.Builder().build(),
                                callback));
    }

    @Test
    public void testUnsupportedDeviceOnRequestSurfacePackage() throws Exception {
        ExtendedMockito.doReturn(false).when(() -> DeviceUtils.isOdpSupported(any()));
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken",
                                new Binder(),
                                0,
                                100,
                                50,
                                new CallerMetadata.Builder().build(),
                                callback));
    }

    @Test
    public void testRequestSurfacePackageInvokesRenderFlow() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        mService.requestSurfacePackage(
                "resultToken",
                new Binder(),
                0,
                100,
                50,
                new CallerMetadata.Builder().build(),
                callback);
        callback.await();
        assertTrue(callback.mWasInvoked);
    }

    @Test
    public void testRequestSurfacePackageThrowsIfSlotResultTokenMissing() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.requestSurfacePackage(
                                null,
                                new Binder(),
                                0,
                                100,
                                50,
                                new CallerMetadata.Builder().build(),
                                callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfHostTokenMissing() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken",
                                null,
                                0,
                                100,
                                50,
                                new CallerMetadata.Builder().build(),
                                callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfDisplayIdInvalid() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken",
                                new Binder(),
                                -1,
                                100,
                                50,
                                new CallerMetadata.Builder().build(),
                                callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfWidthInvalid() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken",
                                new Binder(),
                                0,
                                0,
                                50,
                                new CallerMetadata.Builder().build(),
                                callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfHeightInvalid() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken",
                                new Binder(),
                                0,
                                100,
                                0,
                                new CallerMetadata.Builder().build(),
                                callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfMetadataMissing() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken", new Binder(), 0, 100, 50, null, callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfCallbackMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        mService.requestSurfacePackage(
                                "resultToken",
                                new Binder(),
                                0,
                                100,
                                50,
                                new CallerMetadata.Builder().build(),
                                null));
    }

    @Test
    public void testEnabledGlobalKillSwitchOnRegisterMeasurementEvent() throws Exception {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(true);
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.registerMeasurementEvent(
                                Constants.MEASUREMENT_EVENT_TYPE_WEB_TRIGGER,
                                Bundle.EMPTY,
                                new CallerMetadata.Builder().build(),
                                new RegisterMeasurementEventCallback()));
    }

    @Test
    public void testUnsupportedDeviceOnRegisterMeasurementEvent() throws Exception {
        ExtendedMockito.doReturn(false).when(() -> DeviceUtils.isOdpSupported(any()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.registerMeasurementEvent(
                                Constants.MEASUREMENT_EVENT_TYPE_WEB_TRIGGER,
                                Bundle.EMPTY,
                                new CallerMetadata.Builder().build(),
                                new RegisterMeasurementEventCallback()));
    }

    @Test
    public void testRegisterMeasurementEventPermissionDenied() throws Exception {
        when(mContext.checkCallingPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThrows(
                SecurityException.class,
                () ->
                        mService.registerMeasurementEvent(
                                Constants.MEASUREMENT_EVENT_TYPE_WEB_TRIGGER,
                                Bundle.EMPTY,
                                new CallerMetadata.Builder().build(),
                                new RegisterMeasurementEventCallback()));
    }

    @Test
    public void testRegisterMeasurementEventInvokesWebTriggerFlow() throws Exception {
        var callback = new RegisterMeasurementEventCallback();
        mService.registerMeasurementEvent(
                Constants.MEASUREMENT_EVENT_TYPE_WEB_TRIGGER,
                Bundle.EMPTY,
                new CallerMetadata.Builder().build(),
                callback);
        callback.await();
        assertTrue(callback.mWasInvoked);
    }

    @Test
    public void testWithBoundService() throws TimeoutException {
        Intent serviceIntent =
                new Intent(mContext, OnDevicePersonalizationManagingServiceImpl.class);
        IBinder binder = serviceRule.bindService(serviceIntent);
        assertTrue(binder instanceof OnDevicePersonalizationManagingServiceDelegate);
    }

    @Test
    public void testJobRestoring() {
        OnDevicePersonalizationManagingServiceImpl service =
                new OnDevicePersonalizationManagingServiceImpl(Runnable::run);
        service.onCreate();
        Intent serviceIntent =
                new Intent(mContext, OnDevicePersonalizationManagingServiceImpl.class);
        IBinder binder = service.onBind(serviceIntent);
        assertTrue(binder instanceof OnDevicePersonalizationManagingServiceDelegate);
        ExtendedMockito.verify(
                () -> OnDevicePersonalizationMaintenanceJobService.schedule(any(), anyBoolean()));
        ExtendedMockito.verify(() -> UserDataCollectionJobService.schedule(any()), times(1));
        verify(mMockMdd).schedulePeriodicBackgroundTasks();
    }

    private Bundle createWrappedAppParams() throws Exception {
        Bundle wrappedParams = new Bundle();
        ByteArrayParceledSlice buffer =
                new ByteArrayParceledSlice(
                        PersistableBundleUtils.toByteArray(PersistableBundle.EMPTY));
        wrappedParams.putParcelable(Constants.EXTRA_APP_PARAMS_SERIALIZED, buffer);
        return wrappedParams;
    }

    static class ExecuteCallback extends IExecuteCallback.Stub {
        public boolean mWasInvoked = false;
        public boolean mSuccess = false;
        public boolean mError = false;
        public int mErrorCode = 0;
        public int mIsolatedServiceErrorCode = 0;
        public byte[] mSerializedException = null;
        public String mToken = null;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSuccess(Bundle bundle, CalleeMetadata calleeMetadata) {
            if (bundle != null) {
                mToken = bundle.getString(Constants.EXTRA_SURFACE_PACKAGE_TOKEN_STRING);
            }
            mWasInvoked = true;
            mSuccess = true;
            mLatch.countDown();
        }

        @Override
        public void onError(
                int errorCode,
                int isolatedServiceErrorCode,
                byte[] serializedException,
                CalleeMetadata calleeMetadata) {
            mWasInvoked = true;
            mError = true;
            mErrorCode = errorCode;
            mIsolatedServiceErrorCode = isolatedServiceErrorCode;
            mSerializedException = serializedException;
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await();
        }
    }

    static class RequestSurfacePackageCallback extends IRequestSurfacePackageCallback.Stub {
        public boolean mWasInvoked = false;
        public boolean mSuccess = false;
        public boolean mError = false;
        public int mErrorCode = 0;
        public int mIsolatedServiceErrorCode = 0;
        public byte[] mSerializedException = null;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSuccess(
                SurfaceControlViewHost.SurfacePackage s, CalleeMetadata calleeMetadata) {
            mWasInvoked = true;
            mSuccess = true;
            mLatch.countDown();
        }

        @Override
        public void onError(
                int errorCode,
                int isolatedServiceErrorCode,
                byte[] serializedException,
                CalleeMetadata calleeMetadata) {
            mWasInvoked = true;
            mError = true;
            mErrorCode = errorCode;
            mIsolatedServiceErrorCode = isolatedServiceErrorCode;
            mSerializedException = serializedException;
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await();
        }
    }

    static class RegisterMeasurementEventCallback extends IRegisterMeasurementEventCallback.Stub {
        public boolean mError = false;
        public boolean mSuccess = false;
        public boolean mWasInvoked = false;
        public int mErrorCode = 0;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSuccess(CalleeMetadata calleeMetadata) {
            mWasInvoked = true;
            mSuccess = true;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode, CalleeMetadata calleeMetadata) {
            mWasInvoked = true;
            mError = true;
            mErrorCode = errorCode;
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await();
        }
    }

    private class TestInjector extends OnDevicePersonalizationManagingServiceDelegate.Injector {
        @Override
        Flags getFlags() {
            return mMockFlags;
        }
    }
}
