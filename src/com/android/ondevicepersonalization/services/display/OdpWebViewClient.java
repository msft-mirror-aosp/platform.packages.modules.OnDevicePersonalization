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
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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

    static class Injector {
        Executor getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        void openUrl(String landingPage, Context context) {
            if (landingPage != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(landingPage));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }

    private final Injector mInjector;

    OdpWebViewClient() {
        this(new Injector());
    }

    @VisibleForTesting
    OdpWebViewClient(Injector injector) {
        this.mInjector = injector;
    }

    @Override public WebResourceResponse shouldInterceptRequest(
            @NonNull WebView webView, @NonNull WebResourceRequest request) {
        if (webView == null || request == null || request.getUrl() == null) {
            Log.e(TAG, "Received null webView or Request or Url");
            return null;
        }
        String url = request.getUrl().toString();
        if (EventUrlHelper.isOdpUrl(url)) {
            mInjector.getExecutor().execute(() -> writeEvent(
                    webView.getContext(), url));
            // TODO(b/242753206): Return an empty response.
        }
        return null;
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
            mInjector.getExecutor().execute(() -> writeEvent(
                    webView.getContext(), url));
            String landingPage = request.getUrl().getQueryParameter(
                    EventUrlHelper.URL_LANDING_PAGE_EVENT_KEY);
            mInjector.openUrl(landingPage, webView.getContext());
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
