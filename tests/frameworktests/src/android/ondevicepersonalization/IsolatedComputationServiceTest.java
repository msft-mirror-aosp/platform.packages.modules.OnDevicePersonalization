/*
 * Copyright 2022 The Android Open Source Project
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

package android.ondevicepersonalization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.ondevicepersonalization.aidl.IIsolatedComputationService;
import android.ondevicepersonalization.aidl.IIsolatedComputationServiceCallback;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.util.ByteArrayParceledListSlice;
import com.android.ondevicepersonalization.internal.util.StringParceledListSlice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Unit Tests of IsolatedComputationService class.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class IsolatedComputationServiceTest {
    private static final String EVENT_TYPE_KEY = "event_type";
    private final TestService mTestService = new TestService();
    private IIsolatedComputationService mBinder;
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private boolean mSelectContentCalled;
    private boolean mOnDownloadCalled;
    private boolean mOnRenderCalled;
    private boolean mOnEventCalled;
    private Bundle mCallbackResult;
    private int mCallbackErrorCode;

    @Before
    public void setUp() {
        mTestService.onCreate();
        mBinder = IIsolatedComputationService.Stub.asInterface(mTestService.onBind(null));
    }

    @Test
    public void testServiceThrowsIfOpcodeInvalid() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mBinder.onRequest(
                            9999, new Bundle(),
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnExecute() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString("x", "y");
        ExecuteInput input =
                new ExecuteInput.Builder()
                .setAppPackageName("com.testapp")
                .setAppParams(appParams)
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mSelectContentCalled);
        ExecuteOutput result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, ExecuteOutput.class);
        assertEquals(
                5, result.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
        assertEquals("123", result.getRenderingDataList().get(0).getKeys().get(0));
    }

    @Test
    public void testOnExecutePropagatesError() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putInt("error", 1);  // Trigger an error in the service.
        ExecuteInput input =
                new ExecuteInput.Builder()
                .setAppPackageName("com.testapp")
                .setAppParams(appParams)
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mSelectContentCalled);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnExecuteWithoutAppParams() throws Exception {
        ExecuteInput input =
                new ExecuteInput.Builder()
                .setAppPackageName("com.testapp")
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mSelectContentCalled);
    }

    @Test
    public void testOnExecuteThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EXECUTE, null,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnExecuteThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EXECUTE, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnExecuteThrowsIfDataAccessServiceMissing() throws Exception {
        ExecuteInput input =
                new ExecuteInput.Builder()
                .setAppPackageName("com.testapp")
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EXECUTE, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnExecuteThrowsIfCallbackMissing() throws Exception {
        ExecuteInput input =
                new ExecuteInput.Builder()
                .setAppPackageName("com.testapp")
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EXECUTE, params, null);
                });
    }

    @Test
    public void testOnDownload() throws Exception {
        DownloadInputParcel input = new DownloadInputParcel.Builder()
                .setDownloadedKeys(StringParceledListSlice.emptyList())
                .setDownloadedValues(ByteArrayParceledListSlice.emptyList())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_DOWNLOAD, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnDownloadCalled);
        DownloadOutput result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, DownloadOutput.class);
        assertEquals("12", result.getKeysToRetain().get(0));
    }

    @Test
    public void testOnDownloadThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_DOWNLOAD, null,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnDownloadThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_DOWNLOAD, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnDownloadThrowsIfDataAccessServiceMissing() throws Exception {
        DownloadInputParcel input = new DownloadInputParcel.Builder()
                .setDownloadedKeys(StringParceledListSlice.emptyList())
                .setDownloadedValues(ByteArrayParceledListSlice.emptyList())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_DOWNLOAD, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnDownloadThrowsIfCallbackMissing() throws Exception {
        ParcelFileDescriptor[] pfds = ParcelFileDescriptor.createPipe();
        DownloadInputParcel input = new DownloadInputParcel.Builder()
                .setDownloadedKeys(StringParceledListSlice.emptyList())
                .setDownloadedValues(ByteArrayParceledListSlice.emptyList())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_DOWNLOAD, params, null);
                });
    }

    @Test
    public void testOnRender() throws Exception {
        RenderInput input =
                new RenderInput.Builder()
                .setRenderingData(
                    new RenderingData.Builder()
                        .addKeys("a")
                        .addKeys("b")
                        .build())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_RENDER, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnRenderCalled);
        RenderOutput result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, RenderOutput.class);
        assertEquals("htmlstring", result.getContent());
    }

    @Test
    public void testOnRenderPropagatesError() throws Exception {
        RenderInput input =
                new RenderInput.Builder()
                .setRenderingData(
                    new RenderingData.Builder()
                        .addKeys("z")  // Trigger error in service.
                        .build())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_RENDER, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnRenderCalled);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnRenderThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER, null,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnRenderThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnRenderThrowsIfDataAccessServiceMissing() throws Exception {
        RenderInput input =
                new RenderInput.Builder()
                .setRenderingData(
                    new RenderingData.Builder()
                        .addKeys("a")
                        .addKeys("b")
                        .build())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnRenderThrowsIfCallbackMissing() throws Exception {
        RenderInput input =
                new RenderInput.Builder()
                .setRenderingData(
                    new RenderingData.Builder()
                        .addKeys("a")
                        .addKeys("b")
                        .build())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER, params, null);
                });
    }

    @Test
    public void testOnEvent() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInput.Builder().setParameters(PersistableBundle.EMPTY).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_EVENT, params,
                new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnEventCalled);
        EventOutput result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, EventOutput.class);
        assertEquals(1, result.getEventLogRecord().getType());
        assertEquals(2, result.getEventLogRecord().getRowIndex());
    }

    @Test
    public void testOnEventPropagatesError() throws Exception {
        PersistableBundle eventParams = new PersistableBundle();
        // Input value 9999 will trigger an error in the mock service.
        eventParams.putInt(EVENT_TYPE_KEY, 9999);
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInput.Builder().setParameters(eventParams).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_EVENT, params,
                new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnEventCalled);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnEventThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EVENT, null,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnEventThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EVENT, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnEventThrowsIfDataAccessServiceMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInput.Builder().setParameters(PersistableBundle.EMPTY).build());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EVENT, params,
                            new TestServiceCallback());
                });
    }

    @Test
    public void testOnEventThrowsIfCallbackMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInput.Builder().setParameters(PersistableBundle.EMPTY).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_EVENT, params, null);
                });
    }

    class TestHandler implements IsolatedComputationCallback {
        @Override public void onExecute(
                ExecuteInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<ExecuteOutput> consumer
        ) {
            mSelectContentCalled = true;
            if (input.getAppParams() != null && input.getAppParams().getInt("error") > 0) {
                consumer.accept(null);
            } else {
                ContentValues row = new ContentValues();
                row.put("a", 5);
                consumer.accept(
                        new ExecuteOutput.Builder()
                        .setRequestLogRecord(new RequestLogRecord.Builder().addRows(row).build())
                        .addRenderingDataList(new RenderingData.Builder().addKeys("123").build())
                        .build());
            }
        }

        @Override public void onDownload(
                DownloadInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<DownloadOutput> consumer
        ) {
            mOnDownloadCalled = true;
            consumer.accept(new DownloadOutput.Builder().addKeysToRetain("12").build());
        }

        @Override public void onRender(
                RenderInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<RenderOutput> consumer
        ) {
            mOnRenderCalled = true;
            if (input.getRenderingData().getKeys().size() >= 1
                        && input.getRenderingData().getKeys().get(0).equals("z")) {
                consumer.accept(null);
            } else {
                consumer.accept(
                        new RenderOutput.Builder().setContent("htmlstring").build());
            }
        }

        @Override public void onEvent(
                EventInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<EventOutput> consumer
        ) {
            mOnEventCalled = true;
            int eventType = input.getParameters().getInt(EVENT_TYPE_KEY);
            if (eventType == 9999) {
                consumer.accept(null);
            } else {
                consumer.accept(
                        new EventOutput.Builder()
                        .setEventLogRecord(
                            new EventLogRecord.Builder()
                                .setType(1)
                                .setRowIndex(2)
                                .setData(new ContentValues())
                                .build())
                        .build());
            }
        }
    }

    class TestService extends IsolatedComputationService {
        @Override public IsolatedComputationCallback createCallback() {
            return new TestHandler();
        }
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback
        ) {}
    }

    class TestServiceCallback extends IIsolatedComputationServiceCallback.Stub {
        @Override public void onSuccess(Bundle result) {
            mCallbackResult = result;
            mLatch.countDown();
        }
        @Override public void onError(int errorCode) {
            mCallbackErrorCode = errorCode;
            mLatch.countDown();
        }
    }
}
