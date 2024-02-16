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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationException;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.adservices.ondevicepersonalization.SurfacePackageToken;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;

/**
 * CTS Test cases for OnDevicePersonalizationManager APIs.
 */
@RunWith(Parameterized.class)
public class CtsOdpManagerTests {

    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }

    @Before
    public void setUp() {
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);
    }

    @Test
    public void testExecuteThrowsIfComponentNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
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
                () -> manager.execute(
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
                () -> manager.execute(
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
                () -> manager.execute(
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
                () -> manager.execute(
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
                () -> manager.execute(
                    new ComponentName(SERVICE_PACKAGE, ""),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecuteReturnsNameNotFoundIfServiceNotInstalled() throws InterruptedException {
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
        assertTrue(receiver.getException() instanceof NameNotFoundException);
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
        assertTrue(receiver.getException() instanceof ClassNotFoundException);
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
        assertTrue(receiver.isSuccess());
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
        assertTrue(receiver.isSuccess());
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
        assertTrue(receiver.isSuccess());
        SurfacePackageToken token = receiver.getResult().getSurfacePackageToken();
        assertNotNull(token);
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
        assertTrue(receiver.isSuccess());
        SurfacePackageToken token = receiver.getResult().getSurfacePackageToken();
        assertNull(token);
    }

    @Test
    public void testExecuteReturnsErrorIfServiceThrows() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotNull(manager);
        var receiver = new ResultReceiver<ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_THROW_EXCEPTION);
        appParams.putString(SampleServiceApi.KEY_EXCEPTION_CLASS, "NullPointerException");
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
}
