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

import static android.ondevicepersonalization.OnDevicePersonalizationContext.RESPONSE_TYPE_REDIRECT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OnDevicePersonalizationContextImplTest {
    static final int EVENT_TYPE_ERROR = 10;
    private final OnDevicePersonalizationContext mOdpContext =
            new OnDevicePersonalizationContextImpl(new TestDataService());

    @Test public void testGetEventUrlReturnsResponseFromService() throws Exception {
        assertEquals(
                "5-abc-2-def",
                mOdpContext.getEventUrl(5, "abc", RESPONSE_TYPE_REDIRECT, "def"));
    }

    @Test public void testGetEventUrlThrowsOnError() throws Exception {
        // EventType 10 triggers error in the mock service.
        assertThrows(
                OnDevicePersonalizationException.class,
                () -> mOdpContext.getEventUrl(
                        EVENT_TYPE_ERROR, "abc", RESPONSE_TYPE_REDIRECT, "def"));
    }

    class TestDataService extends IDataAccessService.Stub {
        @Override
        public void onRequest(
                int operation,
                Bundle params,
                IDataAccessServiceCallback callback) {
            if (operation == Constants.DATA_ACCESS_OP_GET_EVENT_URL) {
                int eventType = params.getInt(Constants.EXTRA_EVENT_TYPE);
                String bidId = params.getString(Constants.EXTRA_BID_ID);
                int responseType = params.getInt(Constants.EXTRA_RESPONSE_TYPE);
                String destinationUrl = params.getString(Constants.EXTRA_DESTINATION_URL);
                if (eventType == EVENT_TYPE_ERROR) {
                    try {
                        callback.onError(Constants.STATUS_INTERNAL_ERROR);
                    } catch (RemoteException e) {
                        // Ignored.
                    }
                } else {
                    String url = String.format(
                            "%d-%s-%d-%s", eventType, bidId, responseType, destinationUrl);
                    Bundle result = new Bundle();
                    result.putString(Constants.EXTRA_RESULT, url);
                    try {
                        callback.onSuccess(result);
                    } catch (RemoteException e) {
                        // Ignored.
                    }
                }
            } else {
                throw new IllegalStateException("Unexpected test input");
            }
        }
    }
}
