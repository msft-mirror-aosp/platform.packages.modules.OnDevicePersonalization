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

package com.android.ondevicepersonalization.services.serviceflow;


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;
import com.android.ondevicepersonalization.services.util.PrivacyUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class AppRequestFlowTest {

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final OnDevicePersonalizationDbHelper mDbHelper =
            OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);

    private boolean mCallbackSuccess;
    private boolean mCallbackError;
    private int mCallbackErrorCode;
    private Bundle mExecuteCallback;
    private MockitoSession mSession;
    private ServiceFlowOrchestrator mSfo;

    @Mock
    UserPrivacyStatus mUserPrivacyStatus;

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization global_kill_switch false");

        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .spyStatic(UserPrivacyStatus.class)
                .spyStatic(PrivacyUtils.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);

        setUpTestData();

        mSfo = new ServiceFlowOrchestrator();
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testAppRequestFlow_PersonalizationDisabled() throws InterruptedException {
        doReturn(false).when(mUserPrivacyStatus).isPersonalizationStatusEnabled();

        mSfo.schedule(ServiceFlowType.APP_REQUEST_FLOW, "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                PersistableBundle.EMPTY, new TestExecuteCallback(), mContext, 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_PERSONALIZATION_DISABLED, mCallbackErrorCode);
    }

    @Test
    public void testAppRequestFlow_OutputDataBlocked() throws InterruptedException {
        doReturn(true).when(mUserPrivacyStatus).isPersonalizationStatusEnabled();
        ExtendedMockito.doReturn(false)
                .when(
                        () -> PrivacyUtils.isOutputDataAllowed(
                                anyString(), anyString(), any(Context.class)));

        mSfo.schedule(ServiceFlowType.APP_REQUEST_FLOW, "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                PersistableBundle.EMPTY, new TestExecuteCallback(), mContext, 100L);
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertNull(mExecuteCallback.getByteArray(Constants.EXTRA_OUTPUT_DATA));
        assertEquals(2,
                mDbHelper.getReadableDatabase().query(QueriesContract.QueriesEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testAppRequestFlow_OutputDataAllowed() throws InterruptedException {
        doReturn(true).when(mUserPrivacyStatus).isPersonalizationStatusEnabled();
        ExtendedMockito.doReturn(true)
                .when(
                        () -> PrivacyUtils.isOutputDataAllowed(
                                anyString(), anyString(), any(Context.class)));

        mSfo.schedule(ServiceFlowType.APP_REQUEST_FLOW, "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                PersistableBundle.EMPTY, new TestExecuteCallback(), mContext, 100L);
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertArrayEquals(
                mExecuteCallback.getByteArray(Constants.EXTRA_OUTPUT_DATA),
                new byte[] {1, 2, 3});
        assertEquals(2,
                mDbHelper.getReadableDatabase().query(QueriesContract.QueriesEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    private void setUpTestData() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                "com.example.test", "AABBCCDD", rows);
        EventsDao.getInstanceForTest(mContext).insertQuery(
                new Query.Builder().setServiceName(mContext.getPackageName()).setQueryData(
                        queryDataBytes).build());
        EventsDao.getInstanceForTest(mContext);
    }

    class TestExecuteCallback extends IExecuteCallback.Stub {
        @Override
        public void onSuccess(Bundle bundle) {
            mCallbackSuccess = true;
            mExecuteCallback = bundle;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mCallbackError = true;
            mCallbackErrorCode = errorCode;
            mLatch.countDown();
        }
    }
}
