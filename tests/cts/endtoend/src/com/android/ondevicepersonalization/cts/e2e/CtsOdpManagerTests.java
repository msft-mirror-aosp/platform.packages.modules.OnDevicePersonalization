/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.ondevicepersonalization.cts.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.SurfacePackageToken;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CTS Test cases for OnDevicePersonalizationManager APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CtsOdpManagerTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";

    @Test
    public void testExecuteThrowsIfComponentNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        null,
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfParamsMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                        null,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfExecutorMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                        PersistableBundle.EMPTY,
                        null,
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfReceiverMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);

        assertThrows(
                NullPointerException.class,
                () -> manager.execute(
                        new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        null));
    }

    @Test
    public void testExecuteThrowsIfPackageNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.execute(
                    new ComponentName("", SERVICE_CLASS),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteThrowsIfClassNameMissing() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.execute(
                    new ComponentName(SERVICE_PACKAGE, ""),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<List<SurfacePackageToken>>()));
    }

    @Test
    public void testExecuteReturnsNameNotFoundIfServiceNotInstalled() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);
        var receiver = new ResultReceiver<List<SurfacePackageToken>>();
        manager.execute(
                new ComponentName("somepackage", "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        assertEquals(null, receiver.getResult());
        assertTrue(receiver.getException() instanceof NameNotFoundException);
    }

    @Test
    public void testExecuteReturnsClassNotFoundIfServiceClassNotFound()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);
        var receiver = new ResultReceiver<List<SurfacePackageToken>>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, "someclass"),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        assertEquals(null, receiver.getResult());
        assertTrue(receiver.getException() instanceof ClassNotFoundException);
    }

    @Test
    public void testExecute() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        assertNotEquals(null, manager);
        var receiver = new ResultReceiver<List<SurfacePackageToken>>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                PersistableBundle.EMPTY,
                Executors.newSingleThreadExecutor(),
                receiver);
        receiver.await();
        List<SurfacePackageToken> results = receiver.getResult();
        assertNotEquals(null, results);
        assertEquals(1, results.size());
        SurfacePackageToken token = results.get(0);
        assertNotEquals(null, token);
    }

    class ResultReceiver<T> implements OutcomeReceiver<T, Exception> {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private T mResult;
        private Exception mException;
        @Override public void onResult(T result) {
            mResult = result;
            mLatch.countDown();
        }
        @Override public void onError(Exception e) {
            mException = e;
            mLatch.countDown();
        }
        void await() throws InterruptedException {
            mLatch.await(5, TimeUnit.SECONDS);
        }
        T getResult() {
            return mResult;
        }
        Exception getException() {
            return mException;
        }
    }
}