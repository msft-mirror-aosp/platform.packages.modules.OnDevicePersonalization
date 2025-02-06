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

package com.android.tests.sandbox.ondevicepersonalization;

import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.interfaces.ISdkOnDevicePersonalizationApi;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * Test OnDevicePersonalization APIs running within the Sandbox.
 */
public final class SandboxOnDevicePersonalizationManagerTest {
    private static final String TAG = "SandboxOnDevicePersonalizationManagerTest";
    private static final String SDK_NAME = "com.android.tests.providers.sdkondevicepersonalization";
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";

    private SandboxedSdk mSandboxedSdk;

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());
        Assume.assumeTrue(DeviceSupportHelper.isOdpModuleAvailable());

        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofSeconds(10));

        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "caller_app_allow_list "
                        + sContext.getPackageName());
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
                        + "com.example.odptargetingapp2,"
                        + "com.android.tests.sandbox.ondevicepersonalization");
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "output_data_allow_list "
                        + sContext.getPackageName()
                        + ";com.android.ondevicepersonalization.testing.sampleservice");
    }

    @After
    public void reset() {
        SimpleActivity.stopSimpleActivity(sContext);
        ShellUtils.runShellCommand(
                "device_config delete on_device_personalization "
                        + "caller_app_allow_list");
        ShellUtils.runShellCommand(
                "device_config delete on_device_personalization "
                        + "shared_isolated_process_feature_enabled");
        ShellUtils.runShellCommand(
                "device_config delete on_device_personalization "
                        + "debug.validate_rendering_config_keys");
        ShellUtils.runShellCommand(
                "device_config delete on_device_personalization "
                        + "isolated_service_allow_list");
        ShellUtils.runShellCommand(
                "device_config delete on_device_personalization "
                        + "output_data_allow_list");

        ShellUtils.runShellCommand(
                "am force-stop com.google.android.ondevicepersonalization.services");
        ShellUtils.runShellCommand(
                "am force-stop com.android.ondevicepersonalization.services");
        mSandboxedSdk = null;
    }

    @Test
    public void matchPackageNameWithoutSandbox() {
        boolean result = matchPackageNameWithoutSandbox(sContext.getPackageName());

        assertTrue("Package name did not match without sandbox", result);
    }

    @Test
    public void matchPackageNameWithinSandbox() {
        Assume.assumeTrue(SdkLevel.isAtLeastU());
        assertTrue("Unable to load SDK", loadSdk(SDK_NAME));

        boolean result = matchPackageNameWithinSandbox(sContext.getPackageName());

        assertTrue("Package name did not match within sandbox", result);
    }

    private boolean matchPackageNameWithoutSandbox(String packageName) {
        var receiver = new ResultReceiver<OnDevicePersonalizationManager.ExecuteResult>();
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString(
                SampleServiceApi.KEY_OPCODE,
                SampleServiceApi.OPCODE_CHECK_PACKAGE_NAME);
        appParams.putString(
                SampleServiceApi.KEY_EXPECTED_PACKAGE_NAME,
                packageName);
        sContext.getSystemService(OnDevicePersonalizationManager.class).execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                appParams,
                Executors.newSingleThreadExecutor(),
                receiver);
        try {
            return receiver.isSuccess();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error while calling ResultReceiver#isSuccess", e);
            return false;
        }
    }

    private boolean matchPackageNameWithinSandbox(String packageName) {
        try {
            ISdkOnDevicePersonalizationApi sdkApi = getInterface(mSandboxedSdk);
            return sdkApi.matchPackageName(packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "Error while calling sdk API", e);
            return false;
        }
    }

    private ISdkOnDevicePersonalizationApi getInterface(SandboxedSdk sandboxedSdk) {
        IBinder binder = sandboxedSdk.getInterface();
        return ISdkOnDevicePersonalizationApi.Stub.asInterface(binder);
    }

    private boolean loadSdk(String sdkName) {
        SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        CountDownLatch latch = new CountDownLatch(1);
        final LoadSdkCallbackImpl callback = new LoadSdkCallbackImpl(latch);
        sdkSandboxManager.loadSdk(
                sdkName, new Bundle(), Runnable::run, callback);
        try {
            latch.await(/* timeout */ 30, TimeUnit.SECONDS);
            return mSandboxedSdk != null;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private class LoadSdkCallbackImpl implements OutcomeReceiver<SandboxedSdk, LoadSdkException> {
        private CountDownLatch mLatch;

        private LoadSdkCallbackImpl(CountDownLatch latch) {
            mLatch = latch;
        }

        /**
         * Notifies client the requested SDK is successfully loaded.
         */
        @Override
        public void onResult(SandboxedSdk sandboxedSdk) {
            Log.i(TAG, "SDK has been loaded successfully");
            mSandboxedSdk = sandboxedSdk;
            mLatch.countDown();
        }

        /**
         * Notifies client the requested Sdk failed to be loaded.
         */
        @Override
        public void onError(LoadSdkException error) {
            Log.e(TAG, "Error while loading SDK", error);
            mLatch.countDown();
        }
    }
}
