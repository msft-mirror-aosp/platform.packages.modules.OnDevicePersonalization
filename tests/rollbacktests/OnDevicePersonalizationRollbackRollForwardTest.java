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

import com.android.module_rollback_test.host.ModuleRollbackBaseHostTest;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
@SuppressWarnings("DefaultPackage")
public class OnDevicePersonalizationRollbackRollForwardTest extends ModuleRollbackBaseHostTest {

    private static final String TAG = "OnDevicePersonalizationRollbackRollForwardTest";

    private static final String TEST_APP =
            "com.android.ondevicepersonalization.tests.rollback"
                    + ".ondevicepersonalizationrollbackrollforwardtestapp";

    private static final String TEST_APP1_CLASS =
            "com.android.ondevicepersonalization.tests"
                    + ".OnDevicePersonalizationRollbackRollForwardTestApp";

    /** Runs tests in the TEST_APP on test device */
    public void runTestApp() {
        ITestDevice device = getDevice();
        try {
            if (!device.isAdbRoot()) {
                device.enableAdbRoot();
            }

            installPackage(TEST_APP + ".apk");
            runDeviceTests(TEST_APP, TEST_APP1_CLASS, "testExecute");
        } catch (Exception e) {
            throw new IllegalStateException("Failed on running device test", e);
        }
    }

    /** Customized checks/actions before installing the modules. */
    @Override
    public void onPreInstallModule() {
        try {
            getDevice().executeShellCommand("log -t " + TAG + " onPreInstallModule()");
        } catch (Exception e) {
            throw new IllegalStateException("Failed on running device test", e);
        }
    }

    /**
     * Customized checks/actions before installing higher version module. Current module version v =
     * v1.
     */
    @Override
    public void onPreInstallHigherVersionModule() {
        try {
            getDevice().executeShellCommand("log -t " + TAG + " onPreInstallModule()");
            runTestApp();
        } catch (Exception e) {
            throw new IllegalStateException("Failed on running device test", e);
        }
    }

    /** Customized checks/actions before rolling back a module. Current module version v = v2. */
    @Override
    public void onPreRollbackSetting() {
        try {
            getDevice().executeShellCommand("log -t " + TAG + " onPreRollbackSetting()");
            runTestApp();
        } catch (Exception e) {
            throw new IllegalStateException("Failed on running device test", e);
        }
    }

    /** Customized checks/actions after rolling back a module. Current module version v = v1. */
    @Override
    public void onPostRollbackValidation() {
        try {
            getDevice().executeShellCommand("log -t " + TAG + " onPostRollbackValidation()");
            runTestApp();
        } catch (Exception e) {
            throw new IllegalStateException("Failed on running device test", e);
        }
    }
}
