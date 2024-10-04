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

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.ModelId;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.PackageUtils;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.internal.util.OdpParceledListSlice;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.JoinedEvent;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.vendor.LocalData;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationLocalDataDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;
import com.android.ondevicepersonalization.services.util.IoUtils;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to request data from the managing service.
 */
public class DataAccessServiceImpl extends IDataAccessService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "DataAccessServiceImpl";
    @NonNull
    private final Context mApplicationContext;
    @NonNull
    private final ComponentName mService;
    @Nullable
    private OnDevicePersonalizationVendorDataDao mVendorDataDao = null;
    @Nullable
    private final OnDevicePersonalizationLocalDataDao mLocalDataDao;
    @Nullable
    private final EventsDao mEventsDao;
    private final DataAccessPermission mLocalDataPermission;
    private final DataAccessPermission mEventDataPermission;
    @NonNull
    private final Injector mInjector;
    private Map<String, byte[]> mRemoteData = null;

    public DataAccessServiceImpl(
            @NonNull ComponentName service,
            @NonNull Context applicationContext,
            @NonNull DataAccessPermission localDataPermission,
            @NonNull DataAccessPermission eventDataPermission) {
        this(service, applicationContext, null, localDataPermission, eventDataPermission,
                new Injector());
    }

    public DataAccessServiceImpl(
            @NonNull ComponentName service,
            @NonNull Context applicationContext,
            @NonNull Map<String, byte[]> remoteData,
            @NonNull DataAccessPermission localDataPermission,
            @NonNull DataAccessPermission eventDataPermission) {
        this(service, applicationContext, remoteData, localDataPermission, eventDataPermission,
                new Injector());
    }

    @VisibleForTesting
    public DataAccessServiceImpl(
            @NonNull ComponentName service,
            @NonNull Context applicationContext,
            Map<String, byte[]> remoteData,
            @NonNull DataAccessPermission localDataPermission,
            @NonNull DataAccessPermission eventDataPermission,
            @NonNull Injector injector) {
        mApplicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        mService = Objects.requireNonNull(service, "servicePackageName");
        mInjector = Objects.requireNonNull(injector, "injector");
        try {
            if (remoteData != null) {
                // Use provided remoteData instead of vendorData
                mRemoteData = new HashMap<>(remoteData);
            } else {
                mVendorDataDao = mInjector.getVendorDataDao(
                        mApplicationContext, mService,
                        PackageUtils.getCertDigest(
                                mApplicationContext, mService.getPackageName()));
            }
            mLocalDataPermission = localDataPermission;
            if (mLocalDataPermission != DataAccessPermission.DENIED) {
                mLocalDataDao = mInjector.getLocalDataDao(
                        mApplicationContext, mService,
                        PackageUtils.getCertDigest(
                                mApplicationContext, mService.getPackageName()));
                mLocalDataDao.createTable();
            } else {
                mLocalDataDao = null;
            }
        } catch (PackageManager.NameNotFoundException nnfe) {
            throw new IllegalArgumentException("Service: " + mService.toString()
                    + " does not exist.", nnfe);
        }
        mEventDataPermission = eventDataPermission;
        if (mEventDataPermission != DataAccessPermission.DENIED) {
            mEventsDao = mInjector.getEventsDao(mApplicationContext);
        } else {
            mEventsDao = null;
        }
    }

    /** Handle a request from the isolated process. */
    @Override
    public void onRequest(
            int operation,
            @NonNull Bundle params,
            @NonNull IDataAccessServiceCallback callback
    ) {
        sLogger.d(TAG + ": onRequest: op=" + operation + " params: " + params.toString());
        switch (operation) {
            case Constants.DATA_ACCESS_OP_REMOTE_DATA_LOOKUP:
                String lookupKey = params.getString(Constants.EXTRA_LOOKUP_KEYS);
                if (lookupKey == null || lookupKey.isEmpty()) {
                    sLogger.w(TAG + "Missing lookup key.");
                    sendError(callback, Constants.STATUS_KEY_NOT_FOUND);
                    break;
                }
                mInjector.getExecutor().execute(
                        () -> remoteDataLookup(
                                lookupKey, callback));
                break;
            case Constants.DATA_ACCESS_OP_REMOTE_DATA_KEYSET:
                mInjector.getExecutor().execute(
                        () -> remoteDataKeyset(callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_LOOKUP:
                if (mLocalDataPermission == DataAccessPermission.DENIED) {
                    sLogger.w(TAG + "LocalData is not included for this instance.");
                    sendError(callback, Constants.STATUS_PERMISSION_DENIED);
                    break;
                }
                lookupKey = params.getString(Constants.EXTRA_LOOKUP_KEYS);
                if (lookupKey == null || lookupKey.isEmpty()) {
                    sLogger.w(TAG + "Missing lookup key.");
                    sendError(callback, Constants.STATUS_KEY_NOT_FOUND);
                    break;
                }
                mInjector.getExecutor().execute(
                        () -> localDataLookup(
                                lookupKey, callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_KEYSET:
                if (mLocalDataPermission == DataAccessPermission.DENIED) {
                    sLogger.w(TAG + "LocalData is not included for this instance.");
                    sendError(callback, Constants.STATUS_PERMISSION_DENIED);
                    break;
                }
                mInjector.getExecutor().execute(
                        () -> localDataKeyset(callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_PUT:
                if (mLocalDataPermission == DataAccessPermission.DENIED) {
                    sLogger.w(TAG + "LocalData is not included for this instance.");
                    sendError(callback, Constants.STATUS_PERMISSION_DENIED);
                    break;
                }
                if (mLocalDataPermission == DataAccessPermission.READ_ONLY) {
                    sLogger.w(TAG + "LocalData is read-only for this instance.");
                    sendError(callback, Constants.STATUS_LOCAL_DATA_READ_ONLY);
                    break;
                }
                String putKey = params.getString(Constants.EXTRA_LOOKUP_KEYS);
                ByteArrayParceledSlice parceledValue = params.getParcelable(
                        Constants.EXTRA_VALUE, ByteArrayParceledSlice.class);
                if (parceledValue == null
                        || putKey == null || putKey.isEmpty()) {
                    throw new IllegalArgumentException("Invalid key or value for put.");
                }
                mInjector.getExecutor().execute(
                        () -> localDataPut(putKey, parceledValue, callback));
                break;
            case Constants.DATA_ACCESS_OP_LOCAL_DATA_REMOVE:
                if (mLocalDataPermission == DataAccessPermission.DENIED) {
                    sLogger.w(TAG + "LocalData is not included for this instance.");
                    sendError(callback, Constants.STATUS_PERMISSION_DENIED);
                    break;
                }
                if (mLocalDataPermission == DataAccessPermission.READ_ONLY) {
                    sLogger.w(TAG + "LocalData is read-only for this instance.");
                    sendError(callback, Constants.STATUS_LOCAL_DATA_READ_ONLY);
                    break;
                }
                String deleteKey = params.getString(Constants.EXTRA_LOOKUP_KEYS);
                if (deleteKey == null || deleteKey.isEmpty()) {
                    sLogger.w(TAG + "Missing delete key.");
                    sendError(callback, Constants.STATUS_KEY_NOT_FOUND);
                    break;
                }
                mInjector.getExecutor().execute(
                        () -> localDataDelete(deleteKey, callback));
                break;
            case Constants.DATA_ACCESS_OP_GET_EVENT_URL:
                PersistableBundle eventParams = Objects.requireNonNull(params.getParcelable(
                        Constants.EXTRA_EVENT_PARAMS, PersistableBundle.class));
                byte[] responseData = params.getByteArray(Constants.EXTRA_RESPONSE_DATA);
                String mimeType = params.getString(Constants.EXTRA_MIME_TYPE);
                String destinationUrl = params.getString(Constants.EXTRA_DESTINATION_URL);
                mInjector.getExecutor().execute(
                        () -> getEventUrl(
                                eventParams, responseData, mimeType, destinationUrl, callback)
                );
                break;
            case Constants.DATA_ACCESS_OP_GET_REQUESTS:
                if (mEventDataPermission == DataAccessPermission.DENIED) {
                    sLogger.w(TAG + "Request and event data are not included for this instance.");
                    sendError(callback, Constants.STATUS_PERMISSION_DENIED);
                    break;
                }
                long[] requestTimes = Objects.requireNonNull(params.getLongArray(
                        Constants.EXTRA_LOOKUP_KEYS));
                if (requestTimes.length != 2) {
                    sLogger.w(TAG + "Invalid request timestamps provided.");
                    sendError(callback, Constants.STATUS_REQUEST_TIMESTAMPS_INVALID);
                    break;
                }
                mInjector.getExecutor().execute(
                        () -> getRequests(requestTimes[0], requestTimes[1], callback));
                break;
            case Constants.DATA_ACCESS_OP_GET_JOINED_EVENTS:
                if (mEventDataPermission == DataAccessPermission.DENIED) {
                    sLogger.w(TAG + "Request and event data are not included for this instance.");
                    sendError(callback, Constants.STATUS_PERMISSION_DENIED);
                    break;
                }
                long[] eventTimes = Objects.requireNonNull(params.getLongArray(
                        Constants.EXTRA_LOOKUP_KEYS));
                if (eventTimes.length != 2) {
                    sLogger.w(TAG + "Invalid request timestamps provided.");
                    sendError(callback, Constants.STATUS_REQUEST_TIMESTAMPS_INVALID);
                    break;
                }
                mInjector.getExecutor().execute(
                        () -> getJoinedEvents(eventTimes[0], eventTimes[1], callback));
                break;
            case Constants.DATA_ACCESS_OP_GET_MODEL:
                ModelId modelId = params.getParcelable(Constants.EXTRA_MODEL_ID, ModelId.class);
                if (modelId == null) {
                    sLogger.w(TAG + "Model Id is not provided.");
                    sendError(callback, Constants.STATUS_KEY_NOT_FOUND);
                    break;
                }
                mInjector.getExecutor().execute(() -> getModelFileDescriptor(modelId, callback));
                break;
            default:
                sendError(callback, Constants.STATUS_DATA_ACCESS_UNSUPPORTED_OP);
        }
    }

    @Override
    public void logApiCallStats(
            int apiName, long latencyMillis, int responseCode) {
        mInjector.getExecutor().execute(
                () -> handleLogApiCallStats(apiName, latencyMillis, responseCode));
    }

    private void handleLogApiCallStats(
            int apiName, long latencyMillis, int responseCode) {
        try {
            mInjector
                    .getOdpStatsdLogger()
                    .logApiCallStats(
                            new ApiCallStats.Builder(apiName)
                                    .setResponseCode(responseCode)
                                    .setLatencyMillis((int) latencyMillis)
                                    .setSdkPackageName(mService.getPackageName())
                                    .build());
        } catch (Exception e) {
            sLogger.e(e, TAG + ": error logging api call stats");
        }
    }

    private void remoteDataKeyset(@NonNull IDataAccessServiceCallback callback) {
        Bundle result = new Bundle();
        HashSet<String> keyset;
        if (mRemoteData != null) {
            keyset = new HashSet<>(mRemoteData.keySet());
        } else {
            keyset = new HashSet<>(mVendorDataDao.readAllVendorDataKeys());
        }
        result.putSerializable(Constants.EXTRA_RESULT, keyset);
        sendResult(result, callback);
    }

    private void localDataKeyset(@NonNull IDataAccessServiceCallback callback) {
        Bundle result = new Bundle();
        result.putSerializable(Constants.EXTRA_RESULT,
                new HashSet<>(mLocalDataDao.readAllLocalDataKeys()));
        sendResult(result, callback);
    }

    private void remoteDataLookup(String key, @NonNull IDataAccessServiceCallback callback) {
        try {
            byte[] data;
            if (mRemoteData != null) {
                data = mRemoteData.get(key);
            } else {
                data = mVendorDataDao.readSingleVendorDataRow(key);
            }
            Bundle result = new Bundle();
            result.putParcelable(
                    Constants.EXTRA_RESULT, new ByteArrayParceledSlice(data));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback, Constants.STATUS_DATA_ACCESS_FAILURE);
        }
    }

    private void localDataLookup(String key, @NonNull IDataAccessServiceCallback callback) {
        try {
            byte[] data = mLocalDataDao.readSingleLocalDataRow(key);
            Bundle result = new Bundle();
            result.putParcelable(
                    Constants.EXTRA_RESULT, new ByteArrayParceledSlice(data));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback, Constants.STATUS_DATA_ACCESS_FAILURE);
        }
    }

    private void localDataPut(String key, ByteArrayParceledSlice parceledData,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            byte[] data = parceledData.getByteArray();
            byte[] existingData = mLocalDataDao.readSingleLocalDataRow(key);
            if (!mLocalDataDao.updateOrInsertLocalData(
                    new LocalData.Builder().setKey(key).setData(data).build())) {
                sendError(callback, Constants.STATUS_LOCAL_WRITE_DATA_ACCESS_FAILURE);
            }
            Bundle result = new Bundle();
            result.putParcelable(
                    Constants.EXTRA_RESULT, new ByteArrayParceledSlice(existingData));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback, Constants.STATUS_DATA_ACCESS_FAILURE);
        }
    }

    private void localDataDelete(String key, @NonNull IDataAccessServiceCallback callback) {
        try {
            byte[] existingData = mLocalDataDao.readSingleLocalDataRow(key);
            mLocalDataDao.deleteLocalDataRow(key);
            Bundle result = new Bundle();
            result.putParcelable(
                    Constants.EXTRA_RESULT, new ByteArrayParceledSlice(existingData));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback, Constants.STATUS_DATA_ACCESS_FAILURE);
        }
    }

    private void getEventUrl(
            @NonNull PersistableBundle eventParams,
            @Nullable byte[] responseData,
            @Nullable String mimeType,
            @Nullable String destinationUrl,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            sLogger.d(TAG, ": getEventUrl() started.");
            EventUrlPayload payload = new EventUrlPayload(eventParams, responseData, mimeType);
            Uri eventUrl;
            if (destinationUrl == null || destinationUrl.isEmpty()) {
                eventUrl = EventUrlHelper.getEncryptedOdpEventUrl(payload);
            } else {
                eventUrl = EventUrlHelper.getEncryptedClickTrackingUrl(
                        payload, destinationUrl);
            }
            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT, eventUrl);
            sLogger.d(TAG + ": getEventUrl() success. Url: " + eventUrl.toString());
            sendResult(result, callback);
        } catch (Exception e) {
            sLogger.d(TAG + ": getEventUrl() failed.", e);
            sendError(callback, Constants.STATUS_DATA_ACCESS_FAILURE);
        }
    }

    private void getRequests(long startTimeMillis, long endTimeMillis,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            List<Query> queries = mEventsDao.readAllQueries(
                    startTimeMillis, endTimeMillis, mService);
            List<RequestLogRecord> requestLogRecords = new ArrayList<>();
            for (Query query : queries) {
                RequestLogRecord record = new RequestLogRecord.Builder()
                        .setRows(OnDevicePersonalizationFlatbufferUtils
                                .getContentValuesFromQueryData(query.getQueryData()))
                        .setRequestId(query.getQueryId())
                        .setTimeMillis(query.getTimeMillis())
                        .build();
                requestLogRecords.add(record);
            }
            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT,
                    new OdpParceledListSlice<>(requestLogRecords));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback, Constants.STATUS_DATA_ACCESS_FAILURE);
        }
    }

    private void getJoinedEvents(long startTimeMillis, long endTimeMillis,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            List<JoinedEvent> joinedEvents = mEventsDao.readJoinedTableRows(
                    startTimeMillis, endTimeMillis, mService);
            List<EventLogRecord> joinedLogRecords = new ArrayList<>();
            for (JoinedEvent joinedEvent : joinedEvents) {
                RequestLogRecord requestLogRecord = new RequestLogRecord.Builder()
                        .setRequestId(joinedEvent.getQueryId())
                        .setRows(OnDevicePersonalizationFlatbufferUtils
                                .getContentValuesFromQueryData(joinedEvent.getQueryData()))
                        .setTimeMillis(joinedEvent.getQueryTimeMillis())
                        .build();
                EventLogRecord record = new EventLogRecord.Builder()
                        .setTimeMillis(joinedEvent.getEventTimeMillis())
                        .setType(joinedEvent.getType())
                        .setData(
                                OnDevicePersonalizationFlatbufferUtils
                                        .getContentValuesFromEventData(joinedEvent.getEventData()))
                        .setRequestLogRecord(requestLogRecord)
                        .build();
                joinedLogRecords.add(record);
            }
            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT,
                    new OdpParceledListSlice<>(joinedLogRecords));
            sendResult(result, callback);
        } catch (Exception e) {
            sendError(callback, Constants.STATUS_DATA_ACCESS_FAILURE);
        }
    }

    private void getModelFileDescriptor(
            ModelId modelId, @NonNull IDataAccessServiceCallback callback) {
        try {
            byte[] modelData = null;
            switch (modelId.getTableId()) {
                case ModelId.TABLE_ID_REMOTE_DATA:
                    modelData = mVendorDataDao.readSingleVendorDataRow(modelId.getKey());
                    break;
                case ModelId.TABLE_ID_LOCAL_DATA:
                    modelData = mLocalDataDao.readSingleLocalDataRow(modelId.getKey());
                    break;
                default:
                    sLogger.e(TAG + "Unsupported model table Id %d", modelId.getTableId());
                    sendError(callback, Constants.STATUS_MODEL_TABLE_ID_INVALID);
                    return;
            }

            if (modelData == null) {
                sLogger.e(TAG + " Failed to find model data from database: " + modelId.getKey());
                sendError(callback, Constants.STATUS_MODEL_DB_LOOKUP_FAILED);
                return;
            }
            String modelFile =
                    IoUtils.writeToTempFile(
                            modelId.getKey() + "_" + mInjector.getTimeMillis(), modelData);
            ParcelFileDescriptor modelFd =
                    IoUtils.createFileDescriptor(modelFile, ParcelFileDescriptor.MODE_READ_ONLY);

            Bundle result = new Bundle();
            result.putParcelable(Constants.EXTRA_RESULT, modelFd);
            sendResult(result, callback);
        } catch (Exception e) {
            sLogger.e(e, TAG + " Failed to find model data: %s ", modelId.getKey());
            sendError(callback, Constants.STATUS_MODEL_LOOKUP_FAILURE);
        }
    }

    private void sendResult(
            @NonNull Bundle result,
            @NonNull IDataAccessServiceCallback callback) {
        try {
            callback.onSuccess(result);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    private void sendError(@NonNull IDataAccessServiceCallback callback, int errorCode) {
        try {
            callback.onError(errorCode);
        } catch (RemoteException e) {
            sLogger.e(e, TAG + ": Callback error! Failed to set error code %d", errorCode);
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
                Context context, ComponentName service, String certDigest
        ) {
            return OnDevicePersonalizationVendorDataDao.getInstance(context,
                    service, certDigest);
        }

        OnDevicePersonalizationLocalDataDao getLocalDataDao(
                Context context, ComponentName service, String certDigest
        ) {
            return OnDevicePersonalizationLocalDataDao.getInstance(context,
                    service, certDigest);
        }

        EventsDao getEventsDao(
                Context context
        ) {
            return EventsDao.getInstance(context);
        }

        OdpStatsdLogger getOdpStatsdLogger() {
            return OdpStatsdLogger.getInstance();
        }
    }
}
