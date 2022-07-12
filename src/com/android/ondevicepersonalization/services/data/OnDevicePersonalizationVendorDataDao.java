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

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

/**
 * Dao used to manage access to vendor data tables
 */
public class OnDevicePersonalizationVendorDataDao {
    private static final String TAG = "OnDevicePersonalizationVendorDataDao";

    private static final Map<String, OnDevicePersonalizationVendorDataDao> sVendorDataDaos =
            new HashMap<>();
    private final OnDevicePersonalizationDbHelper mDbHelper;
    private final String mTableName;

    private OnDevicePersonalizationVendorDataDao(OnDevicePersonalizationDbHelper dbHelper,
            String tableName) {
        this.mDbHelper = dbHelper;
        this.mTableName = tableName;
    }

    /**
     * Returns an instance of the OnDevicePersonalizationVendorDataDao given a context.
     *
     * @param context    The context of the application
     * @param owner      Name of package that owns the table
     * @param certDigest Hash of the certificate used to sign the package
     * @return Instance of OnDevicePersonalizationVendorDataDao for accessing the requested
     * package's table
     */
    public static OnDevicePersonalizationVendorDataDao getInstance(Context context, String owner,
            String certDigest) {
        synchronized (OnDevicePersonalizationVendorDataDao.class) {
            // TODO: Validate the owner and certDigest
            String tableName = "vendordata_" + owner + "_" + certDigest;
            OnDevicePersonalizationVendorDataDao instance = sVendorDataDaos.get(tableName);
            if (instance == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstance(context);
                createTableIfNotExists(tableName, dbHelper);
                instance = new OnDevicePersonalizationVendorDataDao(
                        dbHelper, tableName);
                sVendorDataDaos.put(tableName, instance);
            }
            return instance;
        }
    }

    /**
     * Returns an instance of the OnDevicePersonalizationVendorDataDao given a context. This is used
     * for testing only
     */
    @VisibleForTesting
    public static OnDevicePersonalizationVendorDataDao getInstanceForTest(Context context,
            String owner, String certDigest) {
        synchronized (OnDevicePersonalizationVendorDataDao.class) {
            String tableName = "vendordata_" + owner + "_" + certDigest;
            OnDevicePersonalizationVendorDataDao instance = sVendorDataDaos.get(tableName);
            if (instance == null) {
                OnDevicePersonalizationDbHelper dbHelper =
                        OnDevicePersonalizationDbHelper.getInstanceForTest(context);
                createTableIfNotExists(tableName, dbHelper);
                instance = new OnDevicePersonalizationVendorDataDao(
                        dbHelper, tableName);
                sVendorDataDaos.put(tableName, instance);
            }
            return instance;
        }
    }

    private static void createTableIfNotExists(String tableName,
            OnDevicePersonalizationDbHelper dbHelper) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL(VendorDataContract.VendorDataEntry.getCreateTableIfNotExistsStatement(
                    tableName));
        } catch (SQLException e) {
            Log.e(TAG, "Failed to create table: " + tableName, e);
        }
    }

    /**
     * Updates the given vendor data row, adds it, if it doesn't already exist.
     *
     * @return true if the update/insert succeeded, false otherwise
     */
    public boolean updateOrInsertVendorData(String key, byte[] data, String fp) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(VendorDataContract.VendorDataEntry.KEY, key);
            values.put(VendorDataContract.VendorDataEntry.DATA, data);
            values.put(VendorDataContract.VendorDataEntry.FP, fp);
            return db.insertWithOnConflict(mTableName, null,
                    values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to update or insert buyer data", e);
        }
        return false;
    }
}
