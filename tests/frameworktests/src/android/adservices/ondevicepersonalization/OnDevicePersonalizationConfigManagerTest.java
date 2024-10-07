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

package android.adservices.ondevicepersonalization;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OnDevicePersonalizationConfigManagerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testSetPersonalizationStatus() throws Exception {
        OnDevicePersonalizationConfigManager manager =
                new OnDevicePersonalizationConfigManager(mContext);
        ResultReceiver<Void> receiver = new ResultReceiver<>();
        manager.setPersonalizationEnabled(true, Runnable::run, receiver);
        assertTrue(receiver.isCalled());
    }
}
