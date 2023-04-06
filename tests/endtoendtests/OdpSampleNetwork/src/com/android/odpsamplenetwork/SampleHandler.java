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
import android.ondevicepersonalization.Bid;
import android.ondevicepersonalization.DownloadInput;
import android.ondevicepersonalization.DownloadOutput;
import android.ondevicepersonalization.EventInput;
import android.ondevicepersonalization.EventOutput;
import android.ondevicepersonalization.EventUrlOptions;
import android.ondevicepersonalization.ExecuteInput;
import android.ondevicepersonalization.ExecuteOutput;
import android.ondevicepersonalization.ImmutableMap;
import android.ondevicepersonalization.IsolatedComputationHandler;
import android.ondevicepersonalization.Metrics;
import android.ondevicepersonalization.OnDevicePersonalizationContext;
import android.ondevicepersonalization.RenderInput;
import android.ondevicepersonalization.RenderOutput;
import android.ondevicepersonalization.SlotResult;
import android.os.OutcomeReceiver;
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
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class SampleHandler implements IsolatedComputationHandler {
    public static final String TAG = "SampleHandler";
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
            @NonNull Consumer<DownloadOutput> consumer) {
        Log.d(TAG, "onDownload() started.");
        DownloadOutput downloadResult =
                new DownloadOutput.Builder()
                        .setKeysToRetain(getFilteredKeys(input.getData()))
                        .build();
        consumer.accept(downloadResult);
    }

    @Override public void onExecute(
            @NonNull ExecuteInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<ExecuteOutput> consumer
    ) {
        Log.d(TAG, "onExecute() started.");
        sBackgroundExecutor.execute(() -> handleOnExecute(input, odpContext, consumer));
    }

    @Override public void onRender(
            @NonNull RenderInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<RenderOutput> consumer
    ) {
        Log.d(TAG, "onRender() started.");
        sBackgroundExecutor.execute(() -> handleOnRender(input, odpContext, consumer));
    }

    @Override public void onEvent(
            @NonNull EventInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<EventOutput> consumer) {
        Log.d(TAG, "onEvent() started.");
        sBackgroundExecutor.execute(
                () -> handleOnEvent(input, odpContext, consumer));
    }

    private ListenableFuture<Map<String, byte[]>> readRemoteData(
            ImmutableMap remoteData, List<String> keys) {
        return ListenableFutureTask.create(() -> {
            Map<String, byte[]> result = new HashMap<>();
            for (String key : keys) {
                byte[] value = remoteData.get(key);
                if (null != value) {
                    result.put(key, value);
                }
            }
            return result;
        });
    }

    private FluentFuture<List<Ad>> readAds(ImmutableMap remoteData) {
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

    private List<Ad> matchAds(List<Ad> ads, ExecuteInput input) {
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

    private ExecuteOutput buildResult(Ad ad) {
        Log.d(TAG, "buildResult() called.");
        PersistableBundle eventParams = new PersistableBundle();
        // Duplicate ad price in event parameters.
        // TODO(b/259950177): Update cost raising API to provide query/bid
        // during cost raising, then remove this workaround.
        eventParams.putDouble(BID_PRICE_KEY, ad.mPrice);
        return new ExecuteOutput.Builder()
                .addSlotResults(
                    new SlotResult.Builder()
                        .addWinningBids(
                            new Bid.Builder()
                                .setBidId(ad.mId)
                                .setPrice(ad.mPrice)
                                .setScore(ad.mPrice * 10)
                                .setEventsWithMetrics(EVENT_TYPE_CLICK)
                                .setEventMetricsParameters(eventParams)
                                .build())
                        .build())
                .build();
    }

    private void handleOnExecute(
            @NonNull ExecuteInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<ExecuteOutput> consumer
    ) {
        try {
            ImmutableMap remoteData = odpContext.getRemoteData();

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
            Log.e(TAG, "handleOnExecute() failed", e);
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

    private FluentFuture<Ad> readAd(String id, ImmutableMap remoteData) {
        return FluentFuture.from(readRemoteData(remoteData, List.of(id)))
                .transform(
                    result -> parseAd(id, result.get(id)),
                    sBackgroundExecutor
                );
    }

    private RenderOutput buildRenderOutput(
            Ad ad, String impressionUrl, String clickUrl) {
        String content =
                "<img src=\"" + impressionUrl + "\">\n"
                + "<a href=\"" + clickUrl + "\">" + ad.mText + "</a>";
        Log.d(TAG, "content: " + content);
        return new RenderOutput.Builder().setContent(content).build();
    }

    private void handleOnRender(
            @NonNull RenderInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<RenderOutput> consumer
    ) {
        try {
            Log.d(TAG, "handleOnRender() started.");
            String id = input.getBidIds().get(0);
            var adFuture = readAd(id, odpContext.getRemoteData());
            var impUrlFuture = getEventUrl(EVENT_TYPE_IMPRESSION, id, "", odpContext);
            var clickUrlFuture = adFuture.transformAsync(
                    ad -> getEventUrl(EVENT_TYPE_CLICK, id, ad.mLandingPage, odpContext),
                    sBackgroundExecutor);
            var unused = FluentFuture.from(
                    Futures.whenAllComplete(adFuture, impUrlFuture, clickUrlFuture)
                        .call(
                            () -> buildRenderOutput(
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
            Log.e(TAG, "handleOnRender failed.", e);
            consumer.accept(null);
        }
    }

    public void handleOnEvent(
            @NonNull EventInput input,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull Consumer<EventOutput> consumer) {
        try {
            Log.d(TAG, "handleOnEvent() started.");
            if (input.getEventType() != EVENT_TYPE_CLICK) {
                consumer.accept(new EventOutput.Builder().build());
                return;
            }
            double bidPrice = 0.0;
            if (input.getEventParams() != null) {
                bidPrice = input.getEventParams().getDouble(BID_PRICE_KEY);
            }
            double updatedPrice = bidPrice * COST_RAISING_FACTOR;
            EventOutput result = new EventOutput.Builder()
                    .setMetrics(new Metrics.Builder().setDoubleValues(updatedPrice).build())
                    .build();
            consumer.accept(result);
        } catch (Exception e) {
            Log.e(TAG, "handleOnEvent failed.", e);
            consumer.accept(null);
        }
    }

    boolean isBlockedAd(Ad ad) {
        return ad.mTargetKeyword != null && sBlockedKeywords.contains(ad.mTargetKeyword);
    }

    private List<String> getFilteredKeys(Map<String, byte[]> data) {
        Log.d(TAG, "getFilteredKeys() called.");
        List<String> filteredKeys = new ArrayList<String>();
        // Add all keys from the file into the list
        for (String key : data.keySet()) {
            if (key != null && data.get(key) != null) {
                Ad ad = parseAd(key, data.get(key));
                if (ad != null && !isBlockedAd(ad)) {
                    filteredKeys.add(key);
                }
            }
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
