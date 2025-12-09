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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.ondevicepersonalization.ExecuteInIsolatedServiceRequest;
import android.adservices.ondevicepersonalization.ExecuteInIsolatedServiceResponse;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;

/** CTS Test cases for OnDevicePersonalizationManager APIs. */
@RunWith(JUnit4.class)
public class CtsOdpManagerTests {

    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";
    private static final int LARGE_BLOB_SIZE = 30000000;
    private static final int DELAY_MILLIS = 2000;

    private static final String TEST_POPULATION_NAME = "criteo_app_test_task";
    private static final String TEST_WRITE_DATA = Base64.encodeToString(new byte[] {'A'}, 0);

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());
        Assume.assumeTrue(DeviceSupportHelper.isOdpModuleAvailable());

        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + SdkLevel.isAtLeastU());
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
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "Odp__enable_is_feature_enabled "
                        + true);
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
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "Odp__enable_is_feature_enabled "
                        + "null");
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
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
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
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
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
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Ignore("b/377212275")
    public void testExecuteWithOutputDataDisabled() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RETURN_OUTPUT_DATA);
        appParams.putString(SampleServiceApi.KEY_BASE64_VALUE, TEST_WRITE_DATA);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testExecuteWriteAndReadLocalData() throws InterruptedException {
        final String tableKey = "testKey_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        // Write 1 byte.
        writeLocalData(manager, tableKey, /* writeLargeData= */ false);
        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value matches written value.
        readExpectedLocalData(manager, tableKey, TEST_WRITE_DATA, /* expectLargeData= */ false);
        Thread.sleep(DELAY_MILLIS);

        // Remove.
        removeLocalData(manager, tableKey);
        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value was removed.
        checkExpectedMissingLocalData(manager, tableKey);
    }

    @Test
    public void testExecuteWriteAndReadLargeLocalData() throws InterruptedException {
        final String tableKey = "testKey_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        // Write 30MB.
        writeLocalData(manager, tableKey, /* writeLargeData= */ true);
        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value matches written value.
        readExpectedLocalData(manager, tableKey, TEST_WRITE_DATA, /* expectLargeData= */ true);
        Thread.sleep(DELAY_MILLIS);

        // Remove.
        removeLocalData(manager, tableKey);
        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value was removed.
        checkExpectedMissingLocalData(manager, tableKey);
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
            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    public void testExecuteWithScheduleFederatedJob() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = getScheduleFCJobParams(/* useLegacyApi= */ true);

        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FCP_SCHEDULE_WITH_OUTCOME_RECEIVER_ENABLED)
    public void testExecuteWithScheduleFederatedJobWithOutcomeReceiver() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = getScheduleFCJobParams(/* useLegacyApi= */ false);

        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
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
        appParams.putString(SampleServiceApi.KEY_POPULATION_NAME, TEST_POPULATION_NAME);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceThrowsNPEIfExecutorMissing() {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();

        assertThrows(
                NullPointerException.class,
                () -> manager.executeInIsolatedService(request, null, new ResultReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceThrowsNPEIfReceiverMissing() {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.executeInIsolatedService(
                                request, Executors.newSingleThreadExecutor(), null));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceThrowsIAEIfPackageNameMissing() {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(new ComponentName("", SERVICE_CLASS))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        manager.executeInIsolatedService(
                                request,
                                Executors.newSingleThreadExecutor(),
                                new ResultReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceThrowsIAEIfClassNameMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(new ComponentName(SERVICE_PACKAGE, ""))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        manager.executeInIsolatedService(
                                request,
                                Executors.newSingleThreadExecutor(),
                                new ResultReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReturnsIllegalStateIfServiceNotEnrolled()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName("somepackage", "someclass"))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReturnsNameNotFoundIfServiceNotInstalled()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName("com.example.odptargetingapp2", "someclass"))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReturnsManifestParsingErrorIfServiceClassNotFound()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, "someclass"))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceNoOp() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(PersistableBundle.EMPTY)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWithRenderAndLogging() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
        appParams.putString(SampleServiceApi.KEY_RENDERING_CONFIG_IDS, "id1");
        PersistableBundle logData = new PersistableBundle();
        logData.putString("id", "a1");
        logData.putDouble("val", 5.0);
        appParams.putPersistableBundle(SampleServiceApi.KEY_LOG_DATA, logData);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWithRender() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
        appParams.putString(SampleServiceApi.KEY_RENDERING_CONFIG_IDS, "id1");
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReadRemoteData() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_REMOTE_DATA);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReadUserData() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_USER_DATA);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWithLogging() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
        PersistableBundle logData = new PersistableBundle();
        logData.putString("id", "a1");
        logData.putDouble("val", 5.0);
        appParams.putPersistableBundle(SampleServiceApi.KEY_LOG_DATA, logData);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReadLog() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        final long now = System.currentTimeMillis();

        {
            var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
            PersistableBundle logData = new PersistableBundle();
            logData.putLong(SampleServiceApi.KEY_EXPECTED_LOG_DATA_KEY, now);
            appParams.putPersistableBundle(SampleServiceApi.KEY_LOG_DATA, logData);
            ExecuteInIsolatedServiceRequest request =
                    new ExecuteInIsolatedServiceRequest.Builder(
                                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                            .setAppParams(appParams)
                            .build();

            manager.executeInIsolatedService(
                    request, Executors.newSingleThreadExecutor(), receiver);
            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
        }

        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        {
            var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOG);
            appParams.putLong(SampleServiceApi.KEY_EXPECTED_LOG_DATA_VALUE, now);
            ExecuteInIsolatedServiceRequest request =
                    new ExecuteInIsolatedServiceRequest.Builder(
                                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                            .setAppParams(appParams)
                            .build();

            manager.executeInIsolatedService(
                    request, Executors.newSingleThreadExecutor(), receiver);
            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReturnsErrorIfServiceThrows()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_THROW_EXCEPTION);
        appParams.putString(SampleServiceApi.KEY_EXCEPTION_CLASS, "java.lang.NullPointerException");
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceReturnsErrorIfServiceReturnsError()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_FAIL_WITH_ERROR_CODE);
        appParams.putInt(SampleServiceApi.KEY_ERROR_CODE, 10);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWriteAndReadLocalData() throws InterruptedException {
        final String tableKey = "testKey_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        // Write 1 byte.
        writeLocalDataNewExecuteApi(manager, tableKey, /* writeLargeData= */ false);
        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value matches written value.
        readExpectedLocalDataNewExecuteApi(
                manager, tableKey, TEST_WRITE_DATA, /* expectLargeData= */ false);
        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        // Remove.
        removeLocalDataNewExecuteApi(manager, tableKey);
        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value was removed.
        checkExpectedMissingLocalDataNewExecuteApi(manager, tableKey);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWriteAndReadLargeLocalData()
            throws InterruptedException {
        final String tableKey = "testKey_" + System.currentTimeMillis();
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        // Write 30MB.
        writeLocalDataNewExecuteApi(manager, tableKey, /* writeLargeData= */ true);
        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        // Read and check whether value matches written value.
        readExpectedLocalDataNewExecuteApi(
                manager, tableKey, TEST_WRITE_DATA, /* expectLargeData= */ true);
        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        // Remove.
        removeLocalDataNewExecuteApi(manager, tableKey);
        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        checkExpectedMissingLocalDataNewExecuteApi(manager, tableKey);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWithModelInference() throws Exception {
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
            var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putString(
                    SampleServiceApi.KEY_BASE64_VALUE, Base64.encodeToString(buffer, 0));
            ExecuteInIsolatedServiceRequest request =
                    new ExecuteInIsolatedServiceRequest.Builder(
                                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                            .setAppParams(appParams)
                            .build();

            manager.executeInIsolatedService(
                    request, Executors.newSingleThreadExecutor(), receiver);

            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
        }

        // Add delay between writing and read from db to reduce flakiness.
        Thread.sleep(DELAY_MILLIS);

        // Run model inference
        {
            var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString(
                    SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RUN_MODEL_INFERENCE);
            appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
            appParams.putDouble(SampleServiceApi.KEY_INFERENCE_RESULT, 0.5922908);
            ExecuteInIsolatedServiceRequest request =
                    new ExecuteInIsolatedServiceRequest.Builder(
                                    new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                            .setAppParams(appParams)
                            .build();

            manager.executeInIsolatedService(
                    request, Executors.newSingleThreadExecutor(), receiver);

            assertNull(receiver.getResult());
            assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWithScheduleFederatedJob() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(getScheduleFCJobParams(/* useLegacyApi= */ true))
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteInIsolatedServiceWithCancelFederatedJob() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_CANCEL_FEDERATED_JOB);
        appParams.putString(SampleServiceApi.KEY_POPULATION_NAME, TEST_POPULATION_NAME);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IS_FEATURE_ENABLED_API_ENABLED)
    public void testQueryFeatureAvailableApi() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<Integer>();

        manager.queryFeatureAvailability("featureName",
                Executors.newSingleThreadExecutor(),
                receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IS_FEATURE_ENABLED_API_ENABLED)
    public void testQueryFeatureAvailableApiThrowsIfFeatureNameMissing() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<Integer>();

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.queryFeatureAvailability(null,
                                Executors.newSingleThreadExecutor(),
                                receiver));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IS_FEATURE_ENABLED_API_ENABLED)
    public void testQueryFeatureAvailableApiThrowsIfExecutorMissing() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<Integer>();

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.queryFeatureAvailability("featureName",
                                null,
                                receiver));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EXECUTE_IN_ISOLATED_SERVICE_API_ENABLED)
    public void testExecuteNoOutputData() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RETURN_OUTPUT_DATA);
        appParams.putString(SampleServiceApi.KEY_BASE64_VALUE, TEST_WRITE_DATA);

        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IS_FEATURE_ENABLED_API_ENABLED)
    public void testQueryFeatureAvailableApiThrowsIfReceiverMissing() throws Exception {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<Integer>();

        assertThrows(
                NullPointerException.class,
                () ->
                        manager.queryFeatureAvailability("featureName",
                                Executors.newSingleThreadExecutor(),
                                null));
    }

    private static PersistableBundle getScheduleFCJobParams(boolean useLegacyApi) {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE,
                useLegacyApi
                        ? SampleServiceApi.OPCODE_SCHEDULE_FEDERATED_JOB
                        : SampleServiceApi.OPCODE_SCHEDULE_FEDERATED_JOB_V2);
        appParams.putString(SampleServiceApi.KEY_POPULATION_NAME, TEST_POPULATION_NAME);
        return appParams;
    }

    /**
     * Sends a request to the sample service to write to local data using {@code TEST_WRITE_DATA}. *
     *
     * <p>Uses the legacy {@code execute} API.
     */
    private static void writeLocalData(
            OnDevicePersonalizationManager manager, String tableKey, boolean writeLargeData)
            throws InterruptedException {
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();

        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
        appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
        appParams.putString(SampleServiceApi.KEY_BASE64_VALUE, TEST_WRITE_DATA);

        if (writeLargeData) {
            // Set repeat count to inform sample service to write a large blob of data.
            appParams.putInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, LARGE_BLOB_SIZE);
        }

        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Sends a request to the sample service to write to local data using {@code TEST_WRITE_DATA}
     *
     * <p>Uses the new {@code executeInIsolatedService} API.
     */
    private static void writeLocalDataNewExecuteApi(
            OnDevicePersonalizationManager manager, String tableKey, boolean writeLargeData)
            throws InterruptedException {
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
        appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
        appParams.putString(SampleServiceApi.KEY_BASE64_VALUE, TEST_WRITE_DATA);

        if (writeLargeData) {
            appParams.putInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, LARGE_BLOB_SIZE);
        }
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Sends a request to the sample service to confirm that the given key does not exist in local
     * data.
     *
     * <p>Uses the legacy {@code execute} API.
     */
    private static void checkExpectedMissingLocalData(
            OnDevicePersonalizationManager manager, String tableKey) throws InterruptedException {
        // Check to ensure that the given key is missing in the local data
        readExpectedLocalData(
                manager, tableKey, /* expectedDataValue= */ "", /* expectLargeData= */ false);
    }

    /**
     * Sends a request to the sample service to confirm that the given key does not exist in local
     * data.
     *
     * <p>Uses the new {@code executeInIsolatedProcess} API.
     */
    private static void checkExpectedMissingLocalDataNewExecuteApi(
            OnDevicePersonalizationManager manager, String tableKey) throws InterruptedException {
        readExpectedLocalDataNewExecuteApi(
                manager, tableKey, /* expectedDataValue= */ "", /* expectLargeData= */ false);
    }

    /**
     * Sends a request to the sample service to confirm that the given key has a matching value in
     * the local data table.
     *
     * <p>Uses the new {@code executeInIsolatedProcess} API.
     */
    private static void readExpectedLocalDataNewExecuteApi(
            OnDevicePersonalizationManager manager,
            String tableKey,
            String expectedDataValue,
            boolean expectLargeData)
            throws InterruptedException {
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOCAL_DATA);
        appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
        if (!expectedDataValue.isEmpty()) {
            // If expected data value is empty, and we do not include it in the bundle to the
            // SampleService, it will check to ensure that the key does not exist in local data.
            appParams.putString(SampleServiceApi.KEY_BASE64_VALUE, expectedDataValue);
        }

        if (expectLargeData) {
            appParams.putInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, LARGE_BLOB_SIZE);
        }

        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Sends a request to the sample service to confirm that the given key has a matching value in
     * the local data table.
     *
     * <p>Uses the legacy {@code execute} API.
     */
    private static void readExpectedLocalData(
            OnDevicePersonalizationManager manager,
            String tableKey,
            String expectedDataValue,
            boolean expectLargeData)
            throws InterruptedException {
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_READ_LOCAL_DATA);
        appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
        if (!expectedDataValue.isEmpty()) {
            appParams.putString(SampleServiceApi.KEY_BASE64_VALUE, expectedDataValue);
        }

        if (expectLargeData) {
            appParams.putInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, LARGE_BLOB_SIZE);
        }

        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Sends a request to the sample service to remove the given key from the local data table.
     *
     * <p>Uses the legacy {@code execute} API.
     */
    private static void removeLocalData(OnDevicePersonalizationManager manager, String tableKey)
            throws InterruptedException {
        // Remove local data associated with the given tableKey and assert that the execute
        // call is successful. Uses the legacy execute API.
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
        appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Sends a request to the sample service to remove the given key from the local data table.
     *
     * <p>Uses the new {@code executeInIsolatedProcess} API.
     */
    private static void removeLocalDataNewExecuteApi(
            OnDevicePersonalizationManager manager, String tableKey) throws InterruptedException {
        // Remove local data associated with the given tableKey and assert that the execute
        // call is successful. Uses the new execute API.
        var receiver = new ResultReceiver<ExecuteInIsolatedServiceResponse>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_WRITE_LOCAL_DATA);
        appParams.putString(SampleServiceApi.KEY_TABLE_KEY, tableKey);
        ExecuteInIsolatedServiceRequest request =
                new ExecuteInIsolatedServiceRequest.Builder(
                                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
                        .setAppParams(appParams)
                        .build();

        manager.executeInIsolatedService(request, Executors.newSingleThreadExecutor(), receiver);

        assertNull(receiver.getResult());
        assertThat(receiver.getException()).isInstanceOf(IllegalStateException.class);
    }
}
