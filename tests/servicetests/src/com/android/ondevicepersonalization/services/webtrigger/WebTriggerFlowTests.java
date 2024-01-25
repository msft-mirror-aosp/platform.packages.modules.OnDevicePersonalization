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

package com.android.ondevicepersonalization.services.webtrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

@RunWith(JUnit4.class)
public class WebTriggerFlowTests {
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private OnDevicePersonalizationDbHelper mDbHelper;
    private UserPrivacyStatus mUserPrivacyStatus = UserPrivacyStatus.getInstance();

    @Before
    public void setup() {
        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
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
                new Query.Builder().setServicePackageName(mContext.getPackageName()).setQueryData(
                        queryDataBytes).build());
        when(mContext.checkCallingOrSelfPermission(anyString()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testRunWebTriggerFlow() throws Exception {
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(QueriesContract.QueriesEntry.TABLE_NAME, null,
                    null, null, null, null, null).getCount());
        assertEquals(0,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                    null, null, null, null, null).getCount());
        String triggerHeader = String.format(
                "{package: \"%s\", class: \"com.test.TestPersonalizationService\", data: \"ABCD\"}",
                mContext.getPackageName());
        WebTriggerFlow flow = new WebTriggerFlow(
                "http://landingpage", "http://regurl", triggerHeader, mContext,
                new TestInjector());
        var unused = flow.run().get();
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(QueriesContract.QueriesEntry.TABLE_NAME, null,
                    null, null, null, null, null).getCount());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                    null, null, null, null, null).getCount());
    }

    @Test
    public void testEnforceCallerPermission() throws Exception {
        when(mContext.checkCallingOrSelfPermission(anyString()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        String triggerHeader = String.format(
                "{package: \"%s\", class: \"com.test.TestPersonalizationService\", data: \"ABCD\"}",
                mContext.getPackageName());
        WebTriggerFlow flow = new WebTriggerFlow(
                "http://landingpage", "http://regurl", triggerHeader, mContext,
                new TestInjector());
        try {
            var unused = flow.run().get();
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof SecurityException);
        }
    }

    class TestInjector extends WebTriggerFlow.Injector {
        @Override ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override Flags getFlags() {
            return new Flags() {
                @Override public boolean getGlobalKillSwitch() {
                    return false;
                }
            };
        }
    }
}
