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
package com.android.ondevicepersonalization.cts;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.SurfacePackageToken;
import android.content.ComponentName;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * CTS Test cases for OnDevicePersonalizationManager APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CtsOdpManagerTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

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
                        new ComponentName("com.example.test", "com.example.TestClass"),
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
                    new ComponentName("com.example.test", "com.example.TestClass"),
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
                    new ComponentName("com.example.test", "com.example.TestClass"),
                        PersistableBundle.EMPTY,
                        Executors.newSingleThreadExecutor(),
                        null));
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
            mLatch.await();
        }
        T getResult() {
            return mResult;
        }
        Exception getException() {
            return mException;
        }
    }
}
