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

package com.android.ondevicepersonalization.services.data.user;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.odp.module.common.Clock;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

public class UserDataDaoTest {
    private static final String APP_NAME_1 = "com.app.abc";
    private static final String APP_NAME_2 = "com.app.def";
    private static final String APP_NAME_3 = "com.app.ghi";

    private UserDataDao mUserDataDao;
    private Context mContext;
    @Mock private Clock mClock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mUserDataDao = UserDataDao.getInstanceForTest(mContext, mClock);
        when(mClock.currentTimeMillis()).thenReturn(200L);
    }

    @After
    public void cleanUp() throws Exception {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void readEmptyAppInstallTable_success() {
        Map<String, Long> result = mUserDataDao.getAppInstallMap(false);
        assertThat(result).isEmpty();

        result = mUserDataDao.getAppInstallMap(true);
        assertThat(result).isEmpty();
    }

    @Test
    public void insertAndReadAppInstall_success() {
        Map<String, Long> appMap = Map.of(APP_NAME_1, 100L, APP_NAME_2, 100L);
        mUserDataDao.insertAppInstall(appMap, 0);

        Map<String, Long> result = mUserDataDao.getAppInstallMap(false);
        assertThat(result).containsExactlyEntriesIn(appMap);

        result = mUserDataDao.getAppInstallMap(true);
        assertThat(result).isEmpty();
    }

    @Test
    public void insertAndReadAppInstallWithNoise_success() {
        Map<String, Long> appMap = Map.of(APP_NAME_1, 100L, APP_NAME_2, 100L);
        mUserDataDao.insertAppInstall(appMap, 10);

        Map<String, Long> result = mUserDataDao.getAppInstallMap(true);
        assertThat(result).containsExactlyEntriesIn(appMap);

        result = mUserDataDao.getAppInstallMap(false);
        assertThat(result).isEmpty();
    }

    @Test
    public void insertAndReadMultipleAppInstallWithNoise_success() {
        Map<String, Long> appMap = Map.of(APP_NAME_1, 100L, APP_NAME_2, 100L);
        mUserDataDao.insertAppInstall(appMap, 10);

        Map<String, Long> appMap2 = Map.of(APP_NAME_1, 100L, APP_NAME_3, 100L);
        mUserDataDao.insertAppInstall(appMap2, 15);

        List<Map<String, Long>> result = mUserDataDao.readAppInstall(null, null);
        assertThat(result).containsExactly(appMap, appMap2);

        Map<String, Long> appResult = mUserDataDao.getAppInstallMap(false);
        assertThat(appResult).isEmpty();
    }

    @Test
    public void deleteEmptyAppInstallTable_success() {
        boolean success = mUserDataDao.deleteAllAppInstallTable();

        assertThat(success).isTrue();
    }

    @Test
    public void deleteAllAppInstallTable_success() {
        boolean success =
                mUserDataDao.insertAppInstall(Map.of(APP_NAME_1, 100L, APP_NAME_2, 100L), 0);
        assertThat(success).isTrue();
        success = mUserDataDao.insertAppInstall(Map.of(APP_NAME_1, 100L, APP_NAME_3, 100L), 10);
        assertThat(success).isTrue();

        List<Map<String, Long>> appList = mUserDataDao.readAppInstall(null, null);
        assertThat(appList).hasSize(2);

        mUserDataDao.deleteAllAppInstallTable();
        appList = mUserDataDao.readAppInstall(null, null);
        assertThat(appList).isEmpty();
    }
}
