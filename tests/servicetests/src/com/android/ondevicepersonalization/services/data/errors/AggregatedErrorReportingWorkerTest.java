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
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorCodesLoggerTest.TEST_ISOLATED_SERVICE_ERROR_CODE;
import static com.android.ondevicepersonalization.services.data.errors.AggregatedErrorCodesLoggerTest.getExpectedErrorData;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.odp.module.common.PackageUtils;
import com.android.odp.module.common.data.ErrorReportingMetadataStore;
import com.android.odp.module.common.encryption.OdpEncryptionKey;
import com.android.odp.module.common.proto.ErrorReportingMetadata;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Timestamp;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(Parameterized.class)
@ExtendedMockitoRule.MockStatic(PackageUtils.class)
@ExtendedMockitoRule.MockStatic(AppManifestConfigHelper.class)
public class AggregatedErrorReportingWorkerTest {
    private static final String TEST_CERT_DIGEST = "test_cert_digest";
    private static final String TEST_PACKAGE = "test_package";
    private static final String TEST_CLASS = "test_class";
    private static final String TEST_SERVER_URL = "https://google.com";

    private static final String TEST_OVERRIDE_URL = "https://foo.com";

    private static final ComponentName TEST_COMPONENT_NAME =
            new ComponentName(TEST_PACKAGE, TEST_CLASS);

    private static final ImmutableList<ComponentName> TEST_ODP_SERVICE_LIST =
            ImmutableList.of(TEST_COMPONENT_NAME);

    private static final ListenableFuture<Boolean> SUCCESSFUL_FUTURE =
            Futures.immediateFuture(true);

    private static final long DEFAULT_REPORTING_INTERVAL_HOURS = 24;
    private static final ErrorReportingMetadata DEFAULT_UNINITIALIZED_METADATA =
            ErrorReportingMetadata.getDefaultInstance();

    private static final ImmutableList<ComponentName> EMPTY_ODP_SERVICE_LIST = ImmutableList.of();

    @Parameterized.Parameter(0)
    public boolean mUsedOverrideUrl = true;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private final OnDevicePersonalizationAggregatedErrorDataDao mErrorDataDao =
            OnDevicePersonalizationAggregatedErrorDataDao.getInstance(
                    mContext, TEST_COMPONENT_NAME, TEST_CERT_DIGEST);

    private TestInjector mTestInjector;

    private int mDayIndexUtc;

    private TestReportingProtocol mTestReportingProtocol;
    private AggregatedErrorReportingWorker mInstanceUnderTest;

    @Mock private OdpEncryptionKey mMockEncryptionKey;
    @Mock private ErrorReportingMetadataStore mMockMetadataStore;

    @Captor ArgumentCaptor<ErrorReportingMetadata> mMetadataArgumentCaptor;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Setup package utils to return the test cert digest
        doReturn(TEST_CERT_DIGEST).when(() -> PackageUtils.getCertDigest(mContext, TEST_PACKAGE));
        // By default, there is no metadata from previous run
        doReturn(Futures.immediateFuture(DEFAULT_UNINITIALIZED_METADATA))
                .when(mMockMetadataStore)
                .get();
        doReturn(
                        Futures.immediateFuture(
                                DEFAULT_UNINITIALIZED_METADATA.toBuilder()
                                        .setLastSuccessfulUpload(
                                                Timestamp.newBuilder()
                                                        .setSeconds(DateTimeUtils.epochSecondsUtc())
                                                        .build())))
                .when(mMockMetadataStore)
                .set(mMetadataArgumentCaptor.capture());
        mDayIndexUtc = DateTimeUtils.dayIndexUtc();

