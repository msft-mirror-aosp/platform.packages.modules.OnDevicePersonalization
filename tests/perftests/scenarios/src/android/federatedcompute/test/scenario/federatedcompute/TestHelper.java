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
package android.federatedcompute.test.scenario.federatedcompute;

import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Assert;

import java.io.IOException;

/** Helper class for interacting with federatedcompute in perf tests. */
public class TestHelper {
    private static UiDevice sUiDevice;
    private static final String FEDERATED_COMPUTE_TASK_JOB_ID = "1007";

    private static final String FEDERATED_TRAINING_JOB_ID = "109883";

    public static void pressHome() {
        getUiDevice().pressHome();
    }

    public void initialize() {
        disableGlobalKillSwitch();
        disableFederatedComputeKillSwitch();
    }

    public void scheduleFederatedComputeTask() throws IOException {
        executeShellCommand(
                "cmd jobscheduler run -f com.google.android.ondevicepersonalization.services "
                        + FEDERATED_COMPUTE_TASK_JOB_ID);
        SystemClock.sleep(8000);
    }

    public void scheduleFederatedTrainingTask() throws IOException {
        executeShellCommand(
                "cmd jobscheduler run -f com.google.android.federatedcompute "
                        + FEDERATED_TRAINING_JOB_ID);
        SystemClock.sleep(8000);
    }

    private void disableGlobalKillSwitch() {
        executeShellCommand(
                "device_config put on_device_personalization global_kill_switch false");
    }

    private void disableFederatedComputeKillSwitch() {
        executeShellCommand(
                "device_config put on_device_personalization federated_compute_kill_switch false");
    }

    private void executeShellCommand(String cmd) {
        try {
            getUiDevice().executeShellCommand(cmd);
        } catch (IOException e) {
            Assert.fail("Failed to execute shell command: " + cmd + ". error: " + e);
        }
    }

    private static UiDevice getUiDevice() {
        if (sUiDevice == null) {
            sUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return sUiDevice;
    }
}
