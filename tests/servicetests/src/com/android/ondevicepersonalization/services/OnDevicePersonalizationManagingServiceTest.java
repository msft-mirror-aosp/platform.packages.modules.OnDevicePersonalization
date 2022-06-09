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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.ondevicepersonalization.aidl.IInitOnDevicePersonalizationCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    public void testSuccessfulInit() throws Exception {
        FakeInitOnDevicePersonalizationCallback callback =
                new FakeInitOnDevicePersonalizationCallback();
        Bundle bundle = new Bundle();
        mService.init(bundle, callback);

        assertTrue(callback.isInitSuccess());
        IBinder token = callback.getToken();
        assertNotNull(token);
    }

    private static class FakeOnDevicePersonalizationManagingService
            extends OnDevicePersonalizationManagingServiceImpl {
    }

    private static class FakeInitOnDevicePersonalizationCallback
            extends IInitOnDevicePersonalizationCallback.Stub {
        private IBinder mToken;
        private boolean mInitSuccess;

        @Override
        public void onSuccess(IBinder token) throws RemoteException {
            mToken = token;
            mInitSuccess = true;
        }

        @Override
        public void onError(int errorCode) throws RemoteException {
        }

        public boolean isInitSuccess() {
            return mInitSuccess;
        }

        public IBinder getToken() {
            return mToken;
        }
    }
}
