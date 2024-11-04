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

package com.android.ondevicepersonalization.services.data;

import static android.adservices.ondevicepersonalization.Constants.API_NAME_LOCAL_DATA_GET;
import static android.adservices.ondevicepersonalization.Constants.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.ModelId;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.OdpParceledListSlice;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.vendor.LocalData;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationLocalDataDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.IoUtils;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class DataAccessServiceImplTest {
    private static final double DELTA = 0.001;
    private static final byte[] RESPONSE_BYTES = {'A', 'B'};
    private static final int EVENT_TYPE_B2D = 1;
    private static final String SERVICE_CLASS = "TestClass";
    private static final String APP_NAME = "com.app";
    private static final String CERT_DIGEST = "AABBCCDD";
    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();
    private long mTimeMillis = 1000;
    private EventUrlPayload mEventUrlPayload;
    private TestInjector mInjector = new TestInjector();
    private CountDownLatch mLatch = new CountDownLatch(1);
    private Bundle mResult;
    private int mErrorCode = 0;
    private boolean mOnSuccessCalled = false;
    private boolean mOnErrorCalled = false;
    private OnDevicePersonalizationLocalDataDao mLocalDao;
    private OnDevicePersonalizationVendorDataDao mVendorDao;
    private EventsDao mEventsDao;
    private DataAccessServiceImpl mServiceImpl;
    private IDataAccessService mServiceProxy;
    private ComponentName mService;

    @Mock private OdpStatsdLogger mMockOdpStatsdLogger;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mService = ComponentName.createRelative(mApplicationContext.getPackageName(),
                "serviceClass");
        mInjector = new TestInjector();
        mVendorDao = mInjector.getVendorDataDao(mApplicationContext,
                mService,
                PackageUtils.getCertDigest(mApplicationContext,
                        mApplicationContext.getPackageName()));

        mLocalDao = mInjector.getLocalDataDao(mApplicationContext,
                mService,
                PackageUtils.getCertDigest(mApplicationContext,
                        mApplicationContext.getPackageName()));

        mEventsDao = mInjector.getEventsDao(mApplicationContext);

        mServiceImpl = new DataAccessServiceImpl(
                mService, mApplicationContext, null,
                DataAccessPermission.READ_WRITE, DataAccessPermission.READ_WRITE, mInjector);

        mServiceProxy = IDataAccessService.Stub.asInterface(mServiceImpl);
    }

    @Test
    public void testStatusCodes() {
        Set<Integer> allDataAccessCodes = new HashSet<>();
        //add them all and check size, insuring that there are no duplicates
        allDataAccessCodes.add(STATUS_SUCCESS);
        allDataAccessCodes.add(Constants.STATUS_KEY_NOT_FOUND);
        allDataAccessCodes.add(Constants.STATUS_SUCCESS_EMPTY_RESULT);
        allDataAccessCodes.add(Constants.STATUS_PERMISSION_DENIED);
        allDataAccessCodes.add(Constants.STATUS_LOCAL_DATA_READ_ONLY);
        allDataAccessCodes.add(Constants.STATUS_REQUEST_TIMESTAMPS_INVALID);
        allDataAccessCodes.add(Constants.STATUS_MODEL_TABLE_ID_INVALID);
        allDataAccessCodes.add(Constants.STATUS_MODEL_DB_LOOKUP_FAILED);
        allDataAccessCodes.add(Constants.STATUS_MODEL_LOOKUP_FAILURE);
        allDataAccessCodes.add(Constants.STATUS_DATA_ACCESS_UNSUPPORTED_OP);
        allDataAccessCodes.add(Constants.STATUS_DATA_ACCESS_FAILURE);
        allDataAccessCodes.add(Constants.STATUS_LOCAL_WRITE_DATA_ACCESS_FAILURE);

        assertThat(allDataAccessCodes).hasSize(12);
    }

    @Test
    public void testRemoteDataLookup() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_LOOKUP_KEYS, "key");
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        ByteArrayParceledSlice data = mResult.getParcelable(
                Constants.EXTRA_RESULT, ByteArrayParceledSlice.class);
        assertNotNull(data);
        assertNotNull(data.getByteArray());
    }

    @Test
    public void testRemoteDataLookupWithOverride() throws Exception {
        Map<String, byte[]> overrideData = new HashMap<>();
        overrideData.put("key1", "helloworld1".getBytes());
        overrideData.put("key2", "helloworld2".getBytes());
        mServiceImpl = new DataAccessServiceImpl(
                mService, mApplicationContext, overrideData,
                DataAccessPermission.READ_WRITE, DataAccessPermission.READ_WRITE, mInjector);
        mServiceProxy = IDataAccessService.Stub.asInterface(mServiceImpl);
        addTestData();
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_LOOKUP_KEYS, "key1");
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        ByteArrayParceledSlice data = mResult.getParcelable(
                Constants.EXTRA_RESULT, ByteArrayParceledSlice.class);
        assertNotNull(data);
        assertArrayEquals("helloworld1".getBytes(), data.getByteArray());
    }

    @Test
    public void testLocalDataLookup() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_LOOKUP_KEYS, "localkey");
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_LOOKUP,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        ByteArrayParceledSlice data = mResult.getParcelable(
                Constants.EXTRA_RESULT, ByteArrayParceledSlice.class);
        assertNotNull(data);
        assertNotNull(data.getByteArray());
    }

    @Test
    public void testRemoteDataKeysetWithOverride() throws Exception {
        Map<String, byte[]> overrideData = new HashMap<>();
        overrideData.put("key1", "helloworld1".getBytes());
        overrideData.put("key2", "helloworld2".getBytes());
        mServiceImpl = new DataAccessServiceImpl(
                mService, mApplicationContext, overrideData,
                DataAccessPermission.READ_WRITE, DataAccessPermission.READ_WRITE, mInjector);
        mServiceProxy = IDataAccessService.Stub.asInterface(mServiceImpl);
        addTestData();
        Bundle params = new Bundle();
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_REMOTE_DATA_KEYSET,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        HashSet<String> resultSet =
                mResult.getSerializable(Constants.EXTRA_RESULT, HashSet.class);
        assertNotNull(resultSet);
        assertEquals(2, resultSet.size());
        assertTrue(resultSet.contains("key1"));
        assertTrue(resultSet.contains("key2"));
    }

    @Test
    public void testRemoteDataKeyset() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_REMOTE_DATA_KEYSET,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        HashSet<String> resultSet =
                mResult.getSerializable(Constants.EXTRA_RESULT, HashSet.class);
        assertNotNull(resultSet);
        assertEquals(3, resultSet.size());
        assertTrue(resultSet.contains("key"));
        assertTrue(resultSet.contains("key2"));
        assertTrue(resultSet.contains("model"));
    }

    @Test
    public void testLocalDataKeyset() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_KEYSET,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        HashSet<String> resultSet =
                mResult.getSerializable(Constants.EXTRA_RESULT, HashSet.class);
        assertNotNull(resultSet);
        assertEquals(3, resultSet.size());
        assertTrue(resultSet.contains("localkey"));
        assertTrue(resultSet.contains("localkey2"));
        assertTrue(resultSet.contains("model"));
    }

    @Test
    public void testLocalDataPut() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_LOOKUP_KEYS, "localkey");
        byte[] arr = new byte[100];
        params.putParcelable(Constants.EXTRA_VALUE, new ByteArrayParceledSlice(arr));
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        ByteArrayParceledSlice data = mResult.getParcelable(
                Constants.EXTRA_RESULT, ByteArrayParceledSlice.class);
        assertNotNull(data);
        // Contains previous value
        assertNotNull(data.getByteArray());
        assertArrayEquals(mLocalDao.readSingleLocalDataRow("localkey"), arr);
    }

    @Test
    public void testLocalDataRemove() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_LOOKUP_KEYS, "localkey");
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        ByteArrayParceledSlice data = mResult.getParcelable(
                Constants.EXTRA_RESULT, ByteArrayParceledSlice.class);
        assertNotNull(data);
        // Contains previous value
        assertNotNull(data.getByteArray());
        assertNull(mLocalDao.readSingleLocalDataRow("localkey"));
    }

    @Test
    public void testGetEventUrl() throws Exception {
        PersistableBundle eventParams = createEventParams();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_EVENT_PARAMS, eventParams);
        params.putByteArray(Constants.EXTRA_RESPONSE_DATA, RESPONSE_BYTES);
        params.putString(Constants.EXTRA_MIME_TYPE, "image/gif");
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_EVENT_URL,
                params,
                new TestCallback());
        mLatch.await();
        assertNotEquals(null, mResult);
        String eventUrl = mResult.getParcelable(Constants.EXTRA_RESULT, Uri.class).toString();
        assertNotEquals(null, eventUrl);
        EventUrlPayload payload = EventUrlHelper.getEventFromOdpEventUrl(eventUrl);
        assertNotEquals(null, payload);
        PersistableBundle eventParamsFromUrl = payload.getEventParams();
        assertEquals(1, eventParamsFromUrl.getInt("a"));
        assertEquals("xyz", eventParamsFromUrl.getString("b"));
        assertEquals(5.0, eventParamsFromUrl.getDouble("c"), DELTA);
        assertEquals("image/gif", payload.getMimeType());
        assertArrayEquals(RESPONSE_BYTES, payload.getResponseData());
    }

    @Test
    public void testGetClickUrl() throws Exception {
        PersistableBundle eventParams = createEventParams();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_EVENT_PARAMS, eventParams);
        params.putString(Constants.EXTRA_DESTINATION_URL, "http://example.com");
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_EVENT_URL,
                params,
                new TestCallback());
        mLatch.await();
        assertNotEquals(null, mResult);
        String eventUrl = mResult.getParcelable(Constants.EXTRA_RESULT, Uri.class).toString();
        assertNotEquals(null, eventUrl);
        EventUrlPayload payload = EventUrlHelper.getEventFromOdpEventUrl(eventUrl);
        assertNotEquals(null, payload);
        PersistableBundle eventParamsFromUrl = payload.getEventParams();
        assertEquals(1, eventParamsFromUrl.getInt("a"));
        assertEquals("xyz", eventParamsFromUrl.getString("b"));
        assertEquals(5.0, eventParamsFromUrl.getDouble("c"), DELTA);
        Uri uri = Uri.parse(eventUrl);
        assertEquals(uri.getQueryParameter(EventUrlHelper.URL_LANDING_PAGE_EVENT_KEY),
                "http://example.com");
    }

    @Test
    public void testLocalDataNotIncludedErrorCode() throws Exception {
        mServiceImpl = new DataAccessServiceImpl(
                mService, mApplicationContext, null,
                /* localDataPermission */ DataAccessPermission.DENIED,
                /* eventDataPermission */ DataAccessPermission.READ_WRITE,
                mInjector);
        mServiceProxy = IDataAccessService.Stub.asInterface(mServiceImpl);
        Bundle params = new Bundle();
        params.putStringArray(Constants.EXTRA_LOOKUP_KEYS, new String[]{"localkey"});
        params.putByteArray(Constants.EXTRA_VALUE, new byte[100]);

        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_LOOKUP,
                params,
                new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_PERMISSION_DENIED);

        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_KEYSET,
                params,
                new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_PERMISSION_DENIED);

        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT,
                params,
                new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_PERMISSION_DENIED);

        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE,
                params,
                new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_PERMISSION_DENIED);
    }

    @Test
    public void testLocalDataReadOnlyErrorCode() throws Exception {
        mServiceImpl = new DataAccessServiceImpl(
                mService, mApplicationContext, null,
                /* localDataPermission */ DataAccessPermission.READ_ONLY,
                /* eventDataPermission */ DataAccessPermission.READ_WRITE,
                mInjector);
        mServiceProxy = IDataAccessService.Stub.asInterface(mServiceImpl);
        Bundle params = new Bundle();
        params.putStringArray(Constants.EXTRA_LOOKUP_KEYS, new String[]{"localkey"});
        params.putByteArray(Constants.EXTRA_VALUE, new byte[100]);
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_LOCAL_DATA_READ_ONLY);

        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_LOCAL_DATA_READ_ONLY);
    }

    @Test
    public void testGetRequests() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putLongArray(Constants.EXTRA_LOOKUP_KEYS, new long[]{0L, 200L});
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_REQUESTS,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        List<RequestLogRecord> data = mResult.getParcelable(
                Constants.EXTRA_RESULT, OdpParceledListSlice.class).getList();
        assertEquals(3, data.size());
        assertEquals(1, data.get(0).getRequestId());
        assertEquals(2, data.get(1).getRequestId());
        assertEquals(3, data.get(2).getRequestId());
    }

    @Test
    public void testGetRequestsBadInput() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putLongArray(Constants.EXTRA_LOOKUP_KEYS, new long[]{0L});
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_REQUESTS,
                params,
                new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_REQUEST_TIMESTAMPS_INVALID);
    }

    @Test
    public void testDataAccessBadOp() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        mServiceProxy.onRequest(
                -1,
                params,
                new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_DATA_ACCESS_UNSUPPORTED_OP);
    }

    @Test
    public void testGetJoinedEvents() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putLongArray(Constants.EXTRA_LOOKUP_KEYS, new long[]{0L, 200L});
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS,
                params,
                new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        List<EventLogRecord> data = mResult.getParcelable(
                Constants.EXTRA_RESULT, OdpParceledListSlice.class).getList();
        assertEquals(3, data.size());
        assertEquals(2L, data.get(0).getTimeMillis());
        assertEquals(1L, data.get(0).getRequestLogRecord().getTimeMillis());
        assertEquals(11L, data.get(1).getTimeMillis());
        assertEquals(10L, data.get(1).getRequestLogRecord().getTimeMillis());
        assertEquals(101L, data.get(2).getTimeMillis());
        assertEquals(100L, data.get(2).getRequestLogRecord().getTimeMillis());
    }

    @Test
    public void testGetJoinedEventsBadInput() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        params.putLongArray(Constants.EXTRA_LOOKUP_KEYS, new long[]{0L});
        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS,
                params,
                new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_REQUEST_TIMESTAMPS_INVALID);
    }

    @Test
    public void testEventDataNotIncludedErrorCode() throws Exception {
        mServiceImpl = new DataAccessServiceImpl(
                mService, mApplicationContext, null,
                /* localDataPermission */ DataAccessPermission.READ_WRITE,
                /* eventDataPermission */ DataAccessPermission.DENIED,
                mInjector);
        mServiceProxy = IDataAccessService.Stub.asInterface(mServiceImpl);
        Bundle params = new Bundle();
        params.putLongArray(Constants.EXTRA_LOOKUP_KEYS, new long[]{1L, 2L});

        mServiceProxy.onRequest(Constants.DATA_ACCESS_OP_GET_REQUESTS, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_PERMISSION_DENIED);

        mServiceProxy.onRequest(
                Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_PERMISSION_DENIED);
    }

    @Test
    public void testGetModelFileDescriptor_badInput() throws Exception {
        addTestData();
        Bundle params = new Bundle();

        mServiceProxy.onRequest(Constants.DATA_ACCESS_OP_GET_MODEL, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_KEY_NOT_FOUND);
    }

    @Test
    public void testGetModelFileDescriptor_remoteTable_success() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        ModelId modelId =
                new ModelId.Builder()
                        .setTableId(ModelId.TABLE_ID_REMOTE_DATA)
                        .setKey("model")
                        .build();
        params.putParcelable(Constants.EXTRA_MODEL_ID, modelId);

        mServiceProxy.onRequest(Constants.DATA_ACCESS_OP_GET_MODEL, params, new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        ParcelFileDescriptor modelFd =
                mResult.getParcelable(Constants.EXTRA_RESULT, ParcelFileDescriptor.class);
        assertNotNull(modelFd);
    }

    @Test
    public void testGetModelFileDescriptor_tableId_fail() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        ModelId modelId =
                new ModelId.Builder()
                        .setTableId(-1)
                        .setKey("bad-key2")
                        .build();
        params.putParcelable(Constants.EXTRA_MODEL_ID, modelId);

        mServiceProxy.onRequest(Constants.DATA_ACCESS_OP_GET_MODEL, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_MODEL_TABLE_ID_INVALID);
    }

    @Test
    public void testGetModelFileDescriptor_remoteTable_fail() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        ModelId modelId =
                new ModelId.Builder()
                        .setTableId(ModelId.TABLE_ID_REMOTE_DATA)
                        .setKey("bad-key2")
                        .build();
        params.putParcelable(Constants.EXTRA_MODEL_ID, modelId);

        mServiceProxy.onRequest(Constants.DATA_ACCESS_OP_GET_MODEL, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_MODEL_DB_LOOKUP_FAILED);
    }

    @Test
    public void testGetModelFileDescriptor_localTable_success() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        ModelId modelId =
                new ModelId.Builder()
                        .setTableId(ModelId.TABLE_ID_LOCAL_DATA)
                        .setKey("model")
                        .build();
        params.putParcelable(Constants.EXTRA_MODEL_ID, modelId);

        mServiceProxy.onRequest(Constants.DATA_ACCESS_OP_GET_MODEL, params, new TestCallback());
        mLatch.await();
        assertNotNull(mResult);
        ParcelFileDescriptor modelFd =
                mResult.getParcelable(Constants.EXTRA_RESULT, ParcelFileDescriptor.class);
        assertNotNull(modelFd);
    }

    @Test
    public void testGetModelFileDescriptor_localTable_fail() throws Exception {
        addTestData();
        Bundle params = new Bundle();
        ModelId modelId =
                new ModelId.Builder()
                        .setTableId(ModelId.TABLE_ID_LOCAL_DATA)
                        .setKey("bad-key2")
                        .build();
        params.putParcelable(Constants.EXTRA_MODEL_ID, modelId);

        mServiceProxy.onRequest(Constants.DATA_ACCESS_OP_GET_MODEL, params, new TestCallback());
        mLatch.await();
        assertTrue(mOnErrorCalled);
        assertThat(mErrorCode).isEqualTo(Constants.STATUS_MODEL_DB_LOOKUP_FAILED);
    }

    @Test
    public void testLogApiCallStats() {
        Map<String, byte[]> overrideData = new HashMap<>();
        overrideData.put("key1", "helloworld1".getBytes());
        overrideData.put("key2", "helloworld2".getBytes());
        mServiceImpl =
                new DataAccessServiceImpl(
                        mService,
                        mApplicationContext,
                        overrideData,
                        DataAccessPermission.READ_WRITE,
                        DataAccessPermission.READ_WRITE,
                        mInjector);
        mServiceImpl.logApiCallStats(API_NAME_LOCAL_DATA_GET, 10, STATUS_SUCCESS);

        ArgumentCaptor<ApiCallStats> captor = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mMockOdpStatsdLogger).logApiCallStats(captor.capture());
        ApiCallStats callStats = captor.getValue();
        assertThat(callStats.getSdkPackageName()).isEqualTo(mService.getPackageName());
        assertThat(callStats.getAppUid()).isEqualTo(0);
        assertThat(callStats.getApiName()).isEqualTo(API_NAME_LOCAL_DATA_GET);
    }

    private void addTestData() {
        List<VendorData> dataList = new ArrayList<>();
        dataList.add(new VendorData.Builder().setKey("key").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("key2").setData(new byte[10]).build());
        String modelPath = IoUtils.writeToTempFile("mode", new byte[] {12, 13});
        dataList.add(
                new VendorData.Builder()
                        .setKey("model")
                        .setData(modelPath.getBytes(StandardCharsets.UTF_8))
                        .build());

        List<String> retainedKeys = new ArrayList<>();
        retainedKeys.add("key");
        retainedKeys.add("key2");
        mVendorDao.batchUpdateOrInsertVendorDataTransaction(dataList, retainedKeys,
                System.currentTimeMillis());

        mLocalDao.updateOrInsertLocalData(
                new LocalData.Builder().setKey("localkey").setData(new byte[10]).build());
        mLocalDao.updateOrInsertLocalData(
                new LocalData.Builder().setKey("localkey2").setData(new byte[10]).build());
        mLocalDao.updateOrInsertLocalData(
                new LocalData.Builder()
                        .setKey("model")
                        .setData(modelPath.getBytes(StandardCharsets.UTF_8))
                        .build());

        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row = new ContentValues();
        row.put("a", 1);
        rows.add(row);
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                mApplicationContext.getPackageName(), "AABBCCDD", rows);

        Query query1 = new Query.Builder(1L, APP_NAME, mService, CERT_DIGEST, queryDataBytes)
                .build();
        long queryId1 = mEventsDao.insertQuery(query1);
        Query query2 = new Query.Builder(10L, APP_NAME, mService, CERT_DIGEST, queryDataBytes)
                .build();
        long queryId2 = mEventsDao.insertQuery(query2);
        Query query3 = new Query.Builder(100L, APP_NAME, mService, CERT_DIGEST, queryDataBytes)
                .build();
        long queryId3 = mEventsDao.insertQuery(query3);
        Query query4 = new Query.Builder(
                100L,
                APP_NAME,
                new ComponentName("packageA", "classA"),
                CERT_DIGEST,
                queryDataBytes)
                .build();
        mEventsDao.insertQuery(query4);

        ContentValues data = new ContentValues();
        data.put("a", 1);
        byte[] eventData = OnDevicePersonalizationFlatbufferUtils.createEventData(data);

        Event event1 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData(eventData)
                .setService(mService)
                .setQueryId(queryId1)
                .setTimeMillis(2L)
                .setRowIndex(0)
                .build();
        mEventsDao.insertEvent(event1);
        Event event2 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData(eventData)
                .setService(mService)
                .setQueryId(queryId2)
                .setTimeMillis(11L)
                .setRowIndex(0)
                .build();
        mEventsDao.insertEvent(event2);
        Event event3 = new Event.Builder()
                .setType(EVENT_TYPE_B2D)
                .setEventData(eventData)
                .setService(mService)
                .setQueryId(queryId3)
                .setTimeMillis(101L)
                .setRowIndex(0)
                .build();
        mEventsDao.insertEvent(event3);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mApplicationContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    private PersistableBundle createEventParams() {
        PersistableBundle params = new PersistableBundle();
        params.putInt("a", 1);
        params.putString("b", "xyz");
        params.putDouble("c", 5.0);
        return params;
    }

    class TestCallback extends IDataAccessServiceCallback.Stub {
        @Override
        public void onSuccess(Bundle result) {
            mResult = result;
            mOnSuccessCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mErrorCode = errorCode;
            mOnErrorCalled = true;
            mLatch.countDown();
        }
    }

    class TestInjector extends DataAccessServiceImpl.Injector {
        @Override
        long getTimeMillis() {
            return mTimeMillis;
        }

        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        OnDevicePersonalizationVendorDataDao getVendorDataDao(
                Context context, ComponentName service, String certDigest
        ) {
            return OnDevicePersonalizationVendorDataDao.getInstanceForTest(
                    context, service, certDigest);
        }

        @Override
        OnDevicePersonalizationLocalDataDao getLocalDataDao(
                Context context, ComponentName service, String certDigest
        ) {
            return OnDevicePersonalizationLocalDataDao.getInstanceForTest(
                    context, service, certDigest);
        }

        @Override
        EventsDao getEventsDao(
                Context context
        ) {
            return EventsDao.getInstanceForTest(context);
        }

        @Override
        OdpStatsdLogger getOdpStatsdLogger() {
            return mMockOdpStatsdLogger;
        }
    }
}
