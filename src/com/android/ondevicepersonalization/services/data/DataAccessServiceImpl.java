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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ondevicepersonalization.Constants;
import android.ondevicepersonalization.ScoredBid;
import android.ondevicepersonalization.SlotResult;
import android.ondevicepersonalization.aidl.IDataAccessService;
import android.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.HashMap;
import java.util.Objects;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to request data from the managing service.
 */
public class DataAccessServiceImpl extends IDataAccessService.Stub {
    private static final String TAG = "DataAccessServiceImpl";

    /** Parameters needed for generating event URLs. */
    public static class EventUrlQueryData {
        final long mQueryId;
        final String mSlotId;
        final HashMap<String, ScoredBid> mBids = new HashMap<String, ScoredBid>();
        public EventUrlQueryData(long queryId, SlotResult slotResult) {
            mQueryId = queryId;
            mSlotId = slotResult.getSlotId();
            for (ScoredBid bid : slotResult.getWinningBids()) {
                mBids.put(bid.getBidId(), bid);
            }
        }
    }

    @VisibleForTesting
    static class Injector {
        long getTimeMillis() {
            return System.currentTimeMillis();
        }

        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        OnDevicePersonalizationVendorDataDao getVendorDataDao(
                Context context, String packageName, String certDigest
        ) {
            return OnDevicePersonalizationVendorDataDao.getInstance(context,
                    packageName, certDigest);
        }
    }

    @NonNull private final Context mApplicationContext;
    @NonNull private final String mServicePackageName;
    private final OnDevicePersonalizationVendorDataDao mVendorDataDao;
    private final boolean mIncludeUserData;
    @Nullable private final EventUrlQueryData mEventUrlQueryData;
    @NonNull private final Injector mInjector;

    public DataAccessServiceImpl(
            @NonNull String appPackageName,
            @NonNull String servicePackageName,
            @NonNull Context applicationContext,
            boolean includeUserData,
            @Nullable EventUrlQueryData eventUrlQueryData) {
        this(appPackageName, servicePackageName, applicationContext, includeUserData,
                eventUrlQueryData, new Injector());
    }

    @VisibleForTesting
    public DataAccessServiceImpl(
            @NonNull String appPackageName,
            @NonNull String servicePackageName,
            @NonNull Context applicationContext,
            boolean includeUserData,
            @Nullable EventUrlQueryData eventUrlQueryData,
            @NonNull Injector injector) {
        mApplicationContext = Objects.requireNonNull(applicationContext);
        mServicePackageName = Objects.requireNonNull(servicePackageName);
        mInjector = Objects.requireNonNull(injector);
        try {
            mVendorDataDao = mInjector.getVendorDataDao(
                    mApplicationContext, servicePackageName,
                    PackageUtils.getCertDigest(mApplicationContext, servicePackageName));
            mIncludeUserData = includeUserData;
            // if mIncludeUserData is true, also create a R/W DAO for the LOCAL_DATA table.
            mEventUrlQueryData = eventUrlQueryData;
        } catch (PackageManager.NameNotFoundException nnfe) {
            throw new IllegalArgumentException("Package: " + servicePackageName
                    + " does not exist.", nnfe);
        }
    }

    /** Handle a request from the isolated process. */
    @Override
    public void onRequest(
            int operation,
            @NonNull Bundle params,
            @NonNull IDataAccessServiceCallback callback
    ) {
        Log.d(TAG, "onRequest: op=" + operation + " params: " + params.toString());
        switch (operation) {
            case Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP:
                mInjector.getExecutor().execute(
                        () -> remoteDataLookup(
                                params.getStringArray(Constants.EXTRA_LOOKUP_KEYS), callback));
                break;
            case Constants.DATA_ACCESS_OP_GET_EVENT_URL:
                if (mEventUrlQueryData == null) {
                    throw new IllegalArgumentException("EventUrl not available.");
                }
                int eventType = params.getInt(Constants.EXTRA_EVENT_TYPE);
                String bidId = params.getString(Constants.EXTRA_BID_ID);
                String destinationUrl = params.getString(Constants.EXTRA_DESTINATION_URL);
                if (eventType == 0 || bidId == null || bidId.isEmpty()) {
                    throw new IllegalArgumentException("Missing eventType or bidId");
                }
                if (!mEventUrlQueryData.mBids.containsKey(bidId)) {
                    throw new IllegalArgumentException("Invalid bidId");
                }
                mInjector.getExecutor().execute(
                        () -> getEventUrl(eventType, bidId, destinationUrl, callback)
                );
                break;
            case Constants.DATA_ACCESS_OP_REMOTE_DATA_SCAN:
            default:
                sendError(callback);
        }
    }

    private void remoteDataLookup(String[] keys, @NonNull IDataAccessServiceCallback callback) {
        HashMap<String, byte[]> vendorData = new HashMap<>();
        try {
            for (String key : keys) {
                vendorData.put(key, mVendorDataDao.readSingleVendorDataRow(key));
            }
            Bundle result = new Bundle();
            result.putSerializable(Constants.EXTRA_RESULT, vendorData);
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback);
        }
    }

    private void getEventUrl(
            int eventType, @NonNull String bidId, @Nullable String destinationUrl,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            Log.d(TAG, "getEventUrl() started.");
            Event event = new Event.Builder()
                    .setType(eventType)
                    .setQueryId(mEventUrlQueryData.mQueryId)
                    .setServicePackageName(mServicePackageName)
                    .setTimeMillis(mInjector.getTimeMillis())
                    .setSlotId(mEventUrlQueryData.mSlotId)
                    .setSlotPosition(0)  // TODO(b/268718770): Add slot position.
                    .setSlotIndex(0) // TODO(b/268718770): Add slot index.
                    .setBidId(bidId).build();

            boolean needsMetrics = false;
            for (int eventWithMetrics:
                    mEventUrlQueryData.mBids.get(bidId).getEventsWithMetrics()) {
                if (eventType == eventWithMetrics) {
                    needsMetrics = true;
                }
            }
            EventUrlPayload payload =  new EventUrlPayload.Builder()
                    .setEvent(event)
                    .setEventMetricsRequired(needsMetrics)
                    .build();
            String eventUrl;
            if (destinationUrl == null || destinationUrl.isEmpty()) {
                eventUrl = EventUrlHelper.getEncryptedOdpEventUrl(payload);
            } else {
                eventUrl = EventUrlHelper.getEncryptedClickTrackingUrl(
                        payload, destinationUrl);
            }
            Bundle result = new Bundle();
            result.putString(Constants.EXTRA_RESULT, eventUrl);
            Log.d(TAG, "getEventUrl() success. Url: " + eventUrl);
            sendResult(result, callback);
        } catch (Exception e) {
            Log.d(TAG, "getEventUrl() failed.", e);
            sendError(callback);
        }
    }

    private void sendResult(
            @NonNull Bundle result,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            callback.onSuccess(result);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }

    private void sendError(@NonNull IDataAccessServiceCallback callback) {
        try {
            callback.onError(Constants.STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            Log.e(TAG, "Callback error", e);
        }
    }
}
