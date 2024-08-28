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

package com.android.ondevicepersonalization.services.data.errors;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dao used to manage access to per vendor aggregated error codes that are returned by {@link
 * android.adservices.ondevicepersonalization.IsolatedService} implementations.
 */
public class OnDevicePersonalizationAggregatedErrorDataDao {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG =
            OnDevicePersonalizationAggregatedErrorDataDao.class.getSimpleName();
    private static final String ERROR_DATA_TABLE_NAME_PREFIX = "errordata";

    @VisibleForTesting static final int MAX_ALLOWED_ERROR_CODE = 32;

    private static final Map<String, OnDevicePersonalizationAggregatedErrorDataDao>
            sVendorDataDaos = new ConcurrentHashMap<>();
    private final OnDevicePersonalizationDbHelper mDbHelper;
    private final ComponentName mOwner;
    private final String mCertDigest;
    private final String mTableName;
    private final long mPackageVersion;

    private OnDevicePersonalizationAggregatedErrorDataDao(
            OnDevicePersonalizationDbHelper dbHelper,
            ComponentName owner,
            String certDigest,
            long packageVersion) {
        this.mDbHelper = dbHelper;
        this.mOwner = owner;
        this.mCertDigest = certDigest;
        this.mTableName = getTableName(owner, certDigest);
        this.mPackageVersion = packageVersion;
    }

    /**
     * Returns an instance of the {@link OnDevicePersonalizationAggregatedErrorDataDao} for a given
     * component and associated cert digest.
     *
     * @param context The context of the application
     * @param owner ComponentName of the package whose errors will be aggregated in the table
     * @param certDigest Hash of the certificate used to sign the package
     * @return Instance of {@link OnDevicePersonalizationAggregatedErrorDataDao} for accessing the
     *     requested components aggregated error table.
     */
    public static OnDevicePersonalizationAggregatedErrorDataDao getInstance(
            Context context, ComponentName owner, String certDigest) {
        String tableName = getTableName(owner, certDigest);
        OnDevicePersonalizationAggregatedErrorDataDao instance = sVendorDataDaos.get(tableName);
        if (instance == null) {
            synchronized (sVendorDataDaos) {
                instance = sVendorDataDaos.get(tableName);
                if (instance == null) {
                    OnDevicePersonalizationDbHelper dbHelper =
                            OnDevicePersonalizationDbHelper.getInstance(context);
                    instance =
                            new OnDevicePersonalizationAggregatedErrorDataDao(
                                    dbHelper, owner, certDigest, getPackageVersion(owner, context));
                    sVendorDataDaos.put(tableName, instance);
                }
            }
        }
        return instance;
    }

    private static long getPackageVersion(ComponentName owner, Context context) {
        long packageVersion = 0;
        try {
            String packageName = owner.getPackageName();
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo(packageName, /* flags= */ 0);
            packageVersion = packageInfo.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException nne) {
            sLogger.e(TAG + ": Unable to find package " + owner.getPackageName(), nne);
        }
        return packageVersion;
    }

