/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data.user;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.fbs.AppInfo;
import com.android.ondevicepersonalization.services.fbs.AppInfoList;

import com.google.common.primitives.Ints;
import com.google.flatbuffers.FlatBufferBuilder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserDataDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = UserDataDao.class.getSimpleName();
    private static volatile UserDataDao sSingleton;
    private final OnDevicePersonalizationDbHelper mDbHelper;
    private final Clock mClock;

    private UserDataDao(@NonNull OnDevicePersonalizationDbHelper dbHelper, Clock clock) {
        this.mDbHelper = dbHelper;
        this.mClock = clock;
    }

    /** Returns an instance of the EventsDao given a context. */
    public static UserDataDao getInstance(@NonNull Context context) {
        if (sSingleton == null) {
            synchronized (UserDataDao.class) {
                if (sSingleton == null) {
                    OnDevicePersonalizationDbHelper dbHelper =
                            OnDevicePersonalizationDbHelper.getInstance(context);
                    sSingleton = new UserDataDao(dbHelper, MonotonicClock.getInstance());
                }
            }
        }
        return sSingleton;
    }

    /** Returns an instance of the EventsDao given a context. This is used for testing only. */
    @VisibleForTesting
    public static UserDataDao getInstanceForTest(@NonNull Context context, Clock clock) {
        synchronized (UserDataDao.class) {
            if (sSingleton == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstanceForTest(context);
                sSingleton = new UserDataDao(dbHelper, clock);
            }
            return sSingleton;
        }
    }

    /** Returns an instance of the EventsDao given a context. This is used for testing only. */
    @VisibleForTesting
    public static UserDataDao getInstanceForTest(@NonNull Context context) {
        synchronized (UserDataDao.class) {
            if (sSingleton == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstanceForTest(context);
                sSingleton = new UserDataDao(dbHelper, MonotonicClock.getInstance());
            }
            return sSingleton;
        }
    }

    /** Inserts or replaces an entry in AppInstall table. */
    public boolean insertAppInstall(Map<String, Long> appInstallList, float noise) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        if (noise < 0) {
            return false;
        }
        byte[] appInfoList = convertAppInstallFromMapToBytes(appInstallList, noise);
        ContentValues values = new ContentValues();
        values.put(UserDataContract.AppInstall.APP_LIST, appInfoList);
        values.put(UserDataContract.AppInstall.CREATION_TIME, mClock.currentTimeMillis());
        int is_noised = noise > 0 ? 1 : 0;
        values.put(UserDataContract.AppInstall.IS_NOISED, is_noised);
        long jobId =
                db.insertWithOnConflict(
                        UserDataContract.AppInstall.TABLE_NAME,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE);
        return jobId != -1;
    }

    List<Map<String, Long>> readAppInstall(String[] selectionArgs, String selection) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        String[] projection = {UserDataContract.AppInstall.APP_LIST};
        String orderBy = UserDataContract.AppInstall.CREATION_TIME + " DESC";
        List<Map<String, Long>> appInstallList = new ArrayList<>();
        try (Cursor cursor =
                db.query(
                        UserDataContract.AppInstall.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ orderBy)) {
            while (cursor.moveToNext()) {
                byte[] blob =
                        cursor.getBlob(
                                cursor.getColumnIndexOrThrow(UserDataContract.AppInstall.APP_LIST));
                Map<String, Long> appInstall = convertAppInstallFromBytesToMap(blob);
                appInstallList.add(appInstall);
            }
        }
        return appInstallList;
    }

    /**
     * Gets the app installed map .
     *
     * @param noise if true, return the app install list applied randomized response noise. If
     *     false, return the accurate app install list.
     */
    @NonNull
    public Map<String, Long> getAppInstallMap(boolean noise) {
        String selection = UserDataContract.AppInstall.IS_NOISED + " = ?";
        String[] selectionArgs = {String.valueOf(noise ? 1 : 0)};
        List<Map<String, Long>> appInstallList = readAppInstall(selectionArgs, selection);
        if (appInstallList.isEmpty()) {
            sLogger.d(TAG + ": Can't find app install map for noise " + noise);
            return new HashMap<>();
        }
        return appInstallList.get(0);
    }

    /** Deletes all entries in AppInstall table. */
    public boolean deleteAllAppInstallTable() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        boolean success = false;
        db.beginTransaction();
        try {
            db.delete(UserDataContract.AppInstall.TABLE_NAME, null, null);
            success = true;
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            // TODO(b/337481657): add logging for db failure.
            sLogger.e(e, TAG + ": Failed to perform delete all on AppInstall table.");
        } finally {
            db.endTransaction();
        }
        return success;
    }

    /**
     * @return the training constraints that should apply to this task.
     */
    public final AppInfoList getAppInfoList(byte[] blob) {
        return AppInfoList.getRootAsAppInfoList(ByteBuffer.wrap(blob));
    }

    private byte[] convertAppInstallFromMapToBytes(Map<String, Long> appInstallMap, float noise) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        ArrayList<Integer> entryOffsets = new ArrayList<>();
        int offset = 0;
        for (String packageName : appInstallMap.keySet()) {
            long updateTime = appInstallMap.get(packageName);
            offset = builder.createString(packageName);
            offset = AppInfo.createAppInfo(builder, offset, updateTime);
            entryOffsets.add(offset);
        }
        offset = AppInfoList.createAppInfoListVector(builder, Ints.toArray(entryOffsets));
        AppInfoList.startAppInfoList(builder);
        AppInfoList.addAppInfoList(builder, offset);
        AppInfoList.addNoise(builder, noise);
        offset = AppInfoList.endAppInfoList(builder);
        builder.finish(offset);
        return builder.sizedByteArray();
    }

    private Map<String, Long> convertAppInstallFromBytesToMap(byte[] blob) {
        HashMap<String, Long> appInstallMap = new HashMap<>();
        AppInfoList appInfoList = getAppInfoList(blob);
        for (int i = 0; i < appInfoList.appInfoListLength(); i++) {
            AppInfo appInfo = appInfoList.appInfoList(i);
            appInstallMap.put(appInfo.name(), appInfo.updateTime());
        }
        return appInstallMap;
    }
}
