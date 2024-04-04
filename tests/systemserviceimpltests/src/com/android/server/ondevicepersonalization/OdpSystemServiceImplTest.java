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

import static com.android.server.ondevicepersonalization.OnDevicePersonalizationSystemService.PERSONALIZATION_STATUS_KEY;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.Constants;
import android.content.Context;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OdpSystemServiceImplTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_CONFIG_FILE_IDENTIFIER = "TEST_CONFIG";
    private static final String BAD_TEST_KEY = "non-exist-key";
    private final BooleanFileDataStore mTestDataStore =
                    new BooleanFileDataStore(mContext.getFilesDir().getAbsolutePath(),
                                    TEST_CONFIG_FILE_IDENTIFIER);
    private boolean mOnResultCalled;
    private boolean mOnErrorCalled;
    private Bundle mResult;
    private int mErrorCode;
    private CountDownLatch mLatch;
    private OnDevicePersonalizationSystemService mService;
    private IOnDevicePersonalizationSystemServiceCallback mCallback;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());
        Assume.assumeTrue(SdkLevel.isAtLeastU());
        mService = spy(new OnDevicePersonalizationSystemService(mContext, mTestDataStore));
        doNothing().when(mService).enforceCallingPermission();
        mOnResultCalled = false;
        mOnErrorCalled = false;
        mResult = null;
        mErrorCode = 0;
        mLatch = new CountDownLatch(1);
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
        assertNotNull(mCallback);
    }

    @Test
    public void testSystemServerServiceOnRequest() throws Exception {
        mService.onRequest(new Bundle(), mCallback);
        mLatch.await();
        assertTrue(mOnResultCalled);
        assertNull(mResult);
        verify(mService).enforceCallingPermission();
    }

    @Test
    public void testSystemServerServiceSetPersonalizationStatus() throws Exception {
        mService.setPersonalizationStatus(true, mCallback);
        mLatch.await();
        assertTrue(mOnResultCalled);
        assertNotNull(mResult);
        boolean inputBool = mResult.getBoolean(PERSONALIZATION_STATUS_KEY);
        assertTrue(inputBool);
        verify(mService).enforceCallingPermission();
    }

    @Test
    public void testSystemServerServiceReadPersonalizationStatusSuccess() throws Exception {
        mTestDataStore.put(PERSONALIZATION_STATUS_KEY, true);
        mService.readPersonalizationStatus(mCallback);
        assertTrue(mOnResultCalled);
        assertNotNull(mResult);
        boolean inputBool = mResult.getBoolean(PERSONALIZATION_STATUS_KEY);
        assertTrue(inputBool);
        verify(mService).enforceCallingPermission();
    }

    @Test
    public void testSystemServerServiceReadPersonalizationStatusNotFound() throws Exception {
        mTestDataStore.put(BAD_TEST_KEY, true);
        mService.readPersonalizationStatus(mCallback);
        assertTrue(mOnErrorCalled);
        assertNull(mResult);
        assertEquals(mErrorCode, Constants.STATUS_KEY_NOT_FOUND);
        verify(mService).enforceCallingPermission();
    }

    @Test
    public void testSystemServerServiceSetPersonalizationStatusPermissionDenied()
            throws Exception {
        doThrow(SecurityException.class).when(mService).enforceCallingPermission();
        assertThrows(
                SecurityException.class,
                () -> mService.setPersonalizationStatus(true, mCallback));
    }

    @After
    public void cleanUp() {
        mTestDataStore.tearDownForTesting();
    }
}
