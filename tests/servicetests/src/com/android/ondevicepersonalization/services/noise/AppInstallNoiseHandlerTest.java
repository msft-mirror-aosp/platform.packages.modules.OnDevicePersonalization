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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.odp.module.common.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class AppInstallNoiseHandlerTest {
    private static final long CURRENT_TIME = 200L;
    private AppInstallNoiseHandler mHandler;
    private Context mContext;
    @Mock private Clock mMockClock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mHandler = spy(new AppInstallNoiseHandler(mContext, new TestInjector()));
        when(mMockClock.currentTimeMillis()).thenReturn(CURRENT_TIME);
    }

    @Test
    public void testAddNoiseToInstallApps_appNotInTopList_remove() {
        doReturn(0.5).when(mHandler).nextDouble(any());
        Map<String, Long> installAppList = Map.of("com.not.toplist", CURRENT_TIME);

        Map<String, Long> noisedList =
                mHandler.addNoiseToInstalledApps(installAppList, ThreadLocalRandom.current(), 0.1);

        assertThat(noisedList).isEmpty();
    }

    @Test
    public void testAddNoiseToInstallApps_keepApp() {
        doReturn(0.5).when(mHandler).nextDouble(any());
        Map<String, Long> installAppList =
                Map.of("com.whatsapp", CURRENT_TIME, "com.android.chrome", CURRENT_TIME);

        Map<String, Long> noisedList =
                mHandler.addNoiseToInstalledApps(installAppList, ThreadLocalRandom.current(), 0.1);

        assertThat(noisedList).isEqualTo(installAppList);
    }

    @Test
    public void testAddNoiseToInstallApps_multipleAppFipOne() {
        doReturn(0.5, 0.05).when(mHandler).nextDouble(any());
        Map<String, Long> installAppList =
                Map.of("com.whatsapp", CURRENT_TIME, "com.android.chrome", CURRENT_TIME);

        Map<String, Long> noisedList =
                mHandler.addNoiseToInstalledApps(installAppList, ThreadLocalRandom.current(), 0.1);

        assertThat(noisedList).hasSize(1);
    }

    @Test
    public void testGenerateFakeAppInstall_none() {
        doReturn(0.5).when(mHandler).nextDouble(any());

        Map<String, Long> fakeList =
                mHandler.addFakeAppInstall(
                        Map.of(), new HashMap<>(), ThreadLocalRandom.current(), 0.1);

        assertThat(fakeList).isEmpty();
    }

    @Test
    public void testGenerateFakeAppInstall_addOne() {
        doReturn(0.05, 0.5).when(mHandler).nextDouble(any());

        Map<String, Long> fakeList =
                mHandler.addFakeAppInstall(
                        Map.of(), new HashMap<>(), ThreadLocalRandom.current(), 0.1);

        assertThat(fakeList).hasSize(1);
    }

    @Test
    public void testGenerateAppInstallWithNoise() {
        doReturn(0.5).when(mHandler).nextDouble(any());
        Map<String, Long> installAppList =
                Map.of("com.whatsapp", CURRENT_TIME, "com.android.chrome", CURRENT_TIME);

        HashMap<String, Long> noisedList = mHandler.generateAppInstallWithNoise(installAppList, 10);
        assertThat(noisedList).isEqualTo(installAppList);
    }

    public class TestInjector extends AppInstallNoiseHandler.Injector {
        Clock getClock() {
            return mMockClock;
        }
    }
}
