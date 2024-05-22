/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.noise;

import android.content.Context;
import android.content.res.AssetManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class AppInstallNoiseHandler {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AppInstallNoiseHandler.class.getSimpleName();
    public static final String BUNDLED_TOP_APP_PATH = "noise/top-10k.txt";
    private static Set<String> sTopAppList = new HashSet<>();
    private final AssetManager mAssetManager;
    private final Injector mInjector;

    @VisibleForTesting
    static class Injector {
        Clock getClock() {
            return MonotonicClock.getInstance();
        }
    }

    @VisibleForTesting
    AppInstallNoiseHandler(Context context, Injector injector) {
        mAssetManager = context.getAssets();
        mInjector = injector;
        initial();
    }

    public AppInstallNoiseHandler(Context context) {
        this(context, new Injector());
    }

    private void initial() {
        String appName;
        try {
            InputStream inputStream = mAssetManager.open(BUNDLED_TOP_APP_PATH);
            try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
                BufferedReader reader = new BufferedReader(inputStreamReader);
                while ((appName = reader.readLine()) != null) {
                    sTopAppList.add(appName);
                }
            }
        } catch (IOException e) {
            sLogger.e(e, TAG + ": Failed to open top apps list");
        }
    }

    /** Generates the app installed list after adding noise using randomized response. */
    public HashMap<String, Long> generateAppInstallWithNoise(
            Map<String, Long> appMap, float noise) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double noiseProbability = 1.0f / (Math.exp(noise) + 1);
        HashMap<String, Long> noisedAppList =
                addNoiseToInstalledApps(appMap, rand, noiseProbability);
        noisedAppList = addFakeAppInstall(appMap, noisedAppList, rand, noiseProbability);
        return noisedAppList;
    }

    @VisibleForTesting
    HashMap<String, Long> addNoiseToInstalledApps(
            Map<String, Long> appMap, ThreadLocalRandom rand, double noiseProbability) {
        HashMap<String, Long> noisedAppInstallList = new HashMap<>();
        long currentTime = mInjector.getClock().currentTimeMillis();

        for (String packageName : appMap.keySet()) {
            // Excludes apps that are not in the topN list.
            if (!sTopAppList.contains(packageName)) {
                continue;
            }

            // If smaller than noise probability, we will flip this installed app to uninstalled
            // app.
            double randomNumber = nextDouble(rand);
            if (randomNumber < noiseProbability) {
                sLogger.d(TAG + ": Flip app install signal by remove " + packageName);
                continue;
            }
            noisedAppInstallList.put(packageName, currentTime);
        }
        return noisedAppInstallList;
    }

    @VisibleForTesting
    HashMap<String, Long> addFakeAppInstall(
            Map<String, Long> appMap,
            HashMap<String, Long> noisedAppList,
            ThreadLocalRandom rand,
            double noiseProbability) {
        long updateTime = mInjector.getClock().currentTimeMillis();
        for (String appName : sTopAppList) {
            if (!appMap.containsKey(appName)) {
                double randomNumber = nextDouble(rand);
                if (randomNumber < noiseProbability) {
                    noisedAppList.put(appName, updateTime);
                    sLogger.d(TAG + ": Generate fake app info " + appName);
                }
            }
        }
        return noisedAppList;
    }

    /** Wrapper for calls to ThreadLocalRandom. Bound is {0, 1}. */
    @VisibleForTesting
    double nextDouble(ThreadLocalRandom rand) {
        return rand.nextDouble(1);
    }
}
