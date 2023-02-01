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
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.os.Binder;
import android.os.Bundle;
import android.view.SurfaceControlViewHost;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationManagingServiceTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationManagingServiceDelegate mService;
    @Before
    public void setup() throws Exception {
        mService = new OnDevicePersonalizationManagingServiceDelegate(mContext);
    }
    @Test
    public void testVersion() throws Exception {
        assertEquals(mService.getVersion(), "1.0");
    }

    @Test
    public void testRequestSurfacePackageThrowsIfPackageNameIncorrect() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                SecurityException.class,
                () ->
                    mService.requestSurfacePackage(
                        "abc",
                        mContext.getPackageName(),
                        new Binder(),
                        0,
                        0,
                        0,
                        new Bundle(),
                        callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfPackageNameMissing() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                    mService.requestSurfacePackage(
                        null,
                        mContext.getPackageName(),
                        new Binder(),
                        0,
                        0,
                        0,
                        new Bundle(),
                        callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfExchangeMissing() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                    mService.requestSurfacePackage(
                        mContext.getPackageName(),
                        null,
                        new Binder(),
                        0,
                        0,
                        0,
                        new Bundle(),
                        callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfHostTokenMissing() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                    mService.requestSurfacePackage(
                        mContext.getPackageName(),
                        mContext.getPackageName(),
                        null,
                        0,
                        0,
                        0,
                        new Bundle(),
                        callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfParamsMissing() throws Exception {
        var callback = new RequestSurfacePackageCallback();
        assertThrows(
                NullPointerException.class,
                () ->
                    mService.requestSurfacePackage(
                        mContext.getPackageName(),
                        mContext.getPackageName(),
                        new Binder(),
                        0,
                        0,
                        0,
                        null,
                        callback));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfCallbackMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                    mService.requestSurfacePackage(
                        mContext.getPackageName(),
                        mContext.getPackageName(),
                        new Binder(),
                        0,
                        0,
                        0,
                        new Bundle(),
                        null));
    }

    static class RequestSurfacePackageCallback extends IRequestSurfacePackageCallback.Stub {
        public boolean mError = false;
        public int mErrorCode = 0;
        private CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSuccess(SurfaceControlViewHost.SurfacePackage s) {}

        @Override
        public void onError(int errorCode) {
            mError = true;
            mErrorCode = errorCode;
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await();
        }
    }
}
