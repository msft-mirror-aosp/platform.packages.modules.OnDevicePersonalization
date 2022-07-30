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

package com.android.ondevicepersonalization.services.download;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationStartDownloadServiceReceiverTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testOnReceive() throws Exception {
        OnDevicePersonalizationStartDownloadServiceReceiver receiver =
                new OnDevicePersonalizationStartDownloadServiceReceiver();

        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(mContext, intent);
        WorkManager workManager = WorkManager.getInstance(mContext);
        List<WorkInfo> workInfos = workManager.getWorkInfosByTag(
                OnDevicePersonalizationDownloadProcessingWorker.TAG).get();
        assertEquals(1, workInfos.size());
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.get(0).getState());
    }
}
