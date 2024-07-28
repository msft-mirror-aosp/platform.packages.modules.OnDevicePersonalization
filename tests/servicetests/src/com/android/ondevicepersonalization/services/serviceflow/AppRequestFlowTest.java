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

package com.android.ondevicepersonalization.services.serviceflow;


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.ondevicepersonalization.CalleeMetadata;
import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.request.AppRequestFlow;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import com.test.TestPersonalizationHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class AppRequestFlowTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = AppRequestFlowTest.class.getSimpleName();

    private static final String TEST_SERVICE_CLASS = "com.test.TestPersonalizationService";
    private static final int TEST_TIMEOUT_SECONDS = 10;
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final ComponentName mTestServiceComponentName =
            new ComponentName(mContext.getPackageName(), TEST_SERVICE_CLASS);
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final OnDevicePersonalizationDbHelper mDbHelper =
            OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);

    private volatile boolean mCallbackSuccess;
    private volatile boolean mCallbackError;
    private volatile int mCallbackErrorCode;
    private int mIsolatedServiceErrorCode;
    private byte[] mSerializedException;
    private volatile Bundle mExecuteCallback;
    private ServiceFlowOrchestrator mSfo;

    @Mock
    UserPrivacyStatus mUserPrivacyStatus;

    @Spy
    private Flags mSpyFlags = spy(FlagsFactory.getFlags());

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .spyStatic(UserPrivacyStatus.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setup() throws Exception {
        when(mSpyFlags.getGlobalKillSwitch()).thenReturn(false);
        // default for service deadline is 30 seconds
        when(mSpyFlags.getIsolatedServiceDeadlineSeconds()).thenReturn(30);
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");

        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);
        doReturn(true).when(mUserPrivacyStatus).isMeasurementEnabled();
        doReturn(true).when(mUserPrivacyStatus).isProtectedAudienceEnabled();

        setUpTestData();

        mSfo = new ServiceFlowOrchestrator();
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testAppRequestFlow_InvalidService_ErrorManifestMisconfigured()
            throws InterruptedException {
        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                new ComponentName(mContext.getPackageName(), "com.test.BadService"),
                createWrappedAppParams(),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());

        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_MANIFEST_MISCONFIGURED, mCallbackErrorCode);
    }

    @Test
    public void testAppRequestFlow_InvalidPackage_ErrorParsingFailed() throws InterruptedException {
        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                new ComponentName("badPackageName", TEST_SERVICE_CLASS),
                createWrappedAppParams(),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());

        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_MANIFEST_PARSING_FAILED, mCallbackErrorCode);
    }

    @Test
    public void testAppRequestFlow_MeasurementControlRevoked() throws InterruptedException {
        int originalQueriesCount = getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME);
        int originalEventsCount = getDbTableSize(EventsContract.EventsEntry.TABLE_NAME);
        doReturn(false).when(mUserPrivacyStatus).isMeasurementEnabled();

        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                mTestServiceComponentName,
                createWrappedAppParams(),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertFalse(mExecuteCallback.isEmpty());
        // make sure no request or event records are written to the database
        assertEquals(originalQueriesCount, getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME));
        assertEquals(originalEventsCount, getDbTableSize(EventsContract.EventsEntry.TABLE_NAME));
    }

    @Test
    public void testAppRequestFlow_TargetingControlRevoked() throws InterruptedException {
        doReturn(false).when(mUserPrivacyStatus).isProtectedAudienceEnabled();

        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                mTestServiceComponentName,
                createWrappedAppParams(),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertTrue(mExecuteCallback.isEmpty());
    }

    @Test
    public void testAppRequestFlow_OutputDataBlocked() throws InterruptedException {
        when(mSpyFlags.getOutputDataAllowList()).thenReturn("");

        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                mTestServiceComponentName,
                createWrappedAppParams(),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertNull(mExecuteCallback.getByteArray(Constants.EXTRA_OUTPUT_DATA));
        assertEquals(2, getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME));
        assertEquals(1, getDbTableSize(EventsContract.EventsEntry.TABLE_NAME));
    }

    @Test
    public void testAppRequestFlow_OutputDataAllowed() throws InterruptedException {
        String contextPackageName = mContext.getPackageName();
        when(mSpyFlags.getOutputDataAllowList()).thenReturn(
                contextPackageName + ";" + contextPackageName);

        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                mTestServiceComponentName,
                createWrappedAppParams(),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertArrayEquals(
                mExecuteCallback.getByteArray(Constants.EXTRA_OUTPUT_DATA),
                new byte[] {1, 2, 3});
        assertEquals(2, getDbTableSize(QueriesContract.QueriesEntry.TABLE_NAME));
        assertEquals(1, getDbTableSize(EventsContract.EventsEntry.TABLE_NAME));
    }

    @Test
    public void testAppRequestFlow_getServiceFlowFuture_timeoutExceptionReturned()
            throws InterruptedException {
        // When the request fails due to the test service timing out, the callback should fail
        // with the service timeout error code.
        String contextPackageName = mContext.getPackageName();
        when(mSpyFlags.getOutputDataAllowList())
                .thenReturn(contextPackageName + ";" + contextPackageName);
        when(mSpyFlags.getIsolatedServiceDeadlineSeconds()).thenReturn(TEST_TIMEOUT_SECONDS);

        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                mTestServiceComponentName,
                createWrappedAppParams(/* timeout= */ true),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());
        boolean countedDown = mLatch.await(3 * TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(countedDown);
        assertFalse(mCallbackSuccess);
        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_ISOLATED_SERVICE_TIMEOUT, mCallbackErrorCode);
    }

    @Test
    public void testAppRequestFlow_getServiceFlowFuture_outputValidationExceptionReturned()
            throws Exception {
        // When the request fails due to output validation check failing, the callback should fail
        // with the service failed error code. Clear vendor data to cause output
        // validation check to fail.
        String contextPackageName = mContext.getPackageName();
        when(mSpyFlags.getOutputDataAllowList())
                .thenReturn(contextPackageName + ";" + contextPackageName);
        clearVendorDataDao();

        mSfo.scheduleForTest(
                ServiceFlowType.APP_REQUEST_FLOW,
                mContext.getPackageName(),
                mTestServiceComponentName,
                createWrappedAppParams(),
                new TestExecuteCallback(),
                mContext,
                100L,
                110L,
                new AppTestInjector());
        boolean countedDown = mLatch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(countedDown);
        assertFalse(mCallbackSuccess);
        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_SERVICE_FAILED, mCallbackErrorCode);
    }

    private int getDbTableSize(String tableName) {
        return mDbHelper.getReadableDatabase().query(tableName, null,
                null, null, null, null, null).getCount();
    }

    private void setUpTestData() throws Exception {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        ComponentName service = new ComponentName(mContext.getPackageName(), TEST_SERVICE_CLASS);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                DbUtils.toTableValue(service), "AABBCCDD", rows);
        EventsDao.getInstanceForTest(mContext).insertQuery(
                new Query.Builder(
                        System.currentTimeMillis(),
                        mContext.getPackageName(),
                        service,
                        "AABBCCDD",
                        queryDataBytes)
                        .build());
        EventsDao.getInstanceForTest(mContext);

        OnDevicePersonalizationVendorDataDao testVendorDao =
                OnDevicePersonalizationVendorDataDao.getInstanceForTest(
                        mContext,
                        new ComponentName(mContext.getPackageName(), TEST_SERVICE_CLASS),
                        PackageUtils.getCertDigest(mContext, mContext.getPackageName()));
        VendorData vendorData = new VendorData.Builder().setData(new byte[5]).setKey(
                "bid1").build();
        testVendorDao.batchUpdateOrInsertVendorDataTransaction(
                List.of(vendorData),
                List.of("bid1"),
                0
        );
    }

    private void clearVendorDataDao() throws Exception {
        OnDevicePersonalizationVendorDataDao testVendorDao =
                OnDevicePersonalizationVendorDataDao.getInstanceForTest(
                        mContext,
                        new ComponentName(mContext.getPackageName(), TEST_SERVICE_CLASS),
                        PackageUtils.getCertDigest(mContext, mContext.getPackageName()));
        testVendorDao.deleteVendorData(
                mContext,
                new ComponentName(mContext.getPackageName(), TEST_SERVICE_CLASS),
                PackageUtils.getCertDigest(mContext, mContext.getPackageName()));
    }

    private static Bundle createWrappedAppParams() {
        return createWrappedAppParams(/* timeout= */ false);
    }

    /**
     * Creates Bundle with app params for the test, including optional boolean for mimicking timeout
     * in the {@code TestPersonalizationService}.
     */
    private static Bundle createWrappedAppParams(boolean timeout) {
        try {
            Bundle wrappedParams = new Bundle();
            PersistableBundle handlerBundle = PersistableBundle.EMPTY;
            if (timeout) {
                handlerBundle = new PersistableBundle();
                handlerBundle.putBoolean(TestPersonalizationHandler.TIMEOUT_KEY, timeout);
            }
            ByteArrayParceledSlice buffer =
                    new ByteArrayParceledSlice(PersistableBundleUtils.toByteArray(handlerBundle));
            wrappedParams.putParcelable(Constants.EXTRA_APP_PARAMS_SERIALIZED, buffer);
            return wrappedParams;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    class AppTestInjector extends AppRequestFlow.Injector {
        @Override
        public Flags getFlags() {
            return mSpyFlags;
        }

        @Override
        public boolean shouldValidateExecuteOutput() {
            return true;
        }
    }

    class TestExecuteCallback extends IExecuteCallback.Stub {
        @Override
        public void onSuccess(Bundle bundle, CalleeMetadata calleeMetadata) {
            sLogger.d(TAG + " : onSuccess callback.");
            mCallbackSuccess = true;
            mExecuteCallback = bundle;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode, int isolatedServiceErrorCode,
                byte[] serializedException,
                CalleeMetadata calleeMetadata) {
            sLogger.d(TAG + " : onError callback.");
            mCallbackError = true;
            mCallbackErrorCode = errorCode;
            mIsolatedServiceErrorCode = isolatedServiceErrorCode;
            mSerializedException = serializedException;
            mLatch.countDown();
        }
    }
}
