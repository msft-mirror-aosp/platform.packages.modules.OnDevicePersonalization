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

package com.android.server.ondevicepersonalization;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
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
public class OdpSystemServiceImplTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private boolean mOnResultCalled = false;
    private boolean mOnErrorCalled = false;
    private Bundle mResult;
    private int mErrorCode = 0;
    private CountDownLatch mLatch = new CountDownLatch(1);
    private OnDevicePersonalizationSystemService mService =
            new OnDevicePersonalizationSystemService(mContext);
    private IOnDevicePersonalizationSystemServiceCallback mCallback;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());
        Assume.assumeTrue(SdkLevel.isAtLeastU());
        mCallback = new IOnDevicePersonalizationSystemServiceCallback.Stub() {
            @Override
            public void onResult(Bundle bundle) {
                mOnResultCalled = true;
                mResult = bundle;
                mLatch.countDown();
            }

            @Override
            public void onError(int errorCode) {
                mOnErrorCalled = true;
                mErrorCode = errorCode;
                mLatch.countDown();
            }
        };
    }

    @Test
    public void testSystemServerServiceOnRequest() throws Exception {
        mService.onRequest(new Bundle(), mCallback);
        mLatch.await();
        assertTrue(mOnResultCalled);
    }
}
