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
 * Schedule a federatedCompute training one-time task from Odp Test app UI
 * Schedule a federatedCompute training recurring task from Odp Test app UI
 * Then cancel both tasks
 */
public class ScheduleAndCancelTraining {
    private TestHelper mTestHelper = new TestHelper();

    /** Prepare the device before entering the test class */
    @BeforeClass
    public static void prepareDevice() throws IOException {
        TestHelper.killRunningProcess();
        TestHelper.initialize();
        TestHelper.killRunningProcess();
    }

    @Before
    public void setup() throws IOException {
        mTestHelper.pressHome();
        mTestHelper.openTestApp();
    }

    @Test
    public void testScheduleAndCancelTraining() throws IOException {
        mTestHelper.inputNonExistentPopulationForScheduleTraining();
        mTestHelper.clickScheduleTraining();
        mTestHelper.clickCancelTraining();

        mTestHelper.inputNonExistentPopulationForScheduleTraining();
        mTestHelper.inputRandomRecurringTrainingInterval();
        mTestHelper.clickScheduleTraining();
        mTestHelper.clickCancelTraining();
    }

    /** Return device to original state after test exeuction */
    @AfterClass
    public static void tearDown() throws IOException {
        TestHelper.pressHome();
        TestHelper.wrapUp();
    }
}
