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

package com.android.ondevicepersonalization.services.download;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.util.JsonReader;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.PluginController;
import com.android.ondevicepersonalization.libraries.plugin.PluginManager;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.VendorData;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.download.mdd.OnDevicePersonalizationFileGroupPopulator;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.plugin.PluginUtils;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ParcelFileDescriptorOpener;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * AsyncCallable to handle the processing of the downloaded vendor data
 */
public class OnDevicePersonalizationDataProcessingAsyncCallable implements AsyncCallable {
    public static final String TASK_NAME = "DownloadJob";
    private static final String TAG = "OnDevicePersonalizationDataProcessingAsyncCallable";
    private final String mPackageName;
    private final Context mContext;
    private final PluginManager mPluginManager;
    private final PackageInfo mPackageInfo;
    private PluginController mPluginController;
    private OnDevicePersonalizationVendorDataDao mDao;

    public OnDevicePersonalizationDataProcessingAsyncCallable(PackageInfo packageInfo,
            Context context, PluginManager pluginManager) {
        mPackageInfo = packageInfo;
        mPackageName = packageInfo.packageName;
        mContext = context;
        mPluginManager = pluginManager;
    }

    private static boolean validateSyncToken(long syncToken) {
        // TODO(b/249813538) Add any additional requirements
        return syncToken % 3600 == 0;
    }

    /**
     * Processes the downloaded files for the given package and stores the data into sqlite
     * vendor tables
     */
    public ListenableFuture<Void> call() {
        Log.d(TAG, "Package Name: " + mPackageName);
        MobileDataDownload mdd = MobileDataDownloadFactory.getMdd(mContext);
        try {
            String fileGroupName =
                    OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                            mPackageName, mContext);
            ClientFileGroup clientFileGroup = mdd.getFileGroup(
                    GetFileGroupRequest.newBuilder().setGroupName(fileGroupName).build()).get();
            if (clientFileGroup == null) {
                Log.d(TAG, mPackageName + " has no completed downloads.");
                return Futures.immediateFuture(null);
            }
            // It is currently expected that we will only download a single file per package.
            if (clientFileGroup.getFileCount() != 1) {
                Log.d(TAG, mPackageName + " has " + clientFileGroup.getFileCount()
                        + " files in the fileGroup");
                return Futures.immediateFuture(null);
            }
            ClientFile clientFile = clientFileGroup.getFile(0);
            Uri androidUri = Uri.parse(clientFile.getFileUri());
            return processDownloadedJsonFile(androidUri);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "NameNotFoundException for package: " + mPackageName);
        } catch (ExecutionException | IOException e) {
            Log.e(TAG, "Exception for package: " + mPackageName, e);
        } catch (InterruptedException e) {
            Log.d(TAG, mPackageName + " was interrupted.");
        }
        return Futures.immediateFuture(null);
    }

    private ListenableFuture<Void> processDownloadedJsonFile(Uri uri) throws IOException,
            PackageManager.NameNotFoundException, InterruptedException, ExecutionException {
        try {
            mPluginController = Objects.requireNonNull(
                    PluginUtils.createPluginController(
                            PluginUtils.createPluginId(mPackageName,
                                    TASK_NAME), mPluginManager,
                            new String[]{mPackageName}));
        } catch (Exception e) {
            Log.e(TAG, "Could not create plugin controller.", e);
            return Futures.immediateFuture(null);
        }

        long syncToken = -1;
        Map<String, VendorData> vendorDataMap = null;

        SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        try (InputStream in = fileStorage.open(uri, ReadStreamOpener.create())) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("syncToken")) {
                        syncToken = reader.nextLong();
                    } else if (name.equals("contents")) {
                        vendorDataMap = readContentsArray(reader);
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
        }

        if (syncToken == -1 || !validateSyncToken(syncToken)) {
            Log.d(TAG, mPackageName + " downloaded JSON file has invalid syncToken provided");
            return Futures.immediateFuture(null);
        }
        if (vendorDataMap == null || vendorDataMap.size() == 0) {
            Log.d(TAG, mPackageName + " downloaded JSON file has no content provided");
            return Futures.immediateFuture(null);
        }

        mDao = OnDevicePersonalizationVendorDataDao.getInstance(
                mContext, mPackageName,
                PackageUtils.getCertDigest(mContext, mPackageName));
        long existingSyncToken = mDao.getSyncToken();

        // Check if the downloaded file has newer data than what is currently stored
        if (existingSyncToken != -1 && existingSyncToken <= syncToken) {
            return Futures.immediateFuture(null);
        }

        Map<String, VendorData> finalVendorDataMap = vendorDataMap;
        long finalSyncToken = syncToken;
        return FluentFuture.from(PluginUtils.loadPlugin(mPluginController))
                .transformAsync(unused -> executePlugin(fileStorage.open(uri,
                                ParcelFileDescriptorOpener.create())),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor())
                .transform(pluginResult -> filterAndStoreData(pluginResult, finalSyncToken,
                                finalVendorDataMap),
                        OnDevicePersonalizationExecutors.getBackgroundExecutor());
    }

    private Void filterAndStoreData(PersistableBundle pluginResult, long syncToken,
            Map<String, VendorData> vendorDataMap) {
        Log.d(TAG, "Plugin filter code completed successfully");
        List<VendorData> filteredList = new ArrayList<>();
        String[] retainedKeys = pluginResult.getStringArray(PluginUtils.OUTPUT_RESULT_KEY);
        for (String key : retainedKeys) {
            if (vendorDataMap.containsKey(key)) {
                filteredList.add(vendorDataMap.get(key));
            }
        }
        mDao.batchUpdateOrInsertVendorDataTransaction(filteredList,
                syncToken);
        return null;
    }

    private ListenableFuture<PersistableBundle> executePlugin(ParcelFileDescriptor fd) {
        Bundle pluginParams = new Bundle();
        pluginParams.putString(PluginUtils.PARAM_CLASS_NAME_KEY,
                AppManifestConfigHelper.getDownloadHandlerFromOdpSettings(mContext, mPackageInfo));
        pluginParams.putInt(PluginUtils.PARAM_OPERATION_KEY,
                PluginUtils.OP_DOWNLOAD_FILTER_HANDLER);
        pluginParams.putParcelable(PluginUtils.INPUT_PARCEL_FD, fd);
        return PluginUtils.executePlugin(mPluginController, pluginParams);
    }

    private Map<String, VendorData> readContentsArray(JsonReader reader) throws IOException {
        Map<String, VendorData> vendorDataMap = new HashMap<>();
        reader.beginArray();
        while (reader.hasNext()) {
            VendorData data = readContent(reader);
            if (data != null) {
                vendorDataMap.put(data.getKey(), data);
            }
        }
        reader.endArray();

        return vendorDataMap;
    }

    private VendorData readContent(JsonReader reader) throws IOException {
        String key = null;
        byte[] data = null;
        String fp = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("key")) {
                key = reader.nextString();
            } else if (name.equals("data")) {
                data = reader.nextString().getBytes();
            } else if (name.equals("fp")) {
                fp = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (key == null || data == null || fp == null) {
            return null;
        }
        return new VendorData.Builder().setKey(key).setData(data).setFp(fp).build();
    }
}
