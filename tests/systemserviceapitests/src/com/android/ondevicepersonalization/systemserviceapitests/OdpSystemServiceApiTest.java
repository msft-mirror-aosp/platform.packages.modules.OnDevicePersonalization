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

package com.android.ondevicepersonalization.systemserviceapitests;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemService;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.ondevicepersonalization.OnDevicePersonalizationSystemServiceManager;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OdpSystemServiceApiTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    boolean mOnRequestCalled = false;
    CountDownLatch mLatch = new CountDownLatch(1);

    @Before
    public void setUp() {
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());
        Assume.assumeTrue(SdkLevel.isAtLeastU());
    }

    @Test
    public void testInvokeSystemServerServiceSucceedsOnU() throws Exception {
        OnDevicePersonalizationSystemServiceManager manager =
                mContext.getSystemService(OnDevicePersonalizationSystemServiceManager.class);
        assertNotEquals(null, manager);
        IOnDevicePersonalizationSystemService service =
                manager.getService();
        assertNotEquals(null, service);
        service.onRequest(
                new Bundle(),
                new IOnDevicePersonalizationSystemServiceCallback.Stub() {
                    @Override public void onResult(Bundle result) {
                        mOnRequestCalled = true;
                        mLatch.countDown();
                    }
                    @Override
                    public void onError(int errorCode) {
                        mOnRequestCalled = true;
                        mLatch.countDown();
                    }
                });

        mLatch.await();
        assertTrue(mOnRequestCalled);
    }
}
