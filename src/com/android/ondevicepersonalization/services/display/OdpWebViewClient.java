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

import android.annotation.NonNull;
import android.content.Context;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsDao;

import java.util.concurrent.Executor;

class OdpWebViewClient extends WebViewClient {
    private static final String TAG = "OdpWebViewClient";
    private final Executor mExecutor;

    OdpWebViewClient() {
        this(OnDevicePersonalizationExecutors.getBackgroundExecutor());
    }

    @VisibleForTesting
    OdpWebViewClient(Executor executor) {
        this.mExecutor = executor;
    }

    @Override
    public boolean shouldOverrideUrlLoading(
            @NonNull WebView webView, @NonNull WebResourceRequest request) {
        if (webView == null || request == null) {
            Log.e(TAG, "Received null webView or Request");
            return true;
        }
        //Decode odp://localhost/ URIs and call Events table API to write an event.
        String url = request.getUrl().toString();
        if (EventUrlHelper.isOdpUrl(url)) {
            mExecutor.execute(() -> writeEvent(
                    webView.getContext(), url));
            String landingPage = request.getUrl().getQueryParameter(
                    EventUrlHelper.URL_LANDING_PAGE_EVENT_KEY);
            if (landingPage != null) {
                webView.loadUrl(landingPage);
            }
        } else {
            // TODO(b/263180569): Handle any non-odp URLs
            Log.d(TAG, "Non-odp URL encountered: " + url);
        }
        // Cancel the current load
        return true;
    }

    private void writeEvent(Context context, String url) {
        try {
            EventUrlPayload eventUrlPayload = EventUrlHelper.getEventFromOdpEventUrl(url);
            Event event = eventUrlPayload.getEvent();
            if (!EventsDao.getInstance(context).insertEvent(event)) {
                Log.e(TAG, "Failed to insert event: " + event);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt EventUrlPayload", e);
        }
    }
}
