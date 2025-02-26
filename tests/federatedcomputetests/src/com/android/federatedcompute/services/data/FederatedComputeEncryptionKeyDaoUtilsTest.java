/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.federatedcompute.services.data;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.odp.module.common.data.OdpEncryptionKeyDao;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FederatedComputeEncryptionKeyDaoUtilsTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testGetInstance() {
        OdpEncryptionKeyDao instanceUnderTest =
                FederatedComputeEncryptionKeyDaoUtils.getInstance(sContext);
        OdpEncryptionKeyDao secondInstance =
                FederatedComputeEncryptionKeyDaoUtils.getInstance(sContext);

        assertThat(instanceUnderTest).isSameInstanceAs(secondInstance);
        assertNotNull(instanceUnderTest);
        assertThat(instanceUnderTest).isInstanceOf(OdpEncryptionKeyDao.class);
    }

    @Test
    public void testGetInstanceForTest() {
        OdpEncryptionKeyDao instanceUnderTest =
                FederatedComputeEncryptionKeyDaoUtils.getInstanceForTest(sContext);
        OdpEncryptionKeyDao secondInstance =
                FederatedComputeEncryptionKeyDaoUtils.getInstanceForTest(sContext);

        assertThat(instanceUnderTest).isSameInstanceAs(secondInstance);
        assertNotNull(instanceUnderTest);
        assertThat(instanceUnderTest).isInstanceOf(OdpEncryptionKeyDao.class);
    }

    @After
    public void cleanUp() throws Exception {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(sContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
