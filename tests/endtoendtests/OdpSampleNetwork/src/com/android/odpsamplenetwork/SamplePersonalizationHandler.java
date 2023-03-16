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
import android.ondevicepersonalization.EventMetricsInput;
import android.ondevicepersonalization.EventMetricsResult;
import android.ondevicepersonalization.EventUrlOptions;
import android.ondevicepersonalization.Metrics;
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.ondevicepersonalization.PersonalizationHandler;
import android.ondevicepersonalization.RemoteData;
import android.ondevicepersonalization.RenderContentInput;
import android.ondevicepersonalization.RenderContentResult;
import android.ondevicepersonalization.ScoredBid;
import android.ondevicepersonalization.SelectContentInput;
import android.ondevicepersonalization.SelectContentResult;
import android.ondevicepersonalization.SlotResult;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.util.JsonReader;
import android.util.Log;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class SamplePersonalizationHandler implements PersonalizationHandler {
    public static final String TAG = "SamplePersonalizationHandler";
    public static final int EVENT_TYPE_IMPRESSION = 1;
    public static final int EVENT_TYPE_CLICK = 2;
    public static final double COST_RAISING_FACTOR = 2.0;
    private static final int MAX_ADS = 100;
    private static final String BID_PRICE_KEY = "bidprice";
    private static final Set<String> sBlockedKeywords = Set.of("cars", "trucks");

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

    @Override public void computeEventMetrics(
            @NonNull EventMetricsInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<EventMetricsResult> consumer) {
        Log.d(TAG, "computeEventMetrics() started.");
        sBackgroundExecutor.execute(
                () -> handleComputeEventMetricsRequest(input, odpContext, consumer));
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

    private FluentFuture<List<Ad>> readAds(RemoteData remoteData) {
        Log.d(TAG, "readAds() called.");
        ArrayList<String> keys = new ArrayList<>();
        for (int i = 1; i <= MAX_ADS; ++i) {
            keys.add("ad" + i);
        }
        return FluentFuture.from(readRemoteData(remoteData, keys))
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

    private boolean isMatch(Ad ad, String requestKeyword) {
        if (ad.mTargetKeyword != null && !ad.mTargetKeyword.isEmpty()) {
            if (!ad.mTargetKeyword.equals(requestKeyword)) {
                return false;
            }
        }
        if (ad.mExcludeKeyword != null && !ad.mExcludeKeyword.isEmpty()) {
            if (ad.mExcludeKeyword.equals(requestKeyword)) {
                return false;
            }
        }
        return true;
    }

    private List<Ad> matchAds(List<Ad> ads, SelectContentInput input) {
        Log.d(TAG, "matchAds() called.");
        String requestKeyword = "";
        if (input != null && input.getAppParams() != null
                && input.getAppParams().getString("keyword") != null) {
            requestKeyword = input.getAppParams().getString("keyword");
        }
        List<Ad> result = new ArrayList<>();
        for (Ad ad: ads) {
            if (isMatch(ad, requestKeyword)) {
                result.add(ad);
            }
        }
        return result;
    }

    private Ad runAuction(List<Ad> ads) {
        Log.d(TAG, "runAuction() called.");
        Ad winner = null;
        double maxPrice = 0.0;
        for (Ad ad: ads) {
            if (ad.mPrice > maxPrice) {
                winner = ad;
                maxPrice = ad.mPrice;
            }
        }
        return winner;
    }

    private SelectContentResult buildResult(Ad ad) {
        Log.d(TAG, "buildResult() called.");
        PersistableBundle eventParams = new PersistableBundle();
        // Duplicate ad price in event parameters.
        // TODO(b/259950177): Update cost raising API to provide query/bid
        // during cost raising, then remove this workaround.
        eventParams.putDouble(BID_PRICE_KEY, ad.mPrice);
        return new SelectContentResult.Builder()
                .addSlotResults(
                    new SlotResult.Builder()
                        .addWinningBids(
                            new ScoredBid.Builder()
                                .setBidId(ad.mId)
                                .setPrice(ad.mPrice)
                                .setScore(ad.mPrice * 10)
                                .setEventsWithMetrics(EVENT_TYPE_CLICK)
                                .setEventMetricsParameters(eventParams)
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
                        ads -> buildResult(runAuction(matchAds(ads, input))),
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

    private ListenableFuture<String> getEventUrl(
            int eventType, String bidId, String landingPage,
            OnDevicePersonalizationContext odpContext) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            Log.d(TAG, "getEventUrl(): " + eventType);
            int responseType = landingPage != null
                    ? EventUrlOptions.RESPONSE_TYPE_NO_CONTENT
                    : EventUrlOptions.RESPONSE_TYPE_REDIRECT;
            odpContext.getEventUrl(
                    eventType,
                    bidId,
                    new EventUrlOptions.Builder()
                        .setResponseType(responseType)
                        .setDestinationUrl(landingPage)
                        .build(),
                    sBackgroundExecutor,
                    new OutcomeReceiver<String, Exception>() {
                        @Override public void onResult(String result) {
                            completer.set(result);
                        }
                        @Override public void onError(Exception e) {
                            completer.setException(e);
                        }
                    });
            return "getEventUrl";
        });
    }

    private FluentFuture<Ad> readAd(String id, RemoteData remoteData) {
        return FluentFuture.from(readRemoteData(remoteData, List.of(id)))
                .transform(
                    result -> parseAd(id, result.get(id)),
                    sBackgroundExecutor
                );
    }

    private RenderContentResult buildRenderContentResult(
            Ad ad, String impressionUrl, String clickUrl) {
        String content =
                "<img src=\"" + impressionUrl + "\">\n"
                + "<a href=\"" + clickUrl + "\">" + ad.mText + "</a>";
        Log.d(TAG, "content: " + content);
        return new RenderContentResult.Builder().setContent(content).build();
    }

    private void handleRenderContentRequest(
            @NonNull RenderContentInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<RenderContentResult> consumer
    ) {
        try {
            Log.d(TAG, "handleRenderContentRequest() started.");
            String id = input.getBidIds().get(0);
            var adFuture = readAd(id, odpContext.getRemoteData());
            var impUrlFuture = getEventUrl(EVENT_TYPE_IMPRESSION, id, "", odpContext);
            var clickUrlFuture = adFuture.transformAsync(
                    ad -> getEventUrl(EVENT_TYPE_CLICK, id, ad.mLandingPage, odpContext),
                    sBackgroundExecutor);
            var unused = FluentFuture.from(
                    Futures.whenAllComplete(adFuture, impUrlFuture, clickUrlFuture)
                        .call(
                            () -> buildRenderContentResult(
                                Futures.getDone(adFuture), Futures.getDone(impUrlFuture),
                                Futures.getDone(clickUrlFuture)),
                            MoreExecutors.directExecutor()))
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
            Log.e(TAG, "handleRenderContentRequest failed.", e);
            consumer.accept(null);
        }
    }

    public void handleComputeEventMetricsRequest(
            @NonNull EventMetricsInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<EventMetricsResult> consumer) {
        try {
            Log.d(TAG, "handleComputeEventMetricsRequest() started.");
            if (input.getEventType() != EVENT_TYPE_CLICK) {
                consumer.accept(new EventMetricsResult.Builder().build());
                return;
            }
            double bidPrice = 0.0;
            if (input.getEventParams() != null) {
                bidPrice = input.getEventParams().getDouble(BID_PRICE_KEY);
            }
            double updatedPrice = bidPrice * COST_RAISING_FACTOR;
            EventMetricsResult result = new EventMetricsResult.Builder()
                    .setMetrics(new Metrics.Builder().setDoubleValues(updatedPrice).build())
                    .build();
            consumer.accept(result);
        } catch (Exception e) {
            Log.e(TAG, "handleComputeEventMetricsResult failed.", e);
            consumer.accept(null);
        }
    }

    boolean isBlockedAd(Ad ad) {
        return ad.mTargetKeyword != null && sBlockedKeywords.contains(ad.mTargetKeyword);
    }

    private List<String> getFilteredKeys(ParcelFileDescriptor fd) {
        Log.d(TAG, "getFilteredKeys() called.");
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
                            String key = null;
                            byte[] value = null;
                            while (reader.hasNext()) {
                                String elementName = reader.nextName();
                                if (elementName.equals("key")) {
                                    key = reader.nextString();
                                } else if (elementName.equals("data")) {
                                    value = reader.nextString().getBytes();
                                } else {
                                    reader.skipValue();
                                }
                            }
                            reader.endObject();
                            if (key != null && value != null) {
                                Ad ad = parseAd(key, value);
                                if (ad != null && !isBlockedAd(ad)) {
                                    filteredKeys.add(key);
                                }
                            }
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
        final String mText;
        Ad(String id, double price, String targetKeyword, String excludeKeyword,
                String landingPage, String text) {
            mId = id;
            mPrice = price;
            mTargetKeyword = targetKeyword;
            mExcludeKeyword = excludeKeyword;
            mLandingPage = landingPage;
            mText = text;
        }
    }

    Ad parseAd(String id, byte[] data) {
        if (id == null || data == null) {
            return null;
        }
        String dataStr = new String(data, StandardCharsets.UTF_8);
        Log.d(TAG, "parseAd: " + id + " " + dataStr);
        // TODO(b/263493591): Parse JSON ad.
        try (JsonReader reader = new JsonReader(new StringReader(dataStr))) {
            reader.beginObject();
            double price = 0.0;
            String targetKeyword = "";
            String excludeKeyword = "";
            String landingPage = "";
            String text = "Click Here!";
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("price")) {
                    price = reader.nextDouble();
                } else if (name.equals("keyword")) {
                    targetKeyword = reader.nextString();
                } else if (name.equals("excludekeyword")) {
                    excludeKeyword = reader.nextString();
                } else if (name.equals("landingPage")) {
                    landingPage = reader.nextString();
                } else if (name.equals("text")) {
                    text = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return new Ad(id, price, targetKeyword, excludeKeyword, landingPage, text);
        } catch (Exception e) {
            Log.e(TAG, "parseAd() failed.", e);
            return null;
        }
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
