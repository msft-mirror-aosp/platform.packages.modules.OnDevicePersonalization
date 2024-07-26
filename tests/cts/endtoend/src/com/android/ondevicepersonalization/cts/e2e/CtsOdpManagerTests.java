/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.ondevicepersonalization.cts.e2e;

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.GRANULAR_EXCEPTION_ERROR_CODES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationException;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.adservices.ondevicepersonalization.SurfacePackageToken;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.PersistableBundle;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;

/** CTS Test cases for OnDevicePersonalizationManager APIs. */
@RunWith(Parameterized.class)
public class CtsOdpManagerTests {

    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";
    private static final int LARGE_BLOB_SIZE = 10485760;
    private static final int DELAY_MILLIS = 2000;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    @Rule public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{true}, {false}});
    }

    @Before
    public void setUp() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());

        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "debug.validate_rendering_config_keys "
                        + false);
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "isolated_service_allow_list "
                        + "com.android.ondevicepersonalization.testing.sampleservice,"
                        + "com.example.odptargetingapp2");
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "isolated_service_debugging_enabled "
                        + true);
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "output_data_allow_list "
                        + mContext.getPackageName()
                        + ";com.android.ondevicepersonalization.testing.sampleservice");
    }

    @After
    public void reset() {
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "isolated_service_allow_list "
                        + "null");
        ShellUtils.runShellCommand("device_config delete output_data_allow_list");

        ShellUtils.runShellCommand(
                "am force-stop com.google.android.ondevicepersonalization.services");
        ShellUtils.runShellCommand("am force-stop com.android.ondevicepersonalization.services");
    }

    @Test
    public void testExecuteThrowsIfComponentNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.execute(
                                null,
                                PersistableBundle.EMPTY,
                                Executors.newSingleThreadExecutor(),
                                new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecuteThrowsIfParamsMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.execute(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                                null,
                                Executors.newSingleThreadExecutor(),
                                new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecuteThrowsIfExecutorMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.execute(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                                PersistableBundle.EMPTY,
                                null,
                                new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecuteThrowsIfReceiverMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.execute(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                                PersistableBundle.EMPTY,
                                Executors.newSingleThreadExecutor(),
                                null));
    }

    @Test
    public void testExecuteThrowsIfPackageNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        manager.execute(
                                new ComponentName("", SERVICE_CLASS),
                                PersistableBundle.EMPTY,
                                Executors.newSingleThreadExecutor(),
                                new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecuteThrowsIfClassNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        manager.execute(
                                new ComponentName(SERVICE_PACKAGE, ""),
                                PersistableBundle.EMPTY,
                                Executors.newSingleThreadExecutor(),
                                new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecuteReturnsIllegalStateIfServiceNotEnrolled() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        manager.execute(
                new ComponentName("somepackage", "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof IllegalStateException);
    }

    @Test
    @DisableCompatChanges({GRANULAR_EXCEPTION_ERROR_CODES})
    public void testExecuteReturnsNameNotFoundIfServiceNotInstalled() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        manager.execute(
                new ComponentName("com.example.odptargetingapp2", "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof NameNotFoundException);
    }

    @Test
    @EnableCompatChanges({GRANULAR_EXCEPTION_ERROR_CODES})
    public void testExecuteReturnsOdpExceptionIfServiceNotInstalledGranularErrorCodesEnabled()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        manager.execute(
                new ComponentName("com.example.odptargetingapp2", "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(
                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_MANIFEST_PARSING_FAILED,
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
    }

    @Test
    @DisableCompatChanges({GRANULAR_EXCEPTION_ERROR_CODES})
    public void testExecuteReturnsClassNotFoundIfServiceClassNotFound()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof ClassNotFoundException);
    }

    @Test
    @EnableCompatChanges({GRANULAR_EXCEPTION_ERROR_CODES})
    public void testExecuteReturnsOdpExceptionIfServiceClassNotFoundGranularErrorCodesEnabled()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(
                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_MANIFEST_PARSING_FAILED,
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
    }

    @Test
    public void testExecuteNoOp() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        SurfacePackageToken token = receiver.getResult().getSurfacePackageToken();
        assertNull(token);
    }

    @Test
    public void testExecuteWithRenderAndLogging() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
        appParams.putString(SampleServiceApi.KEY_RENDERING_CONFIG_IDS, "id1");
        PersistableBundle logData = new PersistableBundle();
        logData.putString("id", "a1");
        logData.putDouble("val", 5.0);
        appParams.putPersistableBundle(SampleServiceApi.KEY_LOG_DATA, logData);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        SurfacePackageToken token = receiver.getResult().getSurfacePackageToken();
        assertNotNull(token);
    }

    @Test
    public void testExecuteWithRender() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
        appParams.putString(SampleServiceApi.KEY_RENDERING_CONFIG_IDS, "id1");
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        SurfacePackageToken token = receiver.getResult().getSurfacePackageToken();
        assertNotNull(token);
    }

    @Test
    public void testExecuteWithOutputDataDisabled() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RETURN_OUTPUT_DATA);
        appParams.putString(
                SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(new byte[] {'A'}, 0));
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        assertThat(receiver.getResult().getOutputData()).isNull();
    }

    @Test
    public void testExecuteReadRemoteData() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_REMOTE_DATA);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
    }

    @Test
    public void testExecuteReadUserData() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_USER_DATA);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
    }

    @Test
    public void testExecuteWithLogging() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
        PersistableBundle logData = new PersistableBundle();
        logData.putString("id", "a1");
        logData.putDouble("val", 5.0);
        appParams.putPersistableBundle(SampleServiceApi.KEY_LOG_DATA, logData);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        SurfacePackageToken token = receiver.getResult().getSurfacePackageToken();
        assertNull(token);
    }

    @Test
    public void testExecuteReadLog() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        final long now = System.currentTimeMillis();

        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
            PersistableBundle logData = new PersistableBundle();
            logData.putLong(SampleServiceApi.KEY_EXPECTED_LOG_DATA_KEY, now);
            appParams.putPersistableBundle(SampleServiceApi.KEY_LOG_DATA, logData);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOG);
            appParams.putLong(SampleServiceApi.KEY_EXPECTED_LOG_DATA_VALUE, now);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }
    }

    @Test
    public void testExecuteReturnsErrorIfServiceThrows() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_THROW_EXCEPTION);
        appParams.putString(SampleServiceApi.KEY_EXCEPTION_CLASS, "java.lang.NullPointerException");
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.isError());
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode(),
                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED);
    }

    @Test
    public void testExecuteReturnsErrorIfServiceReturnsError() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_FAIL_WITH_ERROR_CODE);
        appParams.putInt(SampleServiceApi.KEY_ERROR_CODE, 10);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.isError());
        assertNull(receiver.getResult());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode(),
                OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED);
    }

    @Test
    public void testExecuteWriteAndReadLocalData() throws InterruptedException {
        final String tableKey = "testKey_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        // Write 1 byte.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putString(
                    SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(new byte[] {'A'}, 0));
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value matches written value.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putString(
                    SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(new byte[] {'A'}, 0));
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        // Remove.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value was removed.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }
    }

    @Test
    public void testExecuteWriteAndReadLargeLocalData() throws InterruptedException {
        final String tableKey = "testKey_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        // Write 10MB.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putString(
                    SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(new byte[] {'A'}, 0));
            appParams.putInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, LARGE_BLOB_SIZE);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value matches written value.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putString(
                    SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(new byte[] {'A'}, 0));
            appParams.putInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, LARGE_BLOB_SIZE);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        // Remove.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value was removed.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }
    }

    @Test
    public void testExecuteSendLargeBlob() throws InterruptedException {
        final String tableKey = "testKey_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_CHECK_VALUE_LENGTH);
        byte[] buffer = new byte[LARGE_BLOB_SIZE];
        for (int i = 0; i < LARGE_BLOB_SIZE; ++i) {
            buffer[i] = 'A';
        }
        appParams.putString(SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(buffer, 0));
        appParams.putInt(SampleServiceApi.KEY_VALUE_LENGTH, LARGE_BLOB_SIZE);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
    }

    @Test
    public void testRunModelInference() throws Exception {
        final String tableKey = "model_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        Uri modelUri =
                Uri.parse(
                        "android.resource://"
                                + ApplicationProvider.getApplicationContext().getPackageName()
                                + "/raw/model");
        Context context = ApplicationProvider.getApplicationContext();
        InputStream in = context.getContentResolver().openInputStream(modelUri);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(buf)) != -1) {
            outputStream.write(buf, 0, bytesRead);
        }
        byte[] buffer = outputStream.toByteArray();
        outputStream.close();
        // Write model to local data.
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putString(
                    SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(buffer, 0));
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }

        Thread.sleep(DELAY_MILLIS);

        // Run model inference
        {
            var receiver = new ResultReceiver<ExecuteResult>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RUN_MODEL_INFERENCE);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putDouble(SampleServiceApi.KEY_INFERENCE_RESULT, 0.5922908);
            manager.execute(
                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                    appParams,
                    Executors.newSingleThreadExecutor(),
                    receiver);
            assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
        }
    }

    @Test
    public void testExecuteWithScheduleFederatedJob() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_SCHEDULE_FEDERATED_JOB);
        appParams.putString(SampleServiceApi.KEY_POPULATION_NAME, "criteo_app_test_task");
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
    }

    @Test
    public void testExecuteWithCancelFederatedJob() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_CANCEL_FEDERATED_JOB);
        appParams.putString(SampleServiceApi.KEY_POPULATION_NAME, "criteo_app_test_task");
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.getErrorMessage(), receiver.isSuccess());
    }
}
