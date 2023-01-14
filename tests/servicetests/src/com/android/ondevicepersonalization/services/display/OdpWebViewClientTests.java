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

package com.android.ondevicepersonalization.services.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventType;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(JUnit4.class)
public class OdpWebViewClientTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final Event mTestEvent = new Event.Builder()
            .setType(EventType.B2D.getValue())
            .setEvent("event".getBytes(StandardCharsets.UTF_8))
            .setBidId("bidId")
            .setServicePackageName("servicePackageName")
            .setSlotId("slotId")
            .setSlotPosition(1)
            .setThreadId(1L)
            .setTimeUsec(1L)
            .build();
    private final EventUrlPayload mTestEventPayload = new EventUrlPayload.Builder()
            .setEvent(mTestEvent).build();
    private final Query mTestQuery = new Query.Builder()
            .setTimeUsec(1L)
            .setThreadId(1L)
            .setQuery("query".getBytes(StandardCharsets.UTF_8))
            .build();
    private EventsDao mDao;
    private OnDevicePersonalizationDbHelper mDbHelper;
    private OdpWebView mWebView;

    @Before
    public void setup() throws Exception {
        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        mDao = EventsDao.getInstanceForTest(mContext);
        // Insert query for FK constraint
        mDao.insertQuery(mTestQuery);

        CountDownLatch latch = new CountDownLatch(1);
        OnDevicePersonalizationExecutors.getHandler().postAtFrontOfQueue(() -> {
            mWebView = new OdpWebView(mContext);
            latch.countDown();
        });
        latch.await();
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testValidUrl() throws Exception {
        WebViewClient webViewClient = new OdpWebViewClient(MoreExecutors.directExecutor());
        String odpUrl = EventUrlHelper.getEncryptedOdpEventUrl(mTestEventPayload);
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(Uri.parse(odpUrl));
        assertTrue(webViewClient.shouldOverrideUrlLoading(mWebView, webResourceRequest));
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testValidUrlWithRedirect() throws Exception {
        String landingPage = "https://www.google.com";
        String odpUrl = EventUrlHelper.getEncryptedClickTrackingUrl(mTestEventPayload, landingPage);
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(Uri.parse(odpUrl));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        AtomicReference<String> actualLandingPage = new AtomicReference<>();
        OnDevicePersonalizationExecutors.getHandler().postAtFrontOfQueue(() -> {
            WebViewClient webViewClient = new OdpWebViewClient(MoreExecutors.directExecutor());
            result.set(webViewClient.shouldOverrideUrlLoading(mWebView, webResourceRequest));
            actualLandingPage.set(mWebView.getLastLoadedUrl());
            latch.countDown();
        });
        latch.await();

        assertTrue(result.get());
        assertEquals(landingPage, actualLandingPage.get());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testInvalidUrl() {
        WebViewClient webViewClient = new OdpWebViewClient(MoreExecutors.directExecutor());
        WebResourceRequest webResourceRequest = new OdpWebResourceRequest(
                Uri.parse("https://www.google.com"));
        assertTrue(webViewClient.shouldOverrideUrlLoading(mWebView, webResourceRequest));
        assertEquals(0,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    static class OdpWebView extends WebView {
        private String mLastLoadedUrl;

        OdpWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void loadUrl(String url) {
            mLastLoadedUrl = url;
        }

        public String getLastLoadedUrl() {
            return mLastLoadedUrl;
        }

    }

    static class OdpWebResourceRequest implements WebResourceRequest {
        Uri mUri;

        OdpWebResourceRequest(Uri uri) {
            this.mUri = uri;
        }

        @Override
        public Uri getUrl() {
            return mUri;
        }

        @Override
        public boolean isForMainFrame() {
            return false;
        }

        @Override
        public boolean isRedirect() {
            return false;
        }

        @Override
        public boolean hasGesture() {
            return false;
        }

        @Override
        public String getMethod() {
            return null;
        }

        @Override
        public Map<String, String> getRequestHeaders() {
            return null;
        }
    }
}