        // Inject a test ReportingProtocol object and a mock metadata store.
        mTestReportingProtocol = new TestReportingProtocol();
        mTestInjector =
                mUsedOverrideUrl
                        ? new TestInjector(
                                mTestReportingProtocol, mMockMetadataStore, TEST_OVERRIDE_URL)
                        : new TestInjector(mTestReportingProtocol, mMockMetadataStore);
        mInstanceUnderTest = AggregatedErrorReportingWorker.createWorker(mTestInjector);
    }

    @After
    public void cleanup() {
        AggregatedErrorReportingWorker.resetForTesting();
        mErrorDataDao.deleteExceptionData();
    }

    @Test
    public void reportAggregateErrors_noOdpServices() {
        // When no odp services installed, expect the report to early exit.
        doReturn(EMPTY_ODP_SERVICE_LIST)
                .when(() -> AppManifestConfigHelper.getOdpServices(mContext, true));

        ListenableFuture<Void> returnedFuture =
                mInstanceUnderTest.reportAggregateErrorsHelper(mContext, /* encryptionKey= */ null);

        assertTrue(returnedFuture.isDone());
        assertEquals(0, mTestInjector.mCallCount.get());
    }

    @Test
    public void reportAggregateErrors_noErrorData() {
        // When odp services are installed but no error data is present in the tables, expect
        // the report to early exit.
        doReturn(TEST_ODP_SERVICE_LIST)
                .when(() -> AppManifestConfigHelper.getOdpServices(mContext, true));

        ListenableFuture<Void> returnedFuture =
                mInstanceUnderTest.reportAggregateErrorsHelper(mContext, /* encryptionKey= */ null);

        assertTrue(returnedFuture.isDone());
        assertEquals(0, mTestInjector.mCallCount.get());
    }

    @Test
    public void reportAggregateErrors_withErrorData_succeeds() {
        // When odp services are installed and there is error data present in the tables,
        // expect there to be single interaction with the injector and the test reporting object.
        doReturn(TEST_ODP_SERVICE_LIST)
                .when(() -> AppManifestConfigHelper.getOdpServices(mContext, true));
        mErrorDataDao.addExceptionCount(TEST_ISOLATED_SERVICE_ERROR_CODE, 1);
        long startSecondsUtc = DateTimeUtils.epochSecondsUtc();

        ListenableFuture<Void> returnedFuture =
                mInstanceUnderTest.reportAggregateErrorsHelper(mContext, mMockEncryptionKey);

        assertTrue(returnedFuture.isDone());
        assertEquals(1, mTestInjector.mCallCount.get());
        assertEquals(
                mUsedOverrideUrl ? TEST_OVERRIDE_URL : TEST_SERVER_URL, mTestInjector.mRequestUri);
        assertEquals(getExpectedErrorData(mDayIndexUtc), mTestInjector.mErrorData.get(0));
        assertEquals(1, mTestReportingProtocol.mCallCount.get());
        assertThat(mTestReportingProtocol.mOdpEncryptionKey).isSameInstanceAs(mMockEncryptionKey);
        // Assert that the metadata store has been updated with a recent timestamp.
        assertThat(mMetadataArgumentCaptor.getAllValues()).hasSize(1);
        assertThat(mMetadataArgumentCaptor.getValue().getLastSuccessfulUpload().getSeconds())
                .isAtLeast(startSecondsUtc);
    }

    @Test
    public void reportAggregateErrors_withErrorData_beforeIntervalRequirement_noOp() {
        // When odp services are installed and there is error data present in the tables,
        // but the interval requirements are not met, expect there to be no interaction with the
        // test reporting object and a single interaction with the metadata store to get the
        // current last reported time stamp.
        doReturn(TEST_ODP_SERVICE_LIST)
                .when(() -> AppManifestConfigHelper.getOdpServices(mContext, true));
        mErrorDataDao.addExceptionCount(TEST_ISOLATED_SERVICE_ERROR_CODE, 1);
        Timestamp currentTimeStamp =
                Timestamp.newBuilder().setSeconds(DateTimeUtils.epochSecondsUtc()).build();
        ErrorReportingMetadata newMetadata =
                DEFAULT_UNINITIALIZED_METADATA.toBuilder()
                        .setLastSuccessfulUpload(currentTimeStamp)
                        .build();
        doReturn(Futures.immediateFuture(newMetadata)).when(mMockMetadataStore).get();

        ListenableFuture<Void> returnedFuture =
                mInstanceUnderTest.reportAggregateErrors(mContext, mMockEncryptionKey);

        assertTrue(returnedFuture.isDone());
        assertEquals(0, mTestInjector.mCallCount.get());
        assertEquals(0, mTestReportingProtocol.mCallCount.get());
        verify(mMockMetadataStore, times(0)).set(any());
        verify(mMockMetadataStore, times(1)).get();
    }

    @Test
    public void isReportingIntervalSatisfied_uninitialized_returnsTrue() throws Exception {
        doReturn(Futures.immediateFuture(DEFAULT_UNINITIALIZED_METADATA))
                .when(mMockMetadataStore)
                .get();

        ListenableFuture<Boolean> returnedFuture =
                mInstanceUnderTest.isReportingIntervalSatisfied(mContext);

        assertTrue(returnedFuture.isDone());
        assertThat(returnedFuture.get()).isTrue();
    }

    @Test
    public void reportAggregateErrors_withErrorData_reportingProtocolFails() {
        // When odp services are installed and there is error data present in the tables,
        // expect there to be single interaction with the injector and the test reporting object.
        doReturn(TEST_ODP_SERVICE_LIST)
                .when(() -> AppManifestConfigHelper.getOdpServices(mContext, true));
        mErrorDataDao.addExceptionCount(TEST_ISOLATED_SERVICE_ERROR_CODE, 1);
        mTestReportingProtocol.mReturnFuture =
                Futures.immediateFailedFuture(new TimeoutException("Http time out!"));

        ListenableFuture<Void> returnedFuture =
                mInstanceUnderTest.reportAggregateErrorsHelper(mContext, /* encryptionKey= */ null);

        assertTrue(returnedFuture.isDone());
        assertEquals(1, mTestInjector.mCallCount.get());
        assertEquals(
                mUsedOverrideUrl ? TEST_OVERRIDE_URL : TEST_SERVER_URL, mTestInjector.mRequestUri);
        assertEquals(getExpectedErrorData(mDayIndexUtc), mTestInjector.mErrorData.get(0));
        assertEquals(1, mTestReportingProtocol.mCallCount.get());
    }

    @Test
    public void reportAggregateErrors_pendingRequest() {
        // A second request when there is an existing request fails immediately.
        doReturn(TEST_ODP_SERVICE_LIST)
                .when(() -> AppManifestConfigHelper.getOdpServices(mContext, true));
        mErrorDataDao.addExceptionCount(TEST_ISOLATED_SERVICE_ERROR_CODE, 1);
        SettableFuture<Boolean> settableFuture = SettableFuture.create();
        mTestReportingProtocol.mReturnFuture = settableFuture;

        ListenableFuture<Void> firstRequest =
                mInstanceUnderTest.reportAggregateErrors(mContext, /* encryptionKey= */ null);
        ListenableFuture<Void> secondRequest =
                mInstanceUnderTest.reportAggregateErrors(mContext, /* encryptionKey= */ null);

        assertFalse(firstRequest.isDone());
        assertTrue(secondRequest.isDone());
        ExecutionException outException =
                assertThrows(ExecutionException.class, secondRequest::get);
        assertThat(outException.getCause()).isInstanceOf(IllegalStateException.class);
        assertEquals(1, mTestInjector.mCallCount.get());
        settableFuture.set(true);
        assertTrue(firstRequest.isDone());
    }

    private static final class TestReportingProtocol implements ReportingProtocol {
        private final AtomicInteger mCallCount = new AtomicInteger(0);
        // Default instance returns the successful future.
        private ListenableFuture<Boolean> mReturnFuture = SUCCESSFUL_FUTURE;
        private OdpEncryptionKey mOdpEncryptionKey = null;

        @Override
        public ListenableFuture<Boolean> reportExceptionData(OdpEncryptionKey encryptionKey) {
            mCallCount.incrementAndGet();
            mOdpEncryptionKey = encryptionKey;
            return mReturnFuture;
        }
    }

    private static final class TestInjector extends AggregatedErrorReportingWorker.Injector {
        // Immutable state provided by test setup
        private final ReportingProtocol mTestProtocol;
        private final ErrorReportingMetadataStore mStore;
        private final String mOverrideUrl;

        // Mutable state used by test for assertions
        private String mRequestUri;
        private ImmutableList<ErrorData> mErrorData;
        private final AtomicInteger mCallCount = new AtomicInteger(0);

        TestInjector(
                ReportingProtocol testProtocol,
                ErrorReportingMetadataStore errorReportingMetadataStore) {
            this(testProtocol, errorReportingMetadataStore, /* overrideUrl= */ "");
        }

        TestInjector(
                ReportingProtocol testProtocol,
                ErrorReportingMetadataStore errorReportingMetadataStore,
                String overrideUrl) {
            this.mTestProtocol = testProtocol;
            this.mStore = errorReportingMetadataStore;
            this.mOverrideUrl = overrideUrl;
        }

        @Override
        ListeningExecutorService getBackgroundExecutor() {
            // Use direct executor to keep all work sequential for the tests.
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ListeningExecutorService getLightweightExecutor() {
            // Use direct executor to keep all work sequential for the tests.
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ReportingProtocol getAggregatedErrorReportingProtocol(
                ImmutableList<ErrorData> errorData, String requestBaseUri, Context context) {
            mCallCount.incrementAndGet();
            mErrorData = errorData;
            mRequestUri = requestBaseUri;
            return mTestProtocol;
        }

        @Override
        String getServerUrl(Context context, String packageName) {
            return TEST_SERVER_URL;
        }

        @Override
        String getErrorReportingServerOverrideUrl() {
            return mOverrideUrl;
        }

        @Override
        long getErrorReportingIntervalHours() {
            return DEFAULT_REPORTING_INTERVAL_HOURS;
        }

        @Override
        ErrorReportingMetadataStore getMetadataStore(Context context) {
            return mStore;
        }
    }
}
