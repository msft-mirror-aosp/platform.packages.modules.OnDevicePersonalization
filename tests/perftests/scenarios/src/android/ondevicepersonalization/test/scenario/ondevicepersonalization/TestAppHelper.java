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
package android.ondevicepersonalization.test.scenario.ondevicepersonalization;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;

import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.Assert;

import java.io.IOException;

/** Helper class for interacting with OdpClient test app in perf tests. */
public class TestAppHelper {
    private static final UiDevice sUiDevice = UiDevice.getInstance(getInstrumentation());
    private static UiScrollable sUiScrollable;
    private static final long UI_FIND_RESOURCE_TIMEOUT = 5000;
    private static final long UI_ROTATE_IDLE_TIMEOUT = 5000;
    private static final String ODP_CLIENT_TEST_APP_PACKAGE_NAME = "com.example.odpclient";
    private static final String GET_AD_BUTTON_RESOURCE_ID = "get_ad_button";
    private static final String RENDERED_VIEW_RESOURCE_ID = "rendered_view";

    /** Commands to prepare the device and odp module before testing. */
    public static void initialize() throws IOException {
        executeShellCommand(
                "device_config set_sync_disabled_for_tests persistent");
        executeShellCommand(
                "device_config put on_device_personalization global_kill_switch false");
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
        executeShellCommand(
                "am broadcast -a android.intent.action.BOOT_COMPLETED -p "
                    + "com.google.android.ondevicepersonalization.services");
        executeShellCommand(
                "cmd jobscheduler run -f "
                    + "com.google.android.ondevicepersonalization.services 1000");
        SystemClock.sleep(5000);
        executeShellCommand(
                "cmd jobscheduler run -f "
                    + "com.google.android.ondevicepersonalization.services 1006");
        SystemClock.sleep(5000);
        executeShellCommand(
                "cmd jobscheduler run -f "
                    + "com.google.android.ondevicepersonalization.services 1003");
        SystemClock.sleep(5000);
        executeShellCommand(
                "cmd jobscheduler run -f "
                    + "com.google.android.ondevicepersonalization.services 1004");
        SystemClock.sleep(5000);
    }

    /** Commands to return device to original state */
    public static void wrapUp() throws IOException {
        executeShellCommand(
                "device_config set_sync_disabled_for_tests none");
    }

    private static void executeShellCommand(String cmd) {
        try {
            sUiDevice.executeShellCommand(cmd);
        } catch (IOException e) {
            Assert.fail("Failed to execute shell command: " + cmd + ". error: " + e);
        }
    }

    /** Open ODP client test app. */
    public static void openApp() throws IOException {
        sUiDevice.executeShellCommand(
                "am start " + ODP_CLIENT_TEST_APP_PACKAGE_NAME + "/.MainActivity");
    }

    /** Go back to home screen. */
    public static void goToHomeScreen() throws IOException {
        sUiDevice.pressHome();
    }

    /** Rotate screen to landscape orientation */
    public void setOrientationLandscape() throws RemoteException {
        sUiDevice.unfreezeRotation();
        sUiDevice.setOrientationLandscape();
        SystemClock.sleep(UI_ROTATE_IDLE_TIMEOUT);
    }

    /** Rotate screen to portrait orientation */
    public void setOrientationPortrait() throws RemoteException {
        sUiDevice.unfreezeRotation();
        sUiDevice.setOrientationPortrait();
        SystemClock.sleep(UI_ROTATE_IDLE_TIMEOUT);
    }

    /** Click Get Ad button. */
    public void clickGetAd() {
        UiObject2 getAdButton = getGetAdButton();
        assertNotNull("Get Ad button not found", getAdButton);
        getAdButton.click();
    }

    /** Verify view is correctly displayed after clicking Get Ad. */
    public void verifyRenderedView() {
        UiObject2 renderedView = getRenderedView();
        assertNotNull("Rendered view not found", renderedView);

        SystemClock.sleep(UI_FIND_RESOURCE_TIMEOUT);
        if (renderedView.getChildCount() == 0) {
            Assert.fail("Failed to render child surface view");
        }
    }

    private UiObject2 getGetAdButton() {
        return sUiDevice.wait(
            Until.findObject(By.res(ODP_CLIENT_TEST_APP_PACKAGE_NAME, GET_AD_BUTTON_RESOURCE_ID)),
            UI_FIND_RESOURCE_TIMEOUT);
    }

    /** Locate the rendered UI element in the scrollable view */
    private UiObject2 getRenderedView() {
        for (int i = 0; i < 2; i++) {
            // Try finding the renderedView on current screen
            UiObject2 renderedView = sUiDevice.wait(
                    Until.findObject(
                            By.res(ODP_CLIENT_TEST_APP_PACKAGE_NAME, RENDERED_VIEW_RESOURCE_ID)),
                    UI_FIND_RESOURCE_TIMEOUT);
            if (renderedView != null) {
                return renderedView;
            }

            // Try scroll to the end
            try {
                getUiScrollable().scrollToEnd(5);
            } catch (UiObjectNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private UiObject2 getChildSurfaceViewByText(final String text) {
        return sUiDevice.wait(
            Until.findObject(By.desc(text)),
            UI_FIND_RESOURCE_TIMEOUT);
    }

    /** Get a UiScrollable instance configured for vertical scrolling */
    private static UiScrollable getUiScrollable() {
        if (sUiScrollable == null) {
            sUiScrollable = new UiScrollable(new UiSelector().scrollable(true));
            sUiScrollable.setAsVerticalList();
        }
        return sUiScrollable;
    }
}
