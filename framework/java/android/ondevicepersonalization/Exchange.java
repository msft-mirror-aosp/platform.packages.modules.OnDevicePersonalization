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

package android.ondevicepersonalization;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.ondevicepersonalization.rtb.Bid;
import android.os.PersistableBundle;

/**
 * Interface for On-Device Exchanges. Exchanges run in the OnDevicePersonalization
 * sandbox, receive requests from apps or SDKs, and return content that will be
 * rendered in a WebView. Exchanges can also make requests to Bidders that also run
 * in the OnDevicePersonalization sandbox.
 *
 * @hide
 */
public interface Exchange {
    // TODO(b/228200518): Add an init API to improve latency if needed.

    /**
     * Handle a request from an app or SDK.
     * @param params Data provided by the calling app or SDK.
     * @param sandboxContext The {@link SandboxContext} for this request.
     */
    void handleRequest(@Nullable PersistableBundle params, @NonNull SandboxContext sandboxContext);

    /**
     * Computes a ranking for the provided {@link Bid} that was returned by a {@link Bidder}.
     * @param bidderInfo The bidder that returned this bid.
     * @param bid A bid from a {@link Bidder}
     * @param sandboxContext The {@link SandboxContext} for this request.
     * @return A numerical score for the bid. If 0, the bid is dropped.
     */
    // TODO(b/19460933): Should bids be scored separately or together?
    double scoreBid(
            @NonNull PackageId bidderInfo,
            @NonNull Bid bid,
            @NonNull SandboxContext sandboxContext
    );
}
