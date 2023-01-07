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
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

class OdpWebViewClient extends WebViewClient {
    @Override public boolean shouldOverrideUrlLoading(
            @NonNull WebView webView, @NonNull WebResourceRequest request) {
        if (webView == null || request == null) {
            return false;
        }
        // TODO(b/242753206): Decode odp:// URIs and call Events table API to
        // write an event.
        return false;
    }
}
