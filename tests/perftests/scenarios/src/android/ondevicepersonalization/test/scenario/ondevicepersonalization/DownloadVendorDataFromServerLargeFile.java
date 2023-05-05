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

import android.platform.test.scenario.annotation.Scenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@Scenario
@RunWith(JUnit4.class)
public class DownloadVendorDataFromServerLargeFile {

    private DownloadHelper mDownloadHelper = new DownloadHelper();

    @Before
    public void setUp() throws IOException {
        mDownloadHelper.pressHome();
        mDownloadHelper.installVendorApkWithServerLargeFile();
    }

    @Test
    public void testDownloadVendorDataFromServerLargeFile() throws IOException {
        mDownloadHelper.downloadVendorData();
        mDownloadHelper.processDownloadedVendorData();
    }

    @After
    public void tearDown() throws IOException {
        mDownloadHelper.uninstallVendorApk();
        mDownloadHelper.cleanupDatabase();
        mDownloadHelper.cleanupDownloadedMetadata();
        mDownloadHelper.pressHome();
    }

}
