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
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.JsonReader;
import android.util.Log;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.VendorData;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.download.mdd.OnDevicePersonalizationFileGroupPopulator;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Runnable to handle the processing of the downloaded vendor data
 */
public class OnDevicePersonalizationDataProcessingRunnable implements Runnable {
    private static final String TAG = "OnDevicePersonalizationDataProcessingRunnable";
    private final String mPackageName;
    private final Context mContext;

    public OnDevicePersonalizationDataProcessingRunnable(String packageName, Context context) {
        mPackageName = packageName;
        mContext = context;
    }

    /**
     * Processes the downloaded files for the given package and stores the data into sqlite
     * vendor tables
     */
    public void run() {
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
                return;
            }
            // It is currently expected that we will only download a single file per package.
            if (clientFileGroup.getFileCount() != 1) {
                Log.d(TAG, mPackageName + " has " + String.valueOf(clientFileGroup.getFileCount())
                        + " files in the fileGroup");
                return;
            }
            ClientFile clientFile = clientFileGroup.getFile(0);
            Uri androidUri = Uri.parse(clientFile.getFileUri());
            SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
            Log.d(TAG, String.valueOf(fileStorage.fileSize(androidUri)));
            processDownloadedJsonFile(androidUri);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "NameNotFoundException for package: " + mPackageName);
        } catch (ExecutionException | IOException e) {
            Log.e(TAG, "Exception for package: " + mPackageName, e);
        } catch (InterruptedException e) {
            Log.d(TAG, mPackageName + " was interrupted.");
        }

    }

    private void processDownloadedJsonFile(Uri uri) throws IOException,
            PackageManager.NameNotFoundException {
        long syncToken = -1;
        List<VendorData> vendorDataList = null;

        SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        try (InputStream in = fileStorage.open(uri, ReadStreamOpener.create())) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("syncToken")) {
                        syncToken = reader.nextLong();
                    } else if (name.equals("contents")) {
                        vendorDataList = readContentsArray(reader);
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
        }

        if (syncToken == -1 || !validateSyncToken(syncToken)) {
            Log.d(TAG, mPackageName + " downloaded JSON file has invalid syncToken provided");
            return;
        }
        if (vendorDataList == null || vendorDataList.size() == 0) {
            Log.d(TAG, mPackageName + " downloaded JSON file has no content provided");
            return;
        }

        OnDevicePersonalizationVendorDataDao dao = OnDevicePersonalizationVendorDataDao.getInstance(
                mContext, mPackageName,
                PackageUtils.getCertDigest(mContext, mPackageName));
        long existingSyncToken = dao.getSyncToken();

        // Check if the downloaded file has newer data than what is currently stored
        if (existingSyncToken != -1 && existingSyncToken <= syncToken) {
            return;
        }
        // TODO(b/239479120) Call code to filter
        // Store syncToken and data via transaction
        dao.batchUpdateOrInsertVendorDataTransaction(vendorDataList, syncToken);
    }

    private static boolean validateSyncToken(long syncToken) {
        // TODO(b/249813538) Add any additional requirements
        return syncToken % 3600 == 0;
    }

    private List<VendorData> readContentsArray(JsonReader reader) throws IOException {
        List<VendorData> vendorDataList = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            VendorData data = readContent(reader);
            if (data != null) {
                vendorDataList.add(data);
            }
        }
        reader.endArray();

        return vendorDataList;
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
