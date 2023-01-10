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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;

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
    private boolean mOnAppRequestCalled;
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
    public void testOnAppRequest() throws Exception {
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_APP_NAME, "com.testapp");
        params.putParcelable(Constants.EXTRA_APP_PARAMS, PersistableBundle.EMPTY);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_APP_REQUEST, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mOnAppRequestCalled);
        AppRequestResult appRequestResult =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, AppRequestResult.class);
        assertEquals("123",
                appRequestResult.getSlotResults().get(0).getWinningBids().get(0).getBidId());
    }

    @Test
    public void testOnAppRequestPropagatesError() throws Exception {
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_APP_NAME, "com.testapp");
        PersistableBundle appParams = new PersistableBundle();
        appParams.putInt("error", 1);  // Trigger an error in the service.
        params.putParcelable(Constants.EXTRA_APP_PARAMS, appParams);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_APP_REQUEST, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mOnAppRequestCalled);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnAppRequestWithoutAppParams() throws Exception {
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_APP_NAME, "com.testapp");
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_APP_REQUEST, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mOnAppRequestCalled);
    }

    @Test
    public void testOnAppRequestThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_APP_REQUEST, null,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testOnAppRequestThrowsIfAppNameMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_APP_PARAMS, PersistableBundle.EMPTY);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_APP_REQUEST, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testOnAppRequestThrowsIfDataAccessServiceMissing() throws Exception {
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_APP_NAME, "com.testapp");
        params.putParcelable(Constants.EXTRA_APP_PARAMS, PersistableBundle.EMPTY);
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_APP_REQUEST, params,
                            new TestPersonalizationServiceCallback());
                });
    }

    @Test
    public void testOnAppRequestThrowsIfCallbackMissing() throws Exception {
        Bundle params = new Bundle();
        params.putString(Constants.EXTRA_APP_NAME, "com.testapp");
        params.putParcelable(Constants.EXTRA_APP_PARAMS, PersistableBundle.EMPTY);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_APP_REQUEST, params, null);
                });
    }

    @Test
    public void testOnDownload() throws Exception {
        Bundle params = new Bundle();
        ParcelFileDescriptor[] pfds = ParcelFileDescriptor.createPipe();
        params.putParcelable(Constants.EXTRA_PARCEL_FD, pfds[0]);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_DOWNLOAD_FINISHED, params, new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mOnDownloadCalled);
        DownloadResult downloadResult =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, DownloadResult.class);
        assertEquals("12", downloadResult.getKeysToRetain().get(0));
        pfds[0].close();
        pfds[1].close();
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
    public void testOnDownloadThrowsIfFdMissing() throws Exception {
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
        Bundle params = new Bundle();
        ParcelFileDescriptor[] pfds = ParcelFileDescriptor.createPipe();
        params.putParcelable(Constants.EXTRA_PARCEL_FD, pfds[0]);
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_DOWNLOAD_FINISHED, params,
                            new TestPersonalizationServiceCallback());
                });
        pfds[0].close();
        pfds[1].close();
    }

    @Test
    public void testOnDownloadThrowsIfCallbackMissing() throws Exception {
        Bundle params = new Bundle();
        ParcelFileDescriptor[] pfds = ParcelFileDescriptor.createPipe();
        params.putParcelable(Constants.EXTRA_PARCEL_FD, pfds[0]);
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
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_SLOT_INFO, new SlotInfo.Builder().build());
        String[] bidIds = {"a", "b"};
        params.putStringArray(Constants.EXTRA_BID_IDS, bidIds);
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
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_SLOT_INFO, new SlotInfo.Builder().build());
        String[] bidIds = {"z"};  // Trigger error in service.
        params.putStringArray(Constants.EXTRA_BID_IDS, bidIds);
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
    public void testRenderContentThrowsIfSlotInfoMissing() throws Exception {
        Bundle params = new Bundle();
        String[] bidIds = {"a", "b"};
        params.putStringArray(Constants.EXTRA_BID_IDS, bidIds);
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
    public void testRenderContentThrowsIfBidIdsMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_SLOT_INFO, new SlotInfo.Builder().build());
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
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_SLOT_INFO, new SlotInfo.Builder().build());
        String[] bidIds = {"a", "b"};
        params.putStringArray(Constants.EXTRA_BID_IDS, bidIds);
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
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_SLOT_INFO, new SlotInfo.Builder().build());
        String[] bidIds = {"a", "b"};
        params.putStringArray(Constants.EXTRA_BID_IDS, bidIds);
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
                Constants.EXTRA_EVENT_METRICS_INPUT, new EventMetricsInput.Builder().build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(
                Constants.OP_COMPUTE_EVENT_METRICS, params,
                new TestPersonalizationServiceCallback());
        mLatch.await();
        assertTrue(mComputeEventMetricsCalled);
        EventMetricsResult result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, EventMetricsResult.class);
        assertEquals(2468, result.getMetrics().getIntMetrics()[0]);
    }

    @Test
    public void testComputeEventMetricsPropagatesError() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_EVENT_METRICS_INPUT,
                // Input value 9999 will trigger an error in the service.
                new EventMetricsInput.Builder().setIntInputs(9999).build());
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
                Constants.EXTRA_EVENT_METRICS_INPUT, new EventMetricsInput.Builder().build());
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
                Constants.EXTRA_EVENT_METRICS_INPUT, new EventMetricsInput.Builder().build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_COMPUTE_EVENT_METRICS, params, null);
                });
    }

    class TestPersonalizationService extends PersonalizationService {
        @Override public void onAppRequest(
                String appPackageName, PersistableBundle appParams,
                OnDevicePersonalizationContext odpContext, AppRequestCallback callback
        ) {
            mOnAppRequestCalled = true;
            if (appParams != null && appParams.getInt("error") > 0) {
                callback.onError();
            } else {
                callback.onSuccess(
                        new AppRequestResult.Builder()
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
                ParcelFileDescriptor fd,
                OnDevicePersonalizationContext odpContext,
                DownloadCallback callback
        ) {
            mOnDownloadCalled = true;
            callback.onSuccess(new DownloadResult.Builder().addKeysToRetain("12").build());
        }

        @Override public void renderContent(
                SlotInfo slotInfo,
                List<String> bidIds,
                OnDevicePersonalizationContext odpContext,
                RenderContentCallback callback
        ) {
            mRenderContentCalled = true;
            if (bidIds.size() >= 1 && bidIds.get(0).equals("z")) {
                callback.onError();
            } else {
                callback.onSuccess(
                        new RenderContentResult.Builder().setContent("htmlstring").build());
            }
        }

        @Override public void computeEventMetrics(
                EventMetricsInput input,
                OnDevicePersonalizationContext odpContext,
                EventMetricsCallback callback
        ) {
            mComputeEventMetricsCalled = true;
            if (input.getIntInputs() != null && input.getIntInputs().length >= 1
                    && input.getIntInputs()[0] == 9999) {
                callback.onError();
            } else {
                callback.onSuccess(
                        new EventMetricsResult.Builder()
                        .setMetrics(
                            new Metrics.Builder().setIntMetrics(2468).build())
                        .build());
            }
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
