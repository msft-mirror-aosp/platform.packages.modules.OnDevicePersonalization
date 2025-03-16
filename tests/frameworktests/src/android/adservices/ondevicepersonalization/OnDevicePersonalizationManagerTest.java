/*
 * Copyright 2024 The Android Open Source Project
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
package android.adservices.ondevicepersonalization;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IIsFeatureEnabledCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.ExceptionInfo;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(Parameterized.class)
public final class OnDevicePersonalizationManagerTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationManagerTest";
    private static final String KEY_OP = "op";
    private static final String KEY_STATUS_CODE = "status";
    private static final String KEY_SERVICE_ERROR_CODE = "serviceerror";
    private static final String KEY_ERROR_MESSAGE = "errormessage";
    private static final int BEST_VALUE = 10;
    private static final ComponentName TEST_SERVICE_COMPONENT_NAME =
            ComponentName.createRelative("com.example.service", ".Example");
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final TestServiceBinder mTestBinder =
            new TestServiceBinder(
                    IOnDevicePersonalizationManagingService.Stub.asInterface(new TestService()));
    private final OnDevicePersonalizationManager mManager =
            new OnDevicePersonalizationManager(mContext, mTestBinder);

    private volatile boolean mLogApiStatsCalled = false;

    @Parameterized.Parameter(0)
    public boolean mRunExecuteInIsolatedService;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + SdkLevel.isAtLeastU());
    }

    @Test
    public void testExecuteSuccess() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "ok");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertTrue(receiver.isSuccess());
        assertFalse(receiver.isError());
        assertNotNull(receiver.getResult());
        if (mRunExecuteInIsolatedService) {
            ExecuteInIsolatedServiceResponse response =
                    (ExecuteInIsolatedServiceResponse) receiver.getResult();
            assertThat(response.getSurfacePackageToken().getTokenString()).isEqualTo("aaaa");
            assertThat(response.getBestValue()).isEqualTo(-1);
        } else {
            ExecuteResult response = (ExecuteResult) receiver.getResult();
            assertThat(response.getSurfacePackageToken().getTokenString()).isEqualTo("aaaa");
            assertThat(response.getOutputData()).isNull();
        }
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteSuccessWithBestValueSpec() throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_OP, "best_value");
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(TEST_SERVICE_COMPONENT_NAME)
                        .setAppParams(bundle)
                        .setOutputSpec(
                                ExecuteInIsolatedServiceRequest.OutputSpec.buildBestValueSpec(100))
                        .build();

        mManager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertTrue(receiver.isSuccess());
        assertFalse(receiver.isError());
        assertNotNull(receiver.getResult());

        ExecuteInIsolatedServiceResponse response = receiver.getResult();
        assertThat(response.getSurfacePackageToken().getTokenString()).isEqualTo("aaaa");
        assertThat(response.getBestValue()).isEqualTo(BEST_VALUE);
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteUnknownError() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof IllegalStateException);
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteServiceError() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_SERVICE_FAILED);
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteErrorWithCode() throws Exception {
        int isolatedServiceErrorCode = 42;
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_SERVICE_FAILED);
        params.putInt(KEY_SERVICE_ERROR_CODE, isolatedServiceErrorCode);
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(
                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED,
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        assertTrue(receiver.getException().getCause() instanceof IsolatedServiceException);
        assertEquals(
                isolatedServiceErrorCode,
                ((IsolatedServiceException) receiver.getException().getCause()).getErrorCode());
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteErrorWithMessage() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_SERVICE_FAILED);
        params.putString(KEY_ERROR_MESSAGE, "TestErrorMessage");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(
                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED,
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        Throwable cause = receiver.getException().getCause();
        assertNotNull(cause);
        assertThat(cause.getMessage()).containsMatch(".*RuntimeException.*TestErrorMessage.*");
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteManifestParsingError() throws Exception {
        // The manifest parsing failure gets translated back to PackageManager.NameNotFound
        // when the legacy execute API is called. The new execute API returns targeted error code.
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_MANIFEST_PARSING_FAILED);
        params.putString(KEY_ERROR_MESSAGE, "Failed parsing manifest");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        Throwable cause = receiver.getException().getCause();
        assertNotNull(cause);
        assertThat(cause.getMessage()).containsMatch(".*RuntimeException.*parsing.*");
        assertTrue(mLogApiStatsCalled);
        if (mRunExecuteInIsolatedService) {
            assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
            assertEquals(
                    OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_MANIFEST_PARSING_FAILED,
                    ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        } else {
            assertTrue(receiver.getException() instanceof PackageManager.NameNotFoundException);
        }
    }

    @Test
    public void testExecuteManifestMisconfigurationError() throws Exception {
        // The manifest misconfigured failure gets  translated back to Class not found
        // when the legacy execute API is used. The new execute API returns the targeted error code.
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_MANIFEST_MISCONFIGURED);
        params.putString(KEY_ERROR_MESSAGE, "Failed parsing manifest");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        Throwable cause = receiver.getException().getCause();
        assertNotNull(cause);
        assertThat(cause.getMessage()).containsMatch(".*RuntimeException.*parsing.*");
        assertTrue(mLogApiStatsCalled);
        if (mRunExecuteInIsolatedService) {
            assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
            assertEquals(
                    OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_MANIFEST_PARSING_FAILED,
                    ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        } else {
            assertTrue(receiver.getException() instanceof ClassNotFoundException);
        }
    }

    @Test
    public void testExecuteServiceTimeoutError() throws Exception {
        // The service timeout failure gets exposed via corresponding OdpException
        // when the new execute API is used.
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_ISOLATED_SERVICE_TIMEOUT);
        params.putString(KEY_ERROR_MESSAGE, "Service timeout");
        var receiver = new ResultReceiver<ExecuteResult>();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        Throwable cause = receiver.getException().getCause();
        assertNotNull(cause);
        assertThat(cause.getMessage()).containsMatch(".*RuntimeException.*timeout.*");
        assertTrue(mLogApiStatsCalled);
        if (mRunExecuteInIsolatedService) {
            assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
            assertEquals(
                    OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_TIMEOUT,
                    ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        } else {
            assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
            assertEquals(
                    OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED,
                    ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        }
    }

    @Test
    public void testExecuteServiceLoadingError() throws Exception {
        // The service loading failure gets exposed via corresponding OdpException
        // when the new execute API is used.
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_ISOLATED_SERVICE_LOADING_FAILED);
        params.putString(KEY_ERROR_MESSAGE, "Service loading failed.");
        var receiver = new ResultReceiver<ExecuteResult>();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        Throwable cause = receiver.getException().getCause();
        assertNotNull(cause);
        assertThat(cause.getMessage()).containsMatch(".*RuntimeException.*loading.*");
        assertTrue(mLogApiStatsCalled);
        if (mRunExecuteInIsolatedService) {
            assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
            assertEquals(
                    OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_LOADING_FAILED,
                    ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        } else {
            assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
            assertEquals(
                    OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED,
                    ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        }
    }

    @Test
    public void testExecuteCatchesIaeFromService() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "iae");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof IllegalArgumentException);
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteCatchesNpeFromService() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "npe");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof NullPointerException);
        assertTrue(mLogApiStatsCalled);
    }

    @Test
    public void testExecuteCatchesOtherExceptions() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "ise");
        var receiver = new ResultReceiver();

        runExecute(params, receiver);

        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof IllegalStateException);
        assertTrue(mLogApiStatsCalled);
    }

    private void runExecute(PersistableBundle params, ResultReceiver receiver) {
        if (mRunExecuteInIsolatedService) {
            ExecuteInIsolatedServiceRequest request =
                    new ExecuteInIsolatedServiceRequest.Builder(TEST_SERVICE_COMPONENT_NAME)
                            .setAppParams(params)
                            .build();
            mManager.executeInIsolatedService(
                    request, Executors.newSingleThreadExecutor(), receiver);
        } else {
            mManager.execute(
                    TEST_SERVICE_COMPONENT_NAME,
                    params,
                    Executors.newSingleThreadExecutor(),
                    receiver);
        }
    }

    private class TestService extends IOnDevicePersonalizationManagingService.Stub {
        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public void execute(
                String callingPackageName,
                ComponentName handler,
                Bundle wrappedParams,
                CallerMetadata metadata,
                ExecuteOptionsParcel options,
                IExecuteCallback callback) {
            try {
                PersistableBundle params;
                String op;
                try {
                    ByteArrayParceledSlice paramsBuffer =
                            wrappedParams.getParcelable(
                                    Constants.EXTRA_APP_PARAMS_SERIALIZED,
                                    ByteArrayParceledSlice.class);
                    params = PersistableBundleUtils.fromByteArray(paramsBuffer.getByteArray());
                    op = params.getString(KEY_OP);
                } catch (Exception e) {
                    Log.e(TAG, "error extracting params", e);
                    throw new IllegalStateException(e);
                }
                if (op.equals("ok")) {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.EXTRA_SURFACE_PACKAGE_TOKEN_STRING, "aaaa");
                    callback.onSuccess(
                            bundle,
                            new CalleeMetadata.Builder()
                                    .setCallbackInvokeTimeMillis(SystemClock.elapsedRealtime())
                                    .build());
                } else if (options.getOutputType()
                        == ExecuteInIsolatedServiceRequest.OutputSpec.OUTPUT_TYPE_BEST_VALUE) {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.EXTRA_SURFACE_PACKAGE_TOKEN_STRING, "aaaa");
                    bundle.putInt(Constants.EXTRA_OUTPUT_BEST_VALUE, BEST_VALUE);
                    callback.onSuccess(
                            bundle,
                            new CalleeMetadata.Builder()
                                    .setCallbackInvokeTimeMillis(SystemClock.elapsedRealtime())
                                    .build());
                } else if (op.equals("error")) {
                    int statusCode =
                            params.getInt(KEY_STATUS_CODE, Constants.STATUS_INTERNAL_ERROR);
                    int serviceErrorCode = params.getInt(KEY_SERVICE_ERROR_CODE, 0);
                    String errorMessage = params.getString(KEY_ERROR_MESSAGE);
                    callback.onError(
                            statusCode,
                            serviceErrorCode,
                            ExceptionInfo.toByteArray(new RuntimeException(errorMessage), 3),
                            new CalleeMetadata.Builder()
                                    .setCallbackInvokeTimeMillis(SystemClock.elapsedRealtime())
                                    .build());
                } else if (op.equals("iae")) {
                    throw new IllegalArgumentException();
                } else if (op.equals("npe")) {
                    throw new NullPointerException();
                } else if (op.equals("ise")) {
                    throw new IllegalStateException();
                } else {
                    throw new UnsupportedOperationException();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "callback error", e);
            }
        }

        @Override
        public void requestSurfacePackage(
                String surfacePackageToken,
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                CallerMetadata metadata,
                IRequestSurfacePackageCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerMeasurementEvent(
                int eventType,
                Bundle params,
                CallerMetadata metadata,
                IRegisterMeasurementEventCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void isFeatureEnabled(
                String featureName,
                CallerMetadata metadata,
                IIsFeatureEnabledCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logApiCallStats(
                String sdkPackageName,
                int apiName,
                long latencyMillis,
                long rpcCallLatencyMillis,
                long rpcReturnLatencyMillis,
                int responseCode) {
            if (!sdkPackageName.equals("com.example.service")) {
                return;
            }
            mLogApiStatsCalled = true;
        }
    }

    private static class TestServiceBinder
            extends AbstractServiceBinder<IOnDevicePersonalizationManagingService> {
        private final IOnDevicePersonalizationManagingService mService;

        TestServiceBinder(IOnDevicePersonalizationManagingService service) {
            mService = service;
        }

        @Override
        public IOnDevicePersonalizationManagingService getService(Executor executor) {
            return mService;
        }

        @Override
        public void unbindFromService() {}
    }
}
