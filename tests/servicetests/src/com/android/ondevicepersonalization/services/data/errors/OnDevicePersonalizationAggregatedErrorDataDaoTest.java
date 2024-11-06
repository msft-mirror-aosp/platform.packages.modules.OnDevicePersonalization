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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
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
    private static final String TEST_PACKAGE = "ownerPkg";
    private static final String OTHER_PACKAGE = "otherPkg";
    private static final ComponentName TEST_OWNER = new ComponentName(TEST_PACKAGE, "ownerCls");
    private static final ComponentName OTHER_OWNER = new ComponentName(OTHER_PACKAGE, "otherCls");
    private static final String TEST_CERT_DIGEST = "certDigest1";
    private static final String OTHER_CERT_DIGEST = "certDigest2";
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
        ExtendedMockito.doReturn(TEST_CERT_DIGEST)
                .when(() -> PackageUtils.getCertDigest(any(), eq(TEST_PACKAGE)));
        ExtendedMockito.doReturn(OTHER_CERT_DIGEST)
                .when(() -> PackageUtils.getCertDigest(any(), eq(OTHER_PACKAGE)));
        OnDevicePersonalizationAggregatedErrorDataDao.cleanupErrorData(
                mContext, /* excludedServices= */ ImmutableList.of());
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
        OnDevicePersonalizationAggregatedErrorDataDao owner1Instance1 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, TEST_OWNER, TEST_CERT_DIGEST);
        OnDevicePersonalizationAggregatedErrorDataDao owner1Instance2 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, TEST_OWNER, TEST_CERT_DIGEST);
        OnDevicePersonalizationAggregatedErrorDataDao owner2Instance1 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, OTHER_OWNER, TEST_CERT_DIGEST);

        assertNotNull(owner1Instance1);
        assertNotNull(owner2Instance1);
        assertThat(owner1Instance1).isSameInstanceAs(owner1Instance2);
        assertNotEquals(owner1Instance1, owner2Instance1);
    }

    @Test
    public void testGetMatchingTables() {
        // Given two tables with some error data
        OnDevicePersonalizationAggregatedErrorDataDao instance1 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, TEST_OWNER, TEST_CERT_DIGEST);
        OnDevicePersonalizationAggregatedErrorDataDao instance2 =
                OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                        mContext, OTHER_OWNER, TEST_CERT_DIGEST);
        instance1.addExceptionCount(1, 1);
        instance2.addExceptionCount(2, 1);
        int originalCount =
                OnDevicePersonalizationAggregatedErrorDataDao.getErrorDataTableNames(mContext)
                        .size();

        // Expect that no tables exist after cleanup
        OnDevicePersonalizationAggregatedErrorDataDao.cleanupErrorData(
                mContext, /* excludedServices= */ ImmutableList.of());

        assertEquals(2, originalCount);
        assertThat(OnDevicePersonalizationAggregatedErrorDataDao.getErrorDataTableNames(mContext))
                .isEmpty();
    }
}
