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

package com.android.ondevicepersonalization.services.request;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.process.ProcessRunner;
import com.android.ondevicepersonalization.services.process.ProcessRunnerImpl;
import com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(Parameterized.class)
public class AppRequestFlowTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private OnDevicePersonalizationDbHelper mDbHelper;

    private boolean mCallbackSuccess;
    private boolean mCallbackError;
    private int mCallbackErrorCode;
    private Bundle mCallbackResult;

    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        // Make sure we can access hidden APIs.
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);

        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                "com.example.test", "AABBCCDD", rows);
        EventsDao.getInstanceForTest(mContext).insertQuery(
                new Query.Builder().setServiceName(mContext.getPackageName()).setQueryData(
                        queryDataBytes).build());
        OnDevicePersonalizationVendorDataDao testVendorDao = OnDevicePersonalizationVendorDataDao
                .getInstanceForTest(mContext, mContext.getPackageName(),
                        PackageUtils.getCertDigest(mContext, mContext.getPackageName()));
        VendorData vendorData = new VendorData.Builder().setData(new byte[5]).setKey(
                "bid1").build();
        testVendorDao.batchUpdateOrInsertVendorDataTransaction(
                List.of(vendorData),
                List.of("bid1"),
                0
        );
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testRunAppRequestFlowOutputDataBlocked() throws Exception {
        AppRequestFlow appRequestFlow = new AppRequestFlow(
                "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                createWrappedAppParams(),
                new TestCallback(), mContext, 100L,
                new TestInjector() {
                    @Override public boolean isOutputDataAllowed(
                        String servicePkg, String appPkg, Context context) {
                            return false;
                        }

                    @Override ProcessRunner getProcessRunner() {
                        return mIsSipFeatureEnabled
                                ? SharedIsolatedProcessRunner.getInstance()
                                : ProcessRunnerImpl.getInstance();
                    }
                });
        appRequestFlow.run();
        mLatch.await();
        assertTrue(mCallbackSuccess);
        assertNull(mCallbackResult.getByteArray(Constants.EXTRA_OUTPUT_DATA));
        assertEquals(2,
                mDbHelper.getReadableDatabase().query(QueriesContract.QueriesEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testRunAppRequestFlowOutputDataAllowed() throws Exception {
        AppRequestFlow appRequestFlow = new AppRequestFlow(
                "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                createWrappedAppParams(),
                new TestCallback(), mContext, 100L,
                new TestInjector() {
                    @Override public boolean isOutputDataAllowed(
                        String servicePkg, String appPkg, Context context) {
                            return true;
                        }

                    @Override ProcessRunner getProcessRunner() {
                        return mIsSipFeatureEnabled
                                ? SharedIsolatedProcessRunner.getInstance()
                                : ProcessRunnerImpl.getInstance();
                    }
                });
        appRequestFlow.run();
        mLatch.await();
        assertTrue(mCallbackSuccess);
        assertArrayEquals(
                mCallbackResult.getByteArray(Constants.EXTRA_OUTPUT_DATA),
                new byte[] {1, 2, 3});
        assertEquals(2,
                mDbHelper.getReadableDatabase().query(QueriesContract.QueriesEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
        assertEquals(1,
                mDbHelper.getReadableDatabase().query(EventsContract.EventsEntry.TABLE_NAME, null,
                        null, null, null, null, null).getCount());
    }

    @Test
    public void testRunAppRequestFlowPersonalizationDisabled() throws Exception {
        AppRequestFlow appRequestFlow = new AppRequestFlow(
                "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                createWrappedAppParams(),
                new TestCallback(), mContext, 100L,
                new TestInjector() {
                    @Override
                    boolean isPersonalizationStatusEnabled() {
                        return false;
                    }

                    @Override
                    ProcessRunner getProcessRunner() {
                        return mIsSipFeatureEnabled
                                ? SharedIsolatedProcessRunner.getInstance()
                                : ProcessRunnerImpl.getInstance();
                    }
                });
        appRequestFlow.run();
        mLatch.await();
        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_PERSONALIZATION_DISABLED, mCallbackErrorCode);
    }

    @Test
    public void testRunAppRequestFlowInvalidRenderingConfigKeys() throws Exception {
        OnDevicePersonalizationVendorDataDao testVendorDao = OnDevicePersonalizationVendorDataDao
                .getInstanceForTest(mContext, mContext.getPackageName(),
                        PackageUtils.getCertDigest(mContext, mContext.getPackageName()));
        testVendorDao.batchUpdateOrInsertVendorDataTransaction(
                List.of(),
                List.of(),
                0
        );
        AppRequestFlow appRequestFlow = new AppRequestFlow(
                "abc",
                new ComponentName(mContext.getPackageName(), "com.test.TestPersonalizationService"),
                createWrappedAppParams(),
                new TestCallback(), mContext, 100L,
                new TestInjector() {
                    @Override
                    ProcessRunner getProcessRunner() {
                        return mIsSipFeatureEnabled
                                ? SharedIsolatedProcessRunner.getInstance()
                                : ProcessRunnerImpl.getInstance();
                    }
                });
        appRequestFlow.run();
        mLatch.await();
        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_SERVICE_FAILED, mCallbackErrorCode);
    }

    private Bundle createWrappedAppParams() throws Exception {
        Bundle wrappedParams = new Bundle();
        ByteArrayParceledSlice buffer = new ByteArrayParceledSlice(
                PersistableBundleUtils.toByteArray(PersistableBundle.EMPTY));
        wrappedParams.putParcelable(Constants.EXTRA_APP_PARAMS_SERIALIZED, buffer);
        return wrappedParams;
    }

    class TestCallback extends IExecuteCallback.Stub {
        @Override
        public void onSuccess(Bundle bundle) {
            mCallbackSuccess = true;
            mCallbackResult = bundle;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mCallbackError = true;
            mCallbackErrorCode = errorCode;
            mLatch.countDown();
        }
    }

    class TestInjector extends AppRequestFlow.Injector {
        @Override ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }
        @Override boolean isPersonalizationStatusEnabled() {
            return true;
        }

        @Override boolean shouldValidateExecuteOutput() {
            return true;
        }
    }
}

