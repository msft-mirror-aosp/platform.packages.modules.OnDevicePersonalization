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

package com.example.odpsamplenetwork;

import android.adservices.ondevicepersonalization.AppInfo;
import android.adservices.ondevicepersonalization.DownloadCompletedInput;
import android.adservices.ondevicepersonalization.DownloadCompletedOutput;
import android.adservices.ondevicepersonalization.EventInput;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.EventOutput;
import android.adservices.ondevicepersonalization.EventUrlProvider;
import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.FederatedComputeInput;
import android.adservices.ondevicepersonalization.FederatedComputeScheduler;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.KeyValueStore;
import android.adservices.ondevicepersonalization.RenderInput;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.TrainingExampleInput;
import android.adservices.ondevicepersonalization.TrainingExampleOutput;
import android.adservices.ondevicepersonalization.TrainingInterval;
import android.adservices.ondevicepersonalization.UserData;
import android.content.ContentValues;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import com.google.setfilters.cuckoofilter.CuckooFilter;

import org.tensorflow.example.BytesList;
import org.tensorflow.example.Example;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;
import org.tensorflow.example.Int64List;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class SampleHandler implements IsolatedWorker {
    public static final String TAG = "OdpSampleNetwork";
    public static final int EVENT_TYPE_IMPRESSION = 1;
    public static final int EVENT_TYPE_CLICK = 2;
    public static final double COST_RAISING_FACTOR = 2.0;
    private static final String AD_ID_KEY = "adid";
    private static final String BID_PRICE_KEY = "price";
    private static final String AUCTION_SCORE_KEY = "score";
    private static final String CLICK_COST_KEY = "clkcost";
    private static final String EVENT_TYPE_KEY = "type";
    private static final int BID_PRICE_OFFSET = 0;
    private static final String TRANSPARENT_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAA"
            + "AAXNSR0IArs4c6QAAAAtJREFUGFdjYAACAAAFAAGq1chRAAAAAElFTkSuQmCC";
    private static final byte[] TRANSPARENT_PNG_BYTES = Base64.decode(TRANSPARENT_PNG_BASE64, 0);

    private static final ListeningExecutorService sBackgroundExecutor =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(
                    /* nThreads */ 4,
                    createThreadFactory("BG Thread", Process.THREAD_PRIORITY_BACKGROUND,
                            Optional.of(getIoThreadPolicy()))));

    private final KeyValueStore mRemoteData;
    private final EventUrlProvider mEventUrlProvider;
    private final UserData mUserData;
    private final FederatedComputeScheduler mFCScheduler;

    SampleHandler(KeyValueStore remoteData, EventUrlProvider eventUrlProvider,
            UserData userData, FederatedComputeScheduler fcScheduler) {
        mRemoteData = remoteData;
        mEventUrlProvider = eventUrlProvider;
        mUserData = userData;
        mFCScheduler = fcScheduler;
        if (mRemoteData == null) {
            Log.e(TAG, "RemoteData missing");
        }
        if (mEventUrlProvider == null) {
            Log.e(TAG, "EventUrlProvider missing");
        }
        if (mUserData == null) {
            Log.e(TAG, "UserData missing");
        }
        if (mFCScheduler == null) {
            Log.e(TAG, "Federated Compute Scheduler missing");
        }
    }

    @Override
    public void onDownloadCompleted(
            @NonNull DownloadCompletedInput input,
            @NonNull Consumer<DownloadCompletedOutput> consumer) {
        Log.d(TAG, "onDownload() started.");
        DownloadCompletedOutput downloadResult =
                new DownloadCompletedOutput.Builder()
                        .setRetainedKeys(getFilteredKeys(input.getData()))
                        .build();
        consumer.accept(downloadResult);
    }

    @Override public void onExecute(
            @NonNull ExecuteInput input,
            @NonNull Consumer<ExecuteOutput> consumer
    ) {
        Log.d(TAG, "onExecute() started.");
        sBackgroundExecutor.execute(() -> handleOnExecute(input, consumer));
    }

    @Override public void onTrainingExample(
            @NonNull TrainingExampleInput input,
            @NonNull Consumer<TrainingExampleOutput> consumer) {
        Log.d(TAG, "onTrainingExample() started.");
        sBackgroundExecutor.execute(() -> handleOnTrainingExample(input, consumer));
    }

    @Override public void onRender(
            @NonNull RenderInput input,
            @NonNull Consumer<RenderOutput> consumer
    ) {
        Log.d(TAG, "onRender() started.");
        sBackgroundExecutor.execute(() -> handleOnRender(input, consumer));
    }

    @Override public void onEvent(
            @NonNull EventInput input,
            @NonNull Consumer<EventOutput> consumer) {
        Log.d(TAG, "onEvent() started.");
        sBackgroundExecutor.execute(
                () -> handleOnWebViewEvent(input, consumer));
    }

    private ListenableFuture<List<Ad>> readAds(KeyValueStore remoteData) {
        Log.d(TAG, "readAds() called.");
        try {
            ArrayList<Ad> ads = new ArrayList<>();
            for (var key: remoteData.keySet()) {
                Ad ad = parseAd(key, remoteData.get(key));
                if (ad != null) {
                    ads.add(ad);
                }
            }
            return Futures.immediateFuture(ads);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private boolean isMatch(Ad ad, String requestKeyword) {
        if (ad.mTargetKeywords != null && !ad.mTargetKeywords.isEmpty()) {
            if (!ad.mTargetKeywords.contains(requestKeyword)) {
                return false;
            }
        }
        if (ad.mTargetApps != null && !ad.mTargetApps.isEmpty()) {
            if (!isInstalledAppFound(ad.mTargetApps)) {
                return false;
            }
        }
        if (ad.mExcludes != null && !ad.mExcludes.isEmpty()) {
            if (ad.mExcludes.contains(requestKeyword)) {
                return false;
            }
            if (isInstalledAppFound(ad.mExcludes)) {
                return false;
            }
        }
        if (ad.mTargetKeywordFilter != null) {
            if (!ad.mTargetKeywordFilter.contains(requestKeyword)) {
                return false;
            }
        }
        if (ad.mExcludeFilter != null) {
            if (ad.mExcludeFilter.contains(requestKeyword)) {
                return false;
            }
            if (isInstalledAppFound(ad.mExcludeFilter)) {
                return false;
            }
        }
        if (ad.mTargetAppFilter != null) {
            if (!isInstalledAppFound(ad.mTargetAppFilter)) {
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

    private ContentValues createLogRecord(String adId, double price, double score) {
        ContentValues result = new ContentValues();
        result.put(AD_ID_KEY, adId);
        result.put(BID_PRICE_KEY, price);
        result.put(AUCTION_SCORE_KEY, score);
        return result;
    }

    private ExecuteOutput buildResult(Ad ad) {
        Log.d(TAG, "buildResult() called.");
        ContentValues logData = createLogRecord(ad.mId, ad.mPrice, ad.mPrice * 10.0);
        return new ExecuteOutput.Builder()
                .setRequestLogRecord(new RequestLogRecord.Builder().addRow(logData).build())
                .addRenderingConfig(new RenderingConfig.Builder().addKey(ad.mId).build())
                .build();
    }

    static Feature convertStringToFeature(String value) {
        BytesList.Builder bytesListBuilder = BytesList.newBuilder();
        String nonNullValue = Strings.nullToEmpty(value);
        bytesListBuilder.addValue(ByteString.copyFromUtf8(nonNullValue));
        return Feature.newBuilder().setBytesList(bytesListBuilder.build()).build();
    }

    static Feature convertLongToFeature(long value) {
        return Feature.newBuilder()
            .setInt64List(Int64List.newBuilder().addValue(value).build())
            .build();
    }

    private void handleOnTrainingExample(
            @NonNull TrainingExampleInput input,
            @NonNull Consumer<TrainingExampleOutput> consumer) {
        Features.Builder featuresBuilder = Features.newBuilder();

        featuresBuilder.putFeature("int-feature-1", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-2", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-3", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-4", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-5", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-6", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-7", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-8", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-9", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-10", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-11", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-12", convertLongToFeature(0L));
        featuresBuilder.putFeature("int-feature-13", convertLongToFeature(0L));

        featuresBuilder.putFeature("categorical-feature-14", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-15", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-16", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-17", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-18", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-19", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-20", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-21", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-22", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-23", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-24", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-25", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-26", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-27", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-28", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-29", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-30", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-31", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-32", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-33", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-34", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-35", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-36", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-37", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-38", convertStringToFeature(""));
        featuresBuilder.putFeature("categorical-feature-39", convertStringToFeature(""));

        featuresBuilder.putFeature("clicked", convertLongToFeature(1L));

        Example example = Example.newBuilder().setFeatures(featuresBuilder.build()).build();
        TrainingExampleOutput result = new TrainingExampleOutput
                .Builder()
                .addTrainingExample(example.toByteArray())
                .addResumptionToken("token1".getBytes()).build();
        consumer.accept(result);
    }

    private void handleOnExecute(
            @NonNull ExecuteInput input,
            @NonNull Consumer<ExecuteOutput> consumer
    ) {
        try {
            if (input != null && input.getAppParams() != null
                    && !input.getAppParams().getString("schedule_training").isEmpty()) {
                TrainingInterval interval = new TrainingInterval.Builder()
                        .setMinimumInterval(Duration.ofSeconds(10))
                        .setSchedulingMode(2)
                        .build();
                FederatedComputeScheduler.Params params = new FederatedComputeScheduler
                        .Params(interval);
                FederatedComputeInput fcInput = new FederatedComputeInput.Builder()
                        .setPopulationName(input.getAppParams().getString("schedule_training"))
                        .build();
                mFCScheduler.schedule(params, fcInput);

                ExecuteOutput result = new ExecuteOutput.Builder().build();
                consumer.accept(result);
            } else {
                var unused = FluentFuture.from(readAds(mRemoteData))
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
            }
        } catch (Exception e) {
            Log.e(TAG, "handleOnExecute() failed", e);
            consumer.accept(null);
        }
    }

    private ListenableFuture<String> getImpressionTrackingUrl() {
        try {
            PersistableBundle eventParams = new PersistableBundle();
            eventParams.putInt(EVENT_TYPE_KEY, EVENT_TYPE_IMPRESSION);
            String url = mEventUrlProvider.createEventTrackingUrlWithResponse(
                    eventParams, TRANSPARENT_PNG_BYTES, "image/png").toString();
            return Futures.immediateFuture(url);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<String> getClickTrackingUrl(
            String landingPage) {
        try {
            PersistableBundle eventParams = new PersistableBundle();
            eventParams.putInt(EVENT_TYPE_KEY, EVENT_TYPE_CLICK);
            String url = mEventUrlProvider.createEventTrackingUrlWithRedirect(
                    eventParams, Uri.parse(landingPage)).toString();
            return Futures.immediateFuture(url);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private ListenableFuture<Ad> readAd(String id, KeyValueStore remoteData) {
        try {
            return Futures.immediateFuture(parseAd(id, remoteData.get(id)));
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private RenderOutput buildRenderOutput(
            Ad ad, String impressionUrl, String clickUrl) {
        if (ad.mTemplateId != null) {
            PersistableBundle templateParams = new PersistableBundle();
            templateParams.putString("impressionUrl", impressionUrl);
            templateParams.putString("clickUrl", clickUrl);
            templateParams.putString("adText", ad.mText);
            return new RenderOutput.Builder()
                    .setTemplateId(ad.mTemplateId)
                    .setTemplateParams(templateParams)
                    .build();
        } else {
            String content =
                    "<img src=\"" + impressionUrl + "\">\n"
                            + "<a href=\"" + clickUrl + "\">" + ad.mText + "</a>";
            Log.d(TAG, "content: " + content);
            return new RenderOutput.Builder().setContent(content).build();
        }
    }

    private void handleOnRender(
            @NonNull RenderInput input,
            @NonNull Consumer<RenderOutput> consumer
    ) {
        try {
            Log.d(TAG, "handleOnRender() started.");
            String id = input.getRenderingConfig().getKeys().get(0);
            var adFuture = readAd(id, mRemoteData);
            var impUrlFuture = getImpressionTrackingUrl();
            var clickUrlFuture = FluentFuture.from(adFuture).transformAsync(
                    ad -> getClickTrackingUrl(ad.mLandingPage),
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

    public void handleOnWebViewEvent(
            @NonNull EventInput input,
            @NonNull Consumer<EventOutput> consumer) {
        try {
            Log.d(TAG, "handleOnEvent() started.");
            PersistableBundle eventParams = input.getParameters();
            int eventType = eventParams.getInt(EVENT_TYPE_KEY);
            if (eventType <= 0) {
                consumer.accept(new EventOutput.Builder().build());
                return;
            }
            ContentValues logData = null;
            if (eventType == EVENT_TYPE_CLICK) {
                double bidPrice = 0.0;
                if (input.getRequestLogRecord() != null
                        && input.getRequestLogRecord().getRows() != null
                        && !input.getRequestLogRecord().getRows().isEmpty()) {
                    ContentValues row = input.getRequestLogRecord().getRows().get(0);
                    Double data = row.getAsDouble(BID_PRICE_KEY);
                    if (data != null) {
                        bidPrice = data.doubleValue();
                    }
                }
                double updatedPrice = bidPrice * COST_RAISING_FACTOR;
                logData = new ContentValues();
                logData.put(CLICK_COST_KEY, updatedPrice);
            }
            EventOutput result = new EventOutput.Builder()
                    .setEventLogRecord(
                        new EventLogRecord.Builder()
                            .setRowIndex(0)
                            .setType(eventType)
                            .setData(logData).build())
                    .build();
            consumer.accept(result);
        } catch (Exception e) {
            Log.e(TAG, "handleOnEvent failed.", e);
            consumer.accept(null);
        }
    }

    boolean isInstalledAppFound(CuckooFilter<String> filter) {
        if (mUserData == null) {
            Log.i(TAG, "No userdata.");
            return false;
        }

        if (mUserData.getAppInfos() == null || mUserData.getAppInfos().isEmpty()) {
            Log.i(TAG, "No installed apps.");
            return false;
        }

        if (filter == null) {
            return false;
        }

        for (String app: mUserData.getAppInfos().keySet()) {
            AppInfo value = mUserData.getAppInfos().get(app);
            if (value != null && value.isInstalled() && filter.contains(app)) {
                return true;
            }
        }

        return false;
    }

    boolean isInstalledAppFound(List<String> apps) {
        if (mUserData == null) {
            Log.i(TAG, "No userdata.");
            return false;
        }

        if (mUserData.getAppInfos() == null || mUserData.getAppInfos().isEmpty()) {
            Log.i(TAG, "No installed apps.");
            return false;
        }

        if (apps == null || apps.isEmpty()) {
            return false;
        }

        for (String app: mUserData.getAppInfos().keySet()) {
            if (apps.contains(app)) {
                return true;
            }
        }

        return false;
    }

    boolean isBlockedAd(Ad ad) {
        return isInstalledAppFound(ad.mExcludeFilter) || isInstalledAppFound(ad.mExcludes);
    }

    private List<String> getFilteredKeys(Map<String, byte[]> data) {
        Log.d(TAG, "getFilteredKeys() called.");
        List<String> filteredKeys = new ArrayList<String>();
        // Add all keys from the file into the list
        for (String key : data.keySet()) {
            if (key != null && data.get(key) != null) {
                if (key.startsWith("ad")) {
                    Ad ad = parseAd(key, data.get(key));
                    if (ad != null && !isBlockedAd(ad)) {
                        filteredKeys.add(key);
                    }
                } else if (key.startsWith("template")) {
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
        final List<String> mTargetKeywords;
        final List<String> mTargetApps;
        final List<String> mExcludes;
        final String mLandingPage;
        final String mText;
        final String mTemplateId;
        final CuckooFilter<String> mTargetKeywordFilter;
        final CuckooFilter<String> mTargetAppFilter;
        final CuckooFilter<String> mExcludeFilter;
        Ad(String id, double price, List<String> targetKeywords, List<String> targetApps,
                List<String> excludes, String landingPage, String text, String templateId,
                CuckooFilter<String> targetKeywordFilter,
                CuckooFilter<String> targetAppFilter,
                CuckooFilter<String> excludeFilter) {
            mId = id;
            mPrice = price;
            mTargetKeywords = targetKeywords;
            mTargetApps = targetApps;
            mExcludes = excludes;
            mLandingPage = landingPage;
            mText = text;
            mTemplateId = templateId;
            mTargetKeywordFilter = targetKeywordFilter;
            mTargetAppFilter = targetAppFilter;
            mExcludeFilter = excludeFilter;
        }
    }

    private static void readJsonArray(JsonReader reader, List<String> values) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            String value = reader.nextString();
            if (value != null && !value.isEmpty()) {
                values.add(value);
            }
        }
        reader.endArray();
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
            ArrayList<String> targetKeywords = new ArrayList<>();
            ArrayList<String> targetApps = new ArrayList<>();
            ArrayList<String> excludes = new ArrayList<>();
            String landingPage = "";
            String text = "Click Here!";
            String templateId = null;
            CuckooFilter<String> targetKeywordFilter = null;
            CuckooFilter<String> targetAppFilter = null;
            CuckooFilter<String> excludeFilter = null;
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("price")) {
                    price = reader.nextDouble();
                } else if (name.equals("keywords")) {
                    readJsonArray(reader, targetKeywords);
                } else if (name.equals("apps")) {
                    readJsonArray(reader, targetApps);
                } else if (name.equals("excludes")) {
                    readJsonArray(reader, excludes);
                } else if (name.equals("landingPage")) {
                    landingPage = reader.nextString();
                } else if (name.equals("text")) {
                    text = reader.nextString();
                } else if (name.equals("template")) {
                    templateId = reader.nextString();
                } else if (name.equals("keywordFilter")) {
                    targetKeywordFilter = CuckooFilterUtil.createCuckooFilter(reader.nextString());
                } else if (name.equals("appFilter")) {
                    targetAppFilter = CuckooFilterUtil.createCuckooFilter(reader.nextString());
                } else if (name.equals("excludeFilter")) {
                    excludeFilter = CuckooFilterUtil.createCuckooFilter(reader.nextString());
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return new Ad(id, price, targetKeywords, targetApps, excludes, landingPage, text,
                    templateId, targetKeywordFilter, targetAppFilter, excludeFilter);
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
