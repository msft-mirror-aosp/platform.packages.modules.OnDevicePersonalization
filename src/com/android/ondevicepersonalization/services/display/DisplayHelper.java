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

package com.android.ondevicepersonalization.services.display;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.ondevicepersonalization.RenderContentResult;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.webkit.WebView;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.ListenableFuture;

/** Helper class to display personalized content. */
public class DisplayHelper {
    private static final String TAG = "DisplayHelper";
    @NonNull private final Context mContext;

    public DisplayHelper(Context context) {
        mContext = context;
    }

    /** Generates an HTML string from the template data in RenderContentResult. */
    @NonNull public String generateHtml(
            @NonNull RenderContentResult renderContentResult) {
        // TODO(b/263180569): Use the template and params from renderContentResult.
        return renderContentResult.getContent();
    }

    /** Creates a webview and displays the provided HTML. */
    @NonNull public ListenableFuture<SurfacePackage> displayHtml(
            @NonNull String html, @NonNull IBinder hostToken,
            int displayId, int width, int height) {
        return CallbackToFutureAdapter.getFuture(
            completer -> {
                try {
                    Log.d(TAG, "displayHtml");
                    OnDevicePersonalizationExecutors.getHandler().post(() -> {
                        createWebView(html, hostToken, displayId, width, height,
                                new OutcomeReceiver<SurfacePackage, Exception>() {
                                    @Override public void onResult(SurfacePackage surfacePackage) {
                                        completer.set(surfacePackage);
                                    }
                                    @Override public void onError(Exception e) {
                                        completer.setException(e);
                                    }
                                });
                    });
                } catch (Exception e) {
                    completer.setException(e);
                }
                return "displayHtml";
            }
        );

    }

    private void createWebView(
            @NonNull String html, @NonNull IBinder hostToken, int displayId, int width, int height,
            @NonNull OutcomeReceiver<SurfacePackage, Exception> receiver) {
        try {
            Log.d(TAG, "createWebView() started");
            WebView webView = new WebView(mContext);
            webView.setWebViewClient(new OdpWebViewClient());
            webView.loadData(html, "text/html; charset=utf-8", "UTF-8");
            Display display = mContext.getSystemService(DisplayManager.class).getDisplay(displayId);
            SurfaceControlViewHost host = new SurfaceControlViewHost(mContext, display, hostToken);
            host.setView(webView, width, height);
            SurfacePackage surfacePackage = host.getSurfacePackage();
            Log.d(TAG, "createWebView success: " + surfacePackage);
            receiver.onResult(surfacePackage);
        } catch (Exception e) {
            Log.d(TAG, "createWebView failed", e);
            receiver.onError(e);
        }
    }
}
