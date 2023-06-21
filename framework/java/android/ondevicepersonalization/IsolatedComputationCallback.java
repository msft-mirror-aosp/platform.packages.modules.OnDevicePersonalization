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

package android.ondevicepersonalization;

import android.annotation.NonNull;

import java.util.function.Consumer;

/**
 * Interface for services that perform personalized computation using user data.
 * @hide
 */
public interface IsolatedComputationCallback {

    /**
     * Handle a request from an app. A {@link IsolatedComputationService} that
     * processes requests from apps must override this method.
     *
     * @param input App Request Parameters.
     * @param consumer Callback to be invoked on completion.
     */
    default void onExecute(
            @NonNull ExecuteInput input,
            @NonNull Consumer<ExecuteOutput> consumer
    ) {
        consumer.accept(null);
    }

    /**
     * Handle a completed download. The platform downloads content using the
     * parameters defined in the package manifest of the {@link IsolatedComputationService}
     * and calls this function after the download is complete.
     *
     * @param input Download handler parameters.
     * @param consumer Callback to be invoked on completion.
     */
    default void onDownload(
            @NonNull DownloadInput input,
            @NonNull Consumer<DownloadOutput> consumer
    ) {
        consumer.accept(null);
    }

    /**
     * Generate HTML for the results that were returned as a result of {@link execute}.
     * The platform will render this HTML in a WebView inside a fenced frame.
     *
     * @param input Parameters for the renderContent request.
     * @param consumer Callback to be invoked on completion.
     */
    default void onRender(
            @NonNull RenderInput input,
            @NonNull Consumer<RenderOutput> consumer
    ) {
        consumer.accept(null);
    }

    /**
     * Generate an event to be logged from an event tracking URL.
     *
     * @param input The parameters needed to compute event data.
     * @param consumer Callback to be invoked on completion.
     */
    default void onWebViewEvent(
            @NonNull WebViewEventInput input,
            @NonNull Consumer<WebViewEventOutput> consumer
    ) {
        consumer.accept(null);
    }
}