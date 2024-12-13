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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.quality.Strictness.LENIENT;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoSession;

import java.util.List;

@RunWith(JUnit4.class)
public class AggregatedErrorCodesLoggerTest {

    private static final String TEST_CERT_DIGEST = "test_cert_digest";
    private static final String TEST_PACKAGE = "test_package";
    private static final String TEST_CLASS = "test_class";

    private static final int TEST_ISOLATED_SERVICE_ERROR_CODE = 2;

    private static final ComponentName TEST_COMPONENT_NAME =
            new ComponentName(TEST_PACKAGE, TEST_CLASS);
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private MockitoSession mSession;

    private int mDayIndexUtc;
    private final OnDevicePersonalizationAggregatedErrorDataDao mErrorDataDao =
            OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                    mContext, TEST_COMPONENT_NAME, TEST_CERT_DIGEST);

    @Before
    public void setUp() {
        mDayIndexUtc = DateTimeUtils.dayIndexUtc();

        mSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(FlagsFactory.class)
                        .mockStatic(PackageUtils.class)
                        .spyStatic(OnDevicePersonalizationExecutors.class)
                        .strictness(LENIENT)
                        .startMocking();

        ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService())
                .when(OnDevicePersonalizationExecutors::getBackgroundExecutor);
        doReturn(TEST_CERT_DIGEST).when(() -> PackageUtils.getCertDigest(any(), any()));
        mErrorDataDao.deleteExceptionData();
    }

    @Test
    public void logIsolatedServiceErrorCode_flagDisabled_skipsLogging() throws Exception {
        doReturn(new TestFlags(false)).when(FlagsFactory::getFlags);

        ListenableFuture<?> loggingFuture =
                AggregatedErrorCodesLogger.logIsolatedServiceErrorCode(
                        TEST_ISOLATED_SERVICE_ERROR_CODE, TEST_COMPONENT_NAME, mContext);

        assertTrue(loggingFuture.isDone());
        assertTrue(mErrorDataDao.getExceptionData().isEmpty());
    }

    @Test
    public void logIsolatedServiceErrorCode_flagEnabled_logsException() {
        doReturn(new TestFlags(true)).when(FlagsFactory::getFlags);

        ListenableFuture<?> loggingFuture =
                AggregatedErrorCodesLogger.logIsolatedServiceErrorCode(
                        TEST_ISOLATED_SERVICE_ERROR_CODE, TEST_COMPONENT_NAME, mContext);

        List<ErrorData> exceptionData = mErrorDataDao.getExceptionData();
        assertTrue(loggingFuture.isDone());
        assertEquals(1, exceptionData.size());
        assertEquals(getExpectedErrorData(mDayIndexUtc), exceptionData.get(0));
    }

    @Test
    public void cleanupAggregatedErrorData_flagDisabled_skipsCleanup() {
        doReturn(new TestFlags(false)).when(FlagsFactory::getFlags);
        mErrorDataDao.addExceptionCount(TEST_ISOLATED_SERVICE_ERROR_CODE, /* exceptionCount= */ 1);

        ListenableFuture<?> cleanupFuture =
                AggregatedErrorCodesLogger.cleanupAggregatedErrorData(mContext);

        List<ErrorData> exceptionData = mErrorDataDao.getExceptionData();
        assertTrue(cleanupFuture.isDone());
        assertEquals(1, exceptionData.size());
        assertEquals(getExpectedErrorData(mDayIndexUtc), exceptionData.get(0));
    }

    @Test
    public void cleanupAggregatedErrorData_flagEnabled_performsCleanup() {
        doReturn(new TestFlags(true)).when(FlagsFactory::getFlags);
        mErrorDataDao.addExceptionCount(TEST_ISOLATED_SERVICE_ERROR_CODE, /* exceptionCount= */ 1);

        ListenableFuture<?> cleanupFuture =
                AggregatedErrorCodesLogger.cleanupAggregatedErrorData(mContext);

        assertTrue(cleanupFuture.isDone());
        assertTrue(mErrorDataDao.getExceptionData().isEmpty());
        assertTrue(
                OnDevicePersonalizationAggregatedErrorDataDao.getErrorDataTableNames(mContext)
                        .isEmpty());
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    private static ErrorData getExpectedErrorData(int dayIndexUtc) {
        return new ErrorData.Builder(TEST_ISOLATED_SERVICE_ERROR_CODE, 1, dayIndexUtc, 0).build();
    }

    private static final class TestFlags implements Flags {
        private final boolean mAggregateErrorReportingEnabled;

        private TestFlags(boolean aggregateErrorReportingEnabled) {
            mAggregateErrorReportingEnabled = aggregateErrorReportingEnabled;
        }

        @Override
        public boolean getAggregatedErrorReportingEnabled() {
            return mAggregateErrorReportingEnabled;
        }
    }
}
