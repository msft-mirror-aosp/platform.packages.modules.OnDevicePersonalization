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

package com.android.ondevicepersonalization.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Binder;
import android.os.Bundle;
import android.view.SurfaceControlViewHost;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationManagingServiceTest {
    private OnDevicePersonalizationManagingServiceImpl mService;
    @Before
    public void setup() throws Exception {
        mService = new FakeOnDevicePersonalizationManagingService();
    }
    @Test
    public void testVersion() throws Exception {
        assertEquals(mService.getVersion(), "1.0");
    }

    @Test
    public void testRequestSurfacePackageReturnsError() throws Exception {
        // TODO(b/228200518): Update this test after implementation.
        CountDownLatch latch = new CountDownLatch(1);

        var callback =
                new IRequestSurfacePackageCallback.Stub() {
                    public boolean error = false;
                    @Override
                    public void onSuccess(SurfaceControlViewHost.SurfacePackage s) {}
                    @Override
                    public void onError(int errorCode) {
                        error = true;
                        latch.countDown();
                    }
                };

        mService.requestSurfacePackage(
                "",
                "x.y",
                new Binder(),
                0,
                0,
                0,
                new Bundle(),
                callback);

        latch.await();
        assertTrue(callback.error);
    }

    private static class FakeOnDevicePersonalizationManagingService
            extends OnDevicePersonalizationManagingServiceImpl {
    }
}
