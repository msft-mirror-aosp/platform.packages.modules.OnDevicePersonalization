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

package com.android.ondevicepersonalization.services;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.ondevicepersonalization.aidl.IPrivacyStatusServiceCallback;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public final class OnDevicePersonalizationPrivacyStatusServiceTest {
    // TODO (b/272821305): test business logic when stub is implemented.
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationPrivacyStatusServiceDelegate mBinder;

    @Before
    public void setup() throws Exception {
        mBinder = new OnDevicePersonalizationPrivacyStatusServiceDelegate(mContext);
    }

    @Test
    public void testSetKidStatus() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean flag = new AtomicBoolean(false);
        mBinder.setKidStatus(false, new IPrivacyStatusServiceCallback() {
            @Override
            public void onSuccess() {
                flag.set(true);
                latch.countDown();
            }

            @Override
            public void onFailure(int errorCode) {
                Assert.fail();
            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        });

        latch.await();
        assertTrue(flag.get());
    }

    @Test
    public void testSetKidStatusIfCallbackMissing() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            mBinder.setKidStatus(false, null);
        });
    }
}
