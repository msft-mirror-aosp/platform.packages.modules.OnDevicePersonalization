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
package android.federatedcompute.test.scenario.federatedcompute;

import android.os.SystemClock;
import android.platform.test.scenario.annotation.Scenario;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@Scenario
@RunWith(JUnit4.class)
/**
 * Schedule recurrent federatedCompute training task from Odp Test app UI
 * Then force the task execution twice through ADB commands
 */
public class ScheduleRecurrentTrainingAndExecute {
    private TestHelper mTestHelper = new TestHelper();

    /** Prepare the device before entering the test class */
    @BeforeClass
    public static void prepareDevice() throws IOException {
        TestHelper.killRunningProcess(); // so we can reset stable flags
        TestHelper.initialize();
        TestHelper.killRunningProcess(); // to get cold start metrics
    }

    @Before
    public void setup() throws IOException {
        mTestHelper.pressHome();
        mTestHelper.openTestApp();
    }

    @Test
    public void testScheduleRecurrentTrainingAndExecute() throws IOException {
        mTestHelper.inputPopulationForScheduleTraining();
        mTestHelper.inputRandomRecurringTrainingInterval();
        mTestHelper.clickScheduleTraining();
        mTestHelper.forceExecuteTrainingTaskForTestApp();
        SystemClock.sleep(5000);
        mTestHelper.forceExecuteTrainingTaskForTestApp();
    }

    /** Return device to original state after test exeuction */
    @AfterClass
    public static void tearDown() throws IOException {
        TestHelper.pressHome();
        TestHelper.wrapUp();
    }
}
