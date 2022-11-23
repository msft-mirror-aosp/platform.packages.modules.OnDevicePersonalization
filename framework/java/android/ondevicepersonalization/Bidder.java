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
import android.ondevicepersonalization.rtb.BidRequest;

/**
 * Interface for On-Device Bidders. Bidders run in the OnDevicePersonalization
 * sandbox and return bids in response to Exchanges.
 *
 * @hide
 */
public interface Bidder {
    /**
     * Return a list of bids to an exchange.
     * @param bidRequest The {@link BidRequest} from the exchange.
     * @param odpContext The {@link OnDevicePersonalizationContext} for this request.
     */
    void requestBids(
            @NonNull BidRequest bidRequest, @NonNull OnDevicePersonalizationContext odpContext);
}
