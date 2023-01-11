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

package com.android.odpsamplenetwork;

import android.annotation.NonNull;
import android.ondevicepersonalization.AppRequestInput;
import android.ondevicepersonalization.AppRequestResult;
import android.ondevicepersonalization.DownloadInput;
import android.ondevicepersonalization.DownloadResult;
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.ondevicepersonalization.PersonalizationService;
import android.ondevicepersonalization.RenderContentInput;
import android.ondevicepersonalization.RenderContentResult;
import android.ondevicepersonalization.ScoredBid;
import android.ondevicepersonalization.SlotResult;
import android.util.Log;

public class SamplePersonalizationService extends PersonalizationService {
    public final String TAG = "SamplePersonalizationService";

    @Override
    public void onDownload(
            @NonNull DownloadInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull PersonalizationService.Callback<DownloadResult> callback) {
        Log.d(TAG, "onDownload() started.");
    }

    @Override public void onAppRequest(
            @NonNull AppRequestInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull PersonalizationService.Callback<AppRequestResult> callback
    ) {
        Log.d(TAG, "onAppRequest() started.");
        SlotResult.Builder slotResultBuilder = new SlotResult.Builder();
        slotResultBuilder.addWinningBids(
                new ScoredBid.Builder()
                        .setBidId("winningbid1").setPrice(5.0).setScore(10.0).build());
        slotResultBuilder.addRejectedBids(
                new ScoredBid.Builder()
                        .setBidId("losingbid1").setPrice(1.0).setScore(1.0).build());
        AppRequestResult result =
                new AppRequestResult.Builder()
                        .addSlotResults(slotResultBuilder.build())
                        .build();
        Log.d(TAG, "onAppRequest() finished.");
        callback.onResult(result);
    }

    @Override public void renderContent(
            @NonNull RenderContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull PersonalizationService.Callback<RenderContentResult> callback
    ) {
        Log.d(TAG, "renderContent() started.");
        String content = "<h2>Winners</h2>" + String.join(",", input.getBidIds()) + "<p>";
        RenderContentResult result =
                new RenderContentResult.Builder()
                        .setContent(content).build();
        Log.d(TAG, "renderContent() finished.");
        callback.onResult(result);
    }
}
