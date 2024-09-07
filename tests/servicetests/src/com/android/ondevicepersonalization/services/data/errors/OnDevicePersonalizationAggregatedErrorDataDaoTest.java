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

package com.android.ondevicepersonalization.services.data.errors;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.odp.module.common.PackageUtils;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationAggregatedErrorDataDaoTest {
    private static final ComponentName TEST_OWNER = new ComponentName("ownerPkg", "ownerCls");
    private static final ComponentName OTHER_OWNER = new ComponentName("otherPkg", "otherCls");
    private static final String TEST_CERT_DIGEST = "certDigest";
    private static final String TASK_IDENTIFIER = "task";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationAggregatedErrorDataDao mDao;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .spyStatic(PackageUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setup() {
        mDao =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, TEST_OWNER, TEST_CERT_DIGEST);

        // Cleanup any existing records
        mDao.deleteExceptionData();
    }

    @Test
    public void testAddExceptionCount_InvalidCode_Fails() {
        assertFalse(
                mDao.addExceptionCount(
                        OnDevicePersonalizationAggregatedErrorDataDao.MAX_ALLOWED_ERROR_CODE + 1,
                        1));
    }

    @Test
    public void testAddExceptionCount_Success() {
        assertTrue(mDao.addExceptionCount(1, 1));
        assertTrue(mDao.addExceptionCount(1, 1));
        assertThat(mDao.getExceptionData()).hasSize(1);
    }

    @Test
    public void testDeleteExceptionData_Success() {
        // Given two records are added to the Dao
        mDao.addExceptionCount(1, 1);
        mDao.addExceptionCount(2, 1);
        ImmutableList<ErrorData> originalData = mDao.getExceptionData();

        // Expect that calling delete clears the table
        assertTrue(mDao.deleteExceptionData());
        assertThat(mDao.getExceptionData()).isEmpty();
        assertThat(originalData).hasSize(2);
    }

    @Test
    public void testGetInstance() {
        ComponentName owner1 = new ComponentName("owner1", "cls1");
        OnDevicePersonalizationAggregatedErrorDataDao instance1Owner1 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, owner1, TEST_CERT_DIGEST);
        OnDevicePersonalizationAggregatedErrorDataDao instance2Owner1 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, owner1, TEST_CERT_DIGEST);
        ComponentName owner2 = new ComponentName("owner2", "cls2");
        OnDevicePersonalizationAggregatedErrorDataDao instance1Owner2 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, owner2, TEST_CERT_DIGEST);

        assertThat(instance1Owner1).isSameInstanceAs(instance2Owner1);
        assertNotEquals(instance1Owner1, instance1Owner2);
    }
}
