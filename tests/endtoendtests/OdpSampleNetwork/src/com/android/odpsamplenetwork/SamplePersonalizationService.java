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
import android.ondevicepersonalization.DownloadInput;
import android.ondevicepersonalization.DownloadResult;
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.ondevicepersonalization.PersonalizationService;
import android.ondevicepersonalization.RemoteData;
import android.ondevicepersonalization.RenderContentInput;
import android.ondevicepersonalization.RenderContentResult;
import android.ondevicepersonalization.ScoredBid;
import android.ondevicepersonalization.SelectContentInput;
import android.ondevicepersonalization.SelectContentResult;
import android.ondevicepersonalization.SlotResult;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.util.JsonReader;
import android.util.Log;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class SamplePersonalizationService extends PersonalizationService {
    public final String TAG = "SamplePersonalizationService";
    public static final int EVENT_TYPE_IMPRESSION = 1;
    public static final int EVENT_TYPE_CLICK = 2;
    private static final ListeningExecutorService sBackgroundExecutor =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(
                    /* nThreads */ 4,
                    createThreadFactory("BG Thread", Process.THREAD_PRIORITY_BACKGROUND,
                            Optional.of(getIoThreadPolicy()))));

    @Override
    public void onDownload(
            @NonNull DownloadInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<DownloadResult> consumer) {
        Log.d(TAG, "onDownload() started.");
        DownloadResult downloadResult =
                new DownloadResult.Builder()
                        .setKeysToRetain(getFilteredKeys(input.getParcelFileDescriptor()))
                        .build();
        consumer.accept(downloadResult);
    }

    @Override public void selectContent(
            @NonNull SelectContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<SelectContentResult> consumer
    ) {
        Log.d(TAG, "selectContent() started.");
        sBackgroundExecutor.execute(() -> handleSelectContentRequest(input, odpContext, consumer));
    }

    @Override public void renderContent(
            @NonNull RenderContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<RenderContentResult> consumer
    ) {
        Log.d(TAG, "renderContent() started.");
        sBackgroundExecutor.execute(() -> handleRenderContentRequest(input, odpContext, consumer));
    }

    private ListenableFuture<Map<String, byte[]>> readRemoteData(
            RemoteData remoteData, List<String> keys) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            remoteData.lookup(
                    keys,
                    sBackgroundExecutor,
                    new OutcomeReceiver<Map<String, byte[]>, Exception>() {
                        @Override public void onResult(Map<String, byte[]> result) {
                            completer.set(result);
                        }
                        @Override public void onError(Exception e) {
                            completer.setException(e);
                        }
                    });
            return "readRemoteData";
        });
    }

    FluentFuture<Integer> getNumAds(RemoteData remoteData) {
        Log.d(TAG, "getNumAds() called.");
        return FluentFuture.from(readRemoteData(remoteData, List.of("numads")))
                .transform(
                    result -> Integer.parseInt(new String(result.get("numads"))),
                    sBackgroundExecutor
                );
    }

    FluentFuture<List<Ad>> readAds(RemoteData remoteData) {
        Log.d(TAG, "readAds() called.");
        return FluentFuture.from(readRemoteData(remoteData, List.of("numads")))
                .transformAsync(
                    result -> {
                        int numAds = Integer.parseInt(new String(result.get("numads")));
                        ArrayList<String> keys = new ArrayList<>();
                        for (int i = 1; i <= numAds; ++i) {
                            keys.add("ad" + i);
                        }
                        return readRemoteData(remoteData, keys);
                    },
                    sBackgroundExecutor
                )
                .transform(
                    result -> {
                        ArrayList<Ad> ads = new ArrayList<>();
                        for (var entry: result.entrySet()) {
                            Ad ad = parseAd(entry.getKey(), entry.getValue());
                            if (ad != null) {
                                ads.add(ad);
                            }
                        }
                        return ads;
                    },
                    sBackgroundExecutor
                );
    }

    List<Ad> filterAds(List<Ad> ads, SelectContentInput input) {
        Log.d(TAG, "filterAds() called.");
        // TODO(b/263493591): Implement match logic.
        return ads;
    }

    Ad runAuction(List<Ad> ads) {
        Log.d(TAG, "runAuction() called.");
        // TODO(b/263493591): Implement auction logic.
        return ads.get(0);
    }

    SelectContentResult buildResult(Ad ad) {
        Log.d(TAG, "buildResult() called.");
        return new SelectContentResult.Builder()
                .addSlotResults(
                    new SlotResult.Builder()
                        .addWinningBids(
                            new ScoredBid.Builder()
                                .setBidId(ad.mId)
                                .setPrice(ad.mPrice)
                                .setScore(ad.mPrice * 10)
                                .setEventsWithMetrics(EVENT_TYPE_CLICK)
                                .build())
                        .build())
                .build();
    }

    private void handleSelectContentRequest(
            @NonNull SelectContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<SelectContentResult> consumer
    ) {
        try {
            RemoteData remoteData = odpContext.getRemoteData();

            var unused = readAds(remoteData)
                    .transform(
                        ads -> buildResult(runAuction(filterAds(ads, input))),
                        sBackgroundExecutor)
                    .transform(
                        result -> {
                            consumer.accept(result);
                            return null;
                        },
                        MoreExecutors.directExecutor())
                    .catching(
                        Exception.class,
                        e -> {
                            Log.e(TAG, "Execution failed.", e);
                            consumer.accept(null);
                            return null;
                        },
                        MoreExecutors.directExecutor());

        } catch (Exception e) {
            Log.e(TAG, "handleSelectContentRequest() failed", e);
            consumer.accept(null);
        }
    }

    public void handleRenderContentRequest(
            @NonNull RenderContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<RenderContentResult> consumer
    ) {
        Log.d(TAG, "renderContent() started.");
        String content = "<h2>Winners</h2>" + String.join(",", input.getBidIds()) + "<p>";
        RenderContentResult result =
                new RenderContentResult.Builder()
                        .setContent(content).build();
        Log.d(TAG, "renderContent() finished.");
        consumer.accept(result);
    }

    private List<String> getFilteredKeys(ParcelFileDescriptor fd) {
        List<String> filteredKeys = new ArrayList<String>();
        // Add all keys from the file into the list
        try (InputStream in =
                     new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("contents")) {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            reader.beginObject();
                            while (reader.hasNext()) {
                                String elementName = reader.nextName();
                                if (elementName.equals("key")) {
                                    filteredKeys.add(reader.nextString());
                                } else {
                                    reader.skipValue();
                                }
                            }
                            reader.endObject();
                        }
                        reader.endArray();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse downloaded data from fd");
        }
        return filteredKeys;
    }

    private static ThreadFactory createThreadFactory(
            final String name, final int priority,
            final Optional<StrictMode.ThreadPolicy> policy) {
        return new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat(name + " #%d")
            .setThreadFactory(
                    new ThreadFactory() {
                        @Override
                        public Thread newThread(final Runnable runnable) {
                            return new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    if (policy.isPresent()) {
                                        StrictMode.setThreadPolicy(policy.get());
                                    }
                                    // Process class operates on the current thread.
                                    Process.setThreadPriority(priority);
                                    runnable.run();
                                }
                            });
                        }
                    })
            .build();
    }

    static class Ad {
        final String mId;
        final double mPrice;
        final String mTargetKeyword;
        final String mExcludeKeyword;
        final String mLandingPage;
        Ad(String id, double price, String targetKeyword, String excludeKeyword,
                String landingPage) {
            mId = id;
            mPrice = price;
            mTargetKeyword = targetKeyword;
            mExcludeKeyword = excludeKeyword;
            mLandingPage = landingPage;
        }
    }

    Ad parseAd(String id, byte[] data) {
        Log.d(TAG, "parseAd: " + id + " " + new String(data));
        // TODO(b/263493591): Parse JSON ad.
        return new Ad(id, 1.0, "", "", "");
    }

    private static ThreadPolicy getIoThreadPolicy() {
        return new ThreadPolicy.Builder()
                .detectNetwork()
                .detectResourceMismatches()
                .detectUnbufferedIo()
                .penaltyLog()
                .build();
    }
}
