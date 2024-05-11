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

package com.android.ondevicepersonalization.services.data.user;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.NetworkCapabilities;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.noise.AppInstallNoiseHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
@MockStatic(FlagsFactory.class)
@MockStatic(MonotonicClock.class)
public class UserDataCollectorTest {
    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final String APP_NAME_1 = "com.app1";
    private static final String APP_NAME_2 = "com.app2";
    private static final String APP_NAME_3 = "com.app3";
    private Context mContext;
    private UserDataCollector mCollector;
    private RawUserData mUserData;

    @Mock private Clock mMockClock;
    @Mock private Flags mMockFlags;
    @Mock private AppInstallNoiseHandler mMockAppInstallNoiseHandler;
    private UserDataDao mUserDataDao;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mUserData = RawUserData.getInstance();
        when(MonotonicClock.getInstance()).thenReturn(mMockClock);
        when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
        mUserDataDao = UserDataDao.getInstanceForTest(mContext, mMockClock);
        mCollector =
                UserDataCollector.getInstanceForTest(
                        mContext, mUserDataDao, mMockAppInstallNoiseHandler);
        TimeZone pstTime = TimeZone.getTimeZone("GMT-08:00");
        TimeZone.setDefault(pstTime);
    }

    @After
    public void cleanUp() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
        mCollector.clearUserData(mUserData);
        mCollector.clearMetadata();
    }

    @Test
    public void testUpdateUserData() throws Exception {
        mCollector.updateUserData(mUserData);

        // Test initial collection.
        // TODO(b/261748573): Add manual tests for histogram updates
        assertNotEquals(0, mUserData.utcOffset);
        assertTrue(mUserData.availableStorageBytes >= 0);
        assertTrue(mUserData.batteryPercentage >= 0);
        assertTrue(mUserData.batteryPercentage <= 100);
        assertNotNull(mUserData.networkCapabilities);

        assertTrue(UserDataCollector.ALLOWED_NETWORK_TYPE.contains(mUserData.dataNetworkType));

        mCollector.updateUserData(mUserData);
        assertTrue(mUserData.installedApps.size() > 0);
    }

    @Test
    public void updateInstalledAppsForUserData() {
        when(mMockFlags.getAppInstallHistoryTtlInMillis()).thenReturn(300L);
        when(mMockFlags.getTargetNoiseForUserFeature()).thenReturn(10f);
        when(mMockClock.currentTimeMillis()).thenReturn(200L);
        HashMap<String, Long> noisedAppMap = new HashMap<>();
        noisedAppMap.put(APP_NAME_1, 100L);
        noisedAppMap.put(APP_NAME_2, 100L);

        when(mMockAppInstallNoiseHandler.generateAppInstallWithNoise(any(), anyFloat()))
                .thenReturn(noisedAppMap);

        mCollector.updateInstalledApps(mUserData);

        Set<String> userDataInstallApp = mUserData.installedApps;
        assertTrue(userDataInstallApp.size() > 0);
        assertThat(mUserData.installedAppsWithNoise).isEqualTo(noisedAppMap.keySet());
        assertThat(mUserDataDao.getAppInstallMap(false).keySet()).isEqualTo(userDataInstallApp);
        assertThat(mUserDataDao.getAppInstallMap(true).keySet()).isEqualTo(noisedAppMap.keySet());
    }

    @Test
    public void testUpdateInstalledAppHistory() {
        when(mMockFlags.getAppInstallHistoryTtlInMillis()).thenReturn(300L);
        when(mMockClock.currentTimeMillis()).thenReturn(200L);

        List<ApplicationInfo> installedApps = createApplicationInfos(APP_NAME_1, APP_NAME_3);
        Map<String, Long> existingAppMap = Map.of(APP_NAME_1, 100L, APP_NAME_2, 100L);

        Map<String, Long> currentMap =
                mCollector.updateExistingAppInstall(installedApps, existingAppMap);
        assertThat(currentMap)
                .containsExactly(APP_NAME_1, 200L, APP_NAME_2, 100L, APP_NAME_3, 200L);

        when(mMockClock.currentTimeMillis()).thenReturn(450L);
        currentMap = mCollector.updateExistingAppInstall(installedApps, existingAppMap);
        // App2 is expired based on ttl.
        assertThat(currentMap).containsExactly(APP_NAME_1, 450L, APP_NAME_3, 450L);
    }

    private List<ApplicationInfo> createApplicationInfos(String... packageNames) {
        return Arrays.stream(packageNames)
                .map(s -> ApplicationInfoBuilder.newBuilder().setPackageName(s).build())
                .collect(Collectors.toList());
    }

    @Test
    public void testRealTimeUpdate() {
        // TODO (b/307176787): test orientation modification.
        mCollector.updateUserData(mUserData);
        TimeZone tzGmt4 = TimeZone.getTimeZone("GMT+04:00");
        TimeZone.setDefault(tzGmt4);
        mCollector.getRealTimeData(mUserData);
        assertEquals(mUserData.utcOffset, 240);
    }

    @Test
    public void testFilterNetworkCapabilities() {
        NetworkCapabilities cap = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .setLinkDownstreamBandwidthKbps(100)
                .setLinkUpstreamBandwidthKbps(10)
                .setSsid("myssid")
                .build();
        NetworkCapabilities filteredCap = UserDataCollector.getFilteredNetworkCapabilities(cap);
        assertEquals(100, filteredCap.getLinkDownstreamBandwidthKbps());
        assertEquals(10, filteredCap.getLinkUpstreamBandwidthKbps());
        assertNull(filteredCap.getSsid());
        assertArrayEquals(
                new int[]{NetworkCapabilities.NET_CAPABILITY_NOT_METERED},
                filteredCap.getCapabilities());
    }
}
