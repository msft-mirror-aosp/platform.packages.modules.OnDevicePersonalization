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

import static org.junit.Assert.assertNotNull;

import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Assert;

import java.io.IOException;

/** Helper class for interacting with federatedcompute in perf tests. */
public class TestHelper {
    private static UiDevice sUiDevice;
    private static final long UI_FIND_RESOURCE_TIMEOUT = 5000;
    private static final long TRAINING_TASK_COMPLETION_TIMEOUT = 120_000;
    private static final String ODP_CLIENT_TEST_APP_PACKAGE_NAME = "com.example.odpclient";
    private static final String SCHEDULE_TRAINING_BUTTON_RESOURCE_ID = "schedule_training_button";
    private static final String SCHEDULE_TRAINING_TEXT_BOX_RESOURCE_ID =
            "schedule_training_text_box";
    private static final String ODP_TEST_APP_POPULATION_NAME = "criteo_app_test_task";
    private static final String ODP_TEST_APP_TRAINING_TASK_JOB_ID = "1586947961";
    private static final String FEDERATED_TRAINING_JOB_SUCCESS_LOG =
            "FederatedJobService - Federated computation job 1586947961 is done";

    public static void pressHome() {
        getUiDevice().pressHome();
    }

    /** Commands to prepare the device, odp module, fcp module before testing. */
    public static void initialize() {
        executeShellCommand(
                "device_config set_sync_disabled_for_tests persistent");
        disableGlobalKillSwitch();
        disableFederatedComputeKillSwitch();
        executeShellCommand(
                "device_config put on_device_personalization "
                    + "enable_ondevicepersonalization_apis true");
        executeShellCommand(
                "device_config put on_device_personalization "
                    + "enable_personalization_status_override true");
        executeShellCommand(
                "device_config put on_device_personalization "
                    + "personalization_status_override_value true");
        executeShellCommand("setprop log.tag.ondevicepersonalization VERBOSE");
        executeShellCommand("setprop log.tag.federatedcompute VERBOSE");
        executeShellCommand(
                "am broadcast -a android.intent.action.BOOT_COMPLETED -p "
                    + "com.google.android.ondevicepersonalization.services");
        executeShellCommand(
                "am broadcast -a android.intent.action.BOOT_COMPLETED -p "
                    + "com.google.android.federatedcompute");
    }

    /** Commands to return device to original state */
    public static void wrapUp() {
        executeShellCommand(
                "device_config set_sync_disabled_for_tests none");
    }

    /** Open ODP client test app. */
    public void openTestApp() throws IOException {
        sUiDevice.executeShellCommand(
                "am start " + ODP_CLIENT_TEST_APP_PACKAGE_NAME + "/.MainActivity");
    }

    /** Put the default population name down for training */
    public void inputPopulationForScheduleTraining() {
        UiObject2 scheduleTrainingTextBox = getScheduleTrainingTextBox();
        assertNotNull("Schedule Training text box not found", scheduleTrainingTextBox);
        scheduleTrainingTextBox.setText(ODP_TEST_APP_POPULATION_NAME);
    }

    /** Click Schedule Training button. */
    public void clickScheduleTraining() {
        UiObject2 scheduleTrainingButton = getScheduleTrainingButton();
        assertNotNull("Schedule Training button not found", scheduleTrainingButton);
        scheduleTrainingButton.click();
        SystemClock.sleep(10000);
    }

    /** Force the JobScheduler to execute the training task, bypassing all constraints */
    public void forceExecuteTrainingTaskForTestApp() throws IOException {
        executeShellCommand("logcat -c"); // Cleans the log buffer
        executeShellCommand("logcat -G 32M"); // Set log buffer to 32MB
        executeShellCommand(
                "cmd jobscheduler run -f com.google.android.federatedcompute "
                    + ODP_TEST_APP_TRAINING_TASK_JOB_ID);
        SystemClock.sleep(10000);

        boolean foundTrainingJobSuccessLog = findLog(
                FEDERATED_TRAINING_JOB_SUCCESS_LOG,
                TRAINING_TASK_COMPLETION_TIMEOUT,
                10000);

        if (!foundTrainingJobSuccessLog) {
            Assert.fail(String.format(
                    "Failed to find federated training job success log within test window %d ms",
                    TRAINING_TASK_COMPLETION_TIMEOUT));
        }
    }

    /** Attempt to find a specific log entry within the timeout window */
    private boolean findLog(final String targetLog, long timeoutMillis,
            long queryIntervalMillis) throws IOException {

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (getUiDevice().executeShellCommand("logcat -d").contains(targetLog)) {
                return true;
            }
            SystemClock.sleep(queryIntervalMillis);
        }
        return false;
    }

    private static void disableGlobalKillSwitch() {
        executeShellCommand(
                "device_config put on_device_personalization global_kill_switch false");
    }

    private static void disableFederatedComputeKillSwitch() {
        executeShellCommand(
                "device_config put on_device_personalization federated_compute_kill_switch false");
    }

    private static void executeShellCommand(String cmd) {
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

    private UiObject2 getScheduleTrainingTextBox() {
        return sUiDevice.wait(
            Until.findObject(
                By.res(ODP_CLIENT_TEST_APP_PACKAGE_NAME, SCHEDULE_TRAINING_TEXT_BOX_RESOURCE_ID)),
            UI_FIND_RESOURCE_TIMEOUT);
    }

    private UiObject2 getScheduleTrainingButton() {
        return sUiDevice.wait(
            Until.findObject(
                By.res(ODP_CLIENT_TEST_APP_PACKAGE_NAME, SCHEDULE_TRAINING_BUTTON_RESOURCE_ID)),
            UI_FIND_RESOURCE_TIMEOUT);
    }

}