    /** Delete the existing aggregate exception data for this package. */
    public boolean deleteExceptionData() {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            if (db.delete(mTableName, /* whereClause= */ "1", /* whereArgs= */ null) <= 0) {
                sLogger.d(TAG + ": zero records deleted for " + mOwner);
                return false;
            }

            db.setTransactionSuccessful();
        } catch (SQLException exception) {
            sLogger.e(TAG + ": failed to delete exception data for " + mOwner, exception);
        } finally {
            db.endTransaction();
        }
        return true;
    }

    /** Get the existing aggregate exception data for this package. */
    public ImmutableList<ErrorData> getExceptionData() {
        ImmutableList.Builder listBuilder = ImmutableList.builder();
        try {
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            try (Cursor cursor =
                    db.query(
                            mTableName,
                            /* columns= */ null,
                            /* selection= */ null,
                            /* selectionArgs= */ null,
                            /* groupBy= */ null,
                            /* having= */ null,
                            /* orderBy= */ null)) {
                while (cursor.moveToNext()) {
                    int errorCount =
                            cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                            AggregatedErrorCodesContract.ErrorDataEntry
                                                    .EXCEPTION_COUNT));
                    int errorCode =
                            cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                            AggregatedErrorCodesContract.ErrorDataEntry
                                                    .EXCEPTION_ERROR_CODE));
                    int epochDay =
                            cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                            AggregatedErrorCodesContract.ErrorDataEntry
                                                    .EXCEPTION_DATE));
                    long packageVersion =
                            cursor.getLong(
                                    cursor.getColumnIndexOrThrow(
                                            AggregatedErrorCodesContract.ErrorDataEntry
                                                    .SERVICE_PACKAGE_VERSION));
                    listBuilder.add(
                            new ErrorData.Builder(errorCode, errorCount, epochDay, packageVersion)
                                    .build());
                }
                cursor.close();
                return listBuilder.build();
            }
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to read aggregate exception data for " + mOwner, e);
        }
        return ImmutableList.of();
    }

    /**
     * Add or update the record of exception count for the provided error code.
     *
     * <p>Uses the current date as the date they exception was thrown.
     *
     * @return whether the exception was successfully recorded in the database.
     */
    public boolean addExceptionCount(int isolatedServiceErrorCode, int exceptionCount) {
        if (isolatedServiceErrorCode > MAX_ALLOWED_ERROR_CODE) {
            sLogger.e(
                    TAG
                            + ": failed to record exception "
                            + isolatedServiceErrorCode
                            + " for package "
                            + mOwner.getPackageName());
            return false;
        }

        int epochDay = DateTimeUtils.dayIndexUtc();
        if (epochDay == -1) {
            sLogger.e(
                    TAG
                            + ": failed to get the epoch day, unable to add exception for package "
                            + mOwner.getPackageName());
            return false;
        }

        int existingExceptionCount = getExceptionCount(isolatedServiceErrorCode, epochDay);
        if (!createTableIfNotExists(mTableName)) {
            sLogger.e(TAG + ": failed to create table " + mTableName);
            return false;
        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            if (!insertErrorData(
                    new ErrorData.Builder(
                                    isolatedServiceErrorCode,
                                    existingExceptionCount + exceptionCount,
                                    epochDay,
                                    mPackageVersion)
                            .build())) {
                sLogger.e(TAG + ": failed to insert error data " + mTableName);
                return false;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return true;
    }

    /**
     * Updates the given vendor data row, adds it if it doesn't already exist.
     *
     * @return true if the update/insert succeeded, false otherwise
     */
    private boolean insertErrorData(ErrorData errorData) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(
                    AggregatedErrorCodesContract.ErrorDataEntry.EXCEPTION_ERROR_CODE,
                    errorData.getErrorCode());
            values.put(
                    AggregatedErrorCodesContract.ErrorDataEntry.EXCEPTION_DATE,
                    errorData.getEpochDay());
            values.put(
                    AggregatedErrorCodesContract.ErrorDataEntry.SERVICE_PACKAGE_VERSION,
                    errorData.getServicePackageVersion());
            values.put(
                    AggregatedErrorCodesContract.ErrorDataEntry.EXCEPTION_COUNT,
                    errorData.getErrorCount());
            return db.insertWithOnConflict(
                            mTableName, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    != -1;
        } catch (SQLiteException e) {
            sLogger.e(TAG + ": Failed to update or insert error data. ", e);
        }
        return false;
    }

    @VisibleForTesting
    /** Returns the existing count associated with the given error code on the given day. */
    int getExceptionCount(int isolatedServiceErrorCode, int epochDay) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection =
                AggregatedErrorCodesContract.ErrorDataEntry.EXCEPTION_ERROR_CODE
                        + " = ? AND "
                        + AggregatedErrorCodesContract.ErrorDataEntry.EXCEPTION_DATE
                        + " = ?";
        String[] selectionArgs = {
            String.valueOf(isolatedServiceErrorCode), String.valueOf(epochDay)
        };
        String[] columns = {AggregatedErrorCodesContract.ErrorDataEntry.EXCEPTION_COUNT};
        try (Cursor cursor =
                db.query(
                        mTableName,
                        columns,
                        selection,
                        selectionArgs,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                                AggregatedErrorCodesContract.ErrorDataEntry.EXCEPTION_COUNT));
            }
        } catch (SQLiteException e) {
            sLogger.e(
                    TAG
                            + ": Failed to query existing error counts associated with error-code: "
                            + isolatedServiceErrorCode
                            + " on day: "
                            + epochDay,
                    e);
        }
        // No existing records or encountered exception
        return 0;
    }

    /** Creates table name based on owner and certDigest */
    public static String getTableName(ComponentName owner, String certDigest) {
        return DbUtils.getTableName(ERROR_DATA_TABLE_NAME_PREFIX, owner, certDigest);
    }

    /** Creates file directory name based on table name and base directory */
    public static String getFileDir(String tableName, File baseDir) {
        return baseDir + "/VendorData/" + tableName;
    }

    private boolean createTableIfNotExists(String tableName) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.execSQL(
                    AggregatedErrorCodesContract.ErrorDataEntry.getCreateTableIfNotExistsStatement(
                            tableName));
        } catch (SQLException e) {
            sLogger.e(TAG + ": Failed to create table: " + tableName, e);
            return false;
        }
        sLogger.i(TAG + ": Successfully created table: " + tableName);
        return true;
    }
}
