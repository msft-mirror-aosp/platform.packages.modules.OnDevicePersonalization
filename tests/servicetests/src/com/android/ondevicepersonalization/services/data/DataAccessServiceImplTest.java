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

package com.android.ondevicepersonalization.services.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.ondevicepersonalization.Constants;
import android.ondevicepersonalization.ScoredBid;
import android.ondevicepersonalization.SlotResult;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class DataAccessServiceImplTest {
    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();
    private long mTimeMillis = 1000;
    private EventUrlPayload mEventUrlPayload;
    private TestInjector mInjector = new TestInjector();
    private CountDownLatch mLatch = new CountDownLatch(1);
    private Bundle mResult;
    private int mErrorCode = 0;
    private boolean mOnSuccessCalled = false;
    private boolean mOnErrorCalled = false;

    @Before
    public void setup() {
        mInjector = new TestInjector();
    }

    @Test
    public void testGetEventUrl() throws Exception {
        Bundle params = new Bundle();
        params.putInt(Constants.EXTRA_EVENT_TYPE, 4);
        params.putString(Constants.EXTRA_BID_ID, "bid5");
        ArrayList<String> bidIds = new ArrayList<String>();
        SlotResult slotResult =
                new SlotResult.Builder()
                    .setSlotId("slot1")
                    .addWinningBids(
                        new ScoredBid.Builder()
                            .setBidId("bid5")
                            .setEventsWithMetrics(4)
                            .setEventMetricsParameters(null)
                            .build())
                    .build();
        DataAccessServiceImpl.EventUrlQueryData eventUrlData =
                new DataAccessServiceImpl.EventUrlQueryData(1357, slotResult);
        var serviceImpl = new DataAccessServiceImpl(
                "com.example.testapp", mApplicationContext.getPackageName(), mApplicationContext,
                true, eventUrlData, mInjector);
        IDataAccessService serviceProxy = IDataAccessService.Stub.asInterface(serviceImpl);
        serviceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_EVENT_URL,
                params,
                new TestCallback());
        mLatch.await();
        assertNotEquals(null, mResult);
        String eventUrl = mResult.getString(Constants.EXTRA_RESULT);
        assertNotEquals(null, eventUrl);
        EventUrlPayload payload = EventUrlHelper.getEventFromOdpEventUrl(eventUrl);
        assertNotEquals(null, payload);
        assertEquals(4, payload.getEvent().getType());
        assertEquals(1357, payload.getEvent().getQueryId());
        assertEquals(1000, payload.getEvent().getTimeMillis());
        assertEquals("slot1", payload.getEvent().getSlotId());
        assertEquals(mApplicationContext.getPackageName(),
                payload.getEvent().getServicePackageName());
        assertEquals("bid5", payload.getEvent().getBidId());
        assertTrue(payload.isEventMetricsRequired());
    }

    @Test
    public void testGetClickUrl() throws Exception {
        Bundle params = new Bundle();
        params.putInt(Constants.EXTRA_EVENT_TYPE, 4);
        params.putString(Constants.EXTRA_BID_ID, "bid5");
        params.putString(Constants.EXTRA_DESTINATION_URL, "http://example.com");
        ArrayList<String> bidIds = new ArrayList<String>();
        SlotResult slotResult =
                new SlotResult.Builder()
                    .setSlotId("slot1")
                    .addWinningBids(
                        new ScoredBid.Builder()
                            .setBidId("bid5")
                            .setEventsWithMetrics(4)
                            .setEventMetricsParameters(null)
                            .build())
                    .build();
        DataAccessServiceImpl.EventUrlQueryData eventUrlData =
                new DataAccessServiceImpl.EventUrlQueryData(1357, slotResult);
        var serviceImpl = new DataAccessServiceImpl(
                "com.example.testapp", mApplicationContext.getPackageName(), mApplicationContext,
                true, eventUrlData, mInjector);
        IDataAccessService serviceProxy = IDataAccessService.Stub.asInterface(serviceImpl);
        serviceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_EVENT_URL,
                params,
                new TestCallback());
        mLatch.await();
        assertNotEquals(null, mResult);
        String eventUrl = mResult.getString(Constants.EXTRA_RESULT);
        assertNotEquals(null, eventUrl);
        EventUrlPayload payload = EventUrlHelper.getEventFromOdpEventUrl(eventUrl);
        assertNotEquals(null, payload);
        assertEquals(4, payload.getEvent().getType());
        assertEquals(1357, payload.getEvent().getQueryId());
        assertEquals(1000, payload.getEvent().getTimeMillis());
        assertEquals("slot1", payload.getEvent().getSlotId());
        assertEquals(mApplicationContext.getPackageName(),
                payload.getEvent().getServicePackageName());
        assertEquals("bid5", payload.getEvent().getBidId());
        Uri uri = Uri.parse(eventUrl);
        assertEquals(uri.getQueryParameter(EventUrlHelper.URL_LANDING_PAGE_EVENT_KEY), "http://example.com");
    }

    class TestCallback extends IDataAccessServiceCallback.Stub {
        @Override public void onSuccess(Bundle result) {
            mResult = result;
            mOnSuccessCalled = true;
            mLatch.countDown();
        }
        @Override public void onError(int errorCode) {
            mErrorCode = errorCode;
            mOnErrorCalled = true;
            mLatch.countDown();
        }
    }

    class TestInjector extends DataAccessServiceImpl.Injector {
        long getTimeMillis() {
            return mTimeMillis;
        }

        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        OnDevicePersonalizationVendorDataDao getVendorDataDao(
                Context context, String packageName, String certDigest
        ) {
            return OnDevicePersonalizationVendorDataDao.getInstanceForTest(
                    context, packageName, certDigest);
        }
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mApplicationContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
