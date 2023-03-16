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

import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.ondevicepersonalization.aidl.IPersonalizationService;
import android.ondevicepersonalization.aidl.IPersonalizationServiceCallback;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.StringParceledListSlice;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledListSlice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Unit Tests of PersonalizationService class.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersonalizationServiceTest {
    private final TestPersonalizationService mTestPersonalizationService =
            new TestPersonalizationService();
    private IPersonalizationService mBinder;
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private boolean mSelectContentCalled;
    private boolean mOnDownloadCalled;
    private boolean mRenderContentCalled;
    private boolean mComputeEventMetricsCalled;
    private Bundle mCallbackResult;
    private int mCallbackErrorCode;

    @Before
    public void setUp() {
        mTestPersonalizationService.onCreate();
        mBinder =
            IPersonalizationService.Stub.asInterface(mTestPersonalizationService.onBind(null));
    }

    @Test
    public void testServiceThrowsIfOpcodeInvalid() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mBinder.onRequest(
                            9999, new Bundle(),
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testSelectContent() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString("x", "y");
        SelectContentInput input =
                new SelectContentInput.Builder()
                .setAppPackageName("com.testapp")
                .addSlotInfos(new SlotInfo.Builder().setWidth(100).setHeight(50).build())
                .setAppParams(appParams)
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_SELECT_CONTENT, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mSelectContentCalled);
        SelectContentResult SelectContentResult =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, SelectContentResult.class);
        assertEquals("123",
                SelectContentResult.getSlotResults().get(0).getWinningBids().get(0).getBidId());
    }

    @Test
    public void testSelectContentPropagatesError() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putInt("error", 1);  // Trigger an error in the service.
        SelectContentInput input =
                new SelectContentInput.Builder()
                .setAppPackageName("com.testapp")
                .addSlotInfos(new SlotInfo.Builder().setWidth(100).setHeight(50).build())
                .setAppParams(appParams)
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_SELECT_CONTENT, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mSelectContentCalled);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testSelectContentWithoutAppParams() throws Exception {
        SelectContentInput input =
                new SelectContentInput.Builder()
                .setAppPackageName("com.testapp")
                .addSlotInfos(new SlotInfo.Builder().setWidth(100).setHeight(50).build())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_SELECT_CONTENT, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mSelectContentCalled);
    }

    @Test
    public void testSelectContentThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_SELECT_CONTENT, null,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testSelectContentThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_SELECT_CONTENT, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testSelectContentThrowsIfDataAccessServiceMissing() throws Exception {
        SelectContentInput input =
                new SelectContentInput.Builder()
                .setAppPackageName("com.testapp")
                .addSlotInfos(new SlotInfo.Builder().setWidth(100).setHeight(50).build())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_SELECT_CONTENT, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testSelectContentThrowsIfCallbackMissing() throws Exception {
        SelectContentInput input =
                new SelectContentInput.Builder()
                .setAppPackageName("com.testapp")
                .addSlotInfos(new SlotInfo.Builder().setWidth(100).setHeight(50).build())
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_SELECT_CONTENT, params, null);
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
                Constants.OP_DOWNLOAD_FINISHED, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mOnDownloadCalled);
        DownloadResult downloadResult =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, DownloadResult.class);
        assertEquals("12", downloadResult.getKeysToRetain().get(0));
    }

    @Test
    public void testOnDownloadThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_DOWNLOAD_FINISHED, null,
                            new TestPersonalizationServiceCallback());
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
                            Constants.OP_DOWNLOAD_FINISHED, params,
                            new TestPersonalizationServiceCallback());
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
                            Constants.OP_DOWNLOAD_FINISHED, params,
                            new TestPersonalizationServiceCallback());
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
                            Constants.OP_DOWNLOAD_FINISHED, params, null);
                });
    }

    @Test
    public void testRenderContent() throws Exception {
        RenderContentInput input =
                new RenderContentInput.Builder()
                .setSlotInfo(
                    new SlotInfo.Builder().build()
                )
                .addBidIds("a")
                .addBidIds("b")
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_RENDER_CONTENT, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mRenderContentCalled);
        RenderContentResult result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, RenderContentResult.class);
        assertEquals("htmlstring", result.getContent());
    }

    @Test
    public void testRenderContentPropagatesError() throws Exception {
        RenderContentInput input =
                new RenderContentInput.Builder()
                .setSlotInfo(
                    new SlotInfo.Builder().build()
                )
                .addBidIds("z")  // Trigger error in service.
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_RENDER_CONTENT, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mRenderContentCalled);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testRenderContentThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER_CONTENT, null,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testRenderContentThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER_CONTENT, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testRenderContentThrowsIfDataAccessServiceMissing() throws Exception {
        RenderContentInput input =
                new RenderContentInput.Builder()
                .setSlotInfo(
                    new SlotInfo.Builder().build()
                )
                .addBidIds("a")
                .addBidIds("b")
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER_CONTENT, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testRenderContentThrowsIfCallbackMissing() throws Exception {
        RenderContentInput input =
                new RenderContentInput.Builder()
                .setSlotInfo(
                    new SlotInfo.Builder().build()
                )
                .addBidIds("a")
                .addBidIds("b")
                .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_RENDER_CONTENT, params, null);
                });
    }

    @Test
    public void testComputeEventMetrics() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT, new EventMetricsInput.Builder().build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_COMPUTE_EVENT_METRICS, params,
                new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mComputeEventMetricsCalled);
        EventMetricsResult result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, EventMetricsResult.class);
        assertEquals(2468, result.getMetrics().getLongValues()[0]);
    }

    @Test
    public void testComputeEventMetricsPropagatesError() throws Exception {
        PersistableBundle eventParams = new PersistableBundle();
        eventParams.putInt("x", 9999);  // Input value 9999 will trigger an error in the service.
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventMetricsInput.Builder().setEventParams(eventParams).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_COMPUTE_EVENT_METRICS, params,
                new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mComputeEventMetricsCalled);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testComputeEventMetricsThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_COMPUTE_EVENT_METRICS, null,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testComputeEventMetricsThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_COMPUTE_EVENT_METRICS, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testComputeEventMetricsThrowsIfDataAccessServiceMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT, new EventMetricsInput.Builder().build());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_COMPUTE_EVENT_METRICS, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testComputeEventMetricsThrowsIfCallbackMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT, new EventMetricsInput.Builder().build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_COMPUTE_EVENT_METRICS, params, null);
                });
    }

    class TestPersonalizationHandler implements PersonalizationHandler {
        @Override public void selectContent(
                SelectContentInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<SelectContentResult> consumer
        ) {
            mSelectContentCalled = true;
            if (input.getAppParams() != null && input.getAppParams().getInt("error") > 0) {
                consumer.accept(null);
            } else {
                consumer.accept(
                        new SelectContentResult.Builder()
                        .addSlotResults(
                            new SlotResult.Builder()
                                .addWinningBids(
                                    new ScoredBid.Builder()
                                    .setBidId("123")
                                    .build()
                                )
                                .build()
                        )
                        .build());
            }
        }

        @Override public void onDownload(
                DownloadInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<DownloadResult> consumer
        ) {
            mOnDownloadCalled = true;
            consumer.accept(new DownloadResult.Builder().addKeysToRetain("12").build());
        }

        @Override public void renderContent(
                RenderContentInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<RenderContentResult> consumer
        ) {
            mRenderContentCalled = true;
            if (input.getBidIds().size() >= 1 && input.getBidIds().get(0).equals("z")) {
                consumer.accept(null);
            } else {
                consumer.accept(
                        new RenderContentResult.Builder().setContent("htmlstring").build());
            }
        }

        @Override public void computeEventMetrics(
                EventMetricsInput input,
                OnDevicePersonalizationContext odpContext,
                Consumer<EventMetricsResult> consumer
        ) {
            mComputeEventMetricsCalled = true;
            if (input.getEventParams() != null && input.getEventParams().getInt("x") == 9999) {
                consumer.accept(null);
            } else {
                consumer.accept(
                        new EventMetricsResult.Builder()
                        .setMetrics(
                            new Metrics.Builder().setLongValues(2468).build())
                        .build());
            }
        }
    }

    class TestPersonalizationService extends PersonalizationService {
        @Override public PersonalizationHandler getHandler() {
            return new TestPersonalizationHandler();
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

    class TestPersonalizationServiceCallback extends IPersonalizationServiceCallback.Stub {
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
