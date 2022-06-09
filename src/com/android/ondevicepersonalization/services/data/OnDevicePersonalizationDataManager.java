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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Manager used to manage vendor data
 */
public class OnDevicePersonalizationDataManager {
    private static final String TAG = "OnDevicePersonalizationDbManager";

    private static OnDevicePersonalizationDataManager sDbManager;
    private final OnDevicePersonalizationDbHelper mDbHelper;

    private OnDevicePersonalizationDataManager(OnDevicePersonalizationDbHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    /** Returns an instance of the OnDevicePersonalizationDataManager given a context. */
    public static OnDevicePersonalizationDataManager getInstance(Context context) {
        synchronized (OnDevicePersonalizationDataManager.class) {
            if (sDbManager == null) {
                sDbManager = new OnDevicePersonalizationDataManager(
                        OnDevicePersonalizationDbHelper.getInstance(context));
            }
            return sDbManager;
        }
    }

    /**
     * Returns an instance of the OnDevicePersonalizationDataManager given a context. This is used
     * for testing only
     */
    @VisibleForTesting
    public static OnDevicePersonalizationDataManager getInstanceForTest(Context context) {
        synchronized (OnDevicePersonalizationDataManager.class) {
            if (sDbManager == null) {
                sDbManager = new OnDevicePersonalizationDataManager(
                        OnDevicePersonalizationDbHelper.getInstanceForTest(context));
            }
            return sDbManager;
        }
    }

    /**
     * Updates the given buyer data row, adds it, if it doesn't already exist.
     *
     * @return true if the update/insert succeeded, false otherwise
     */
    public boolean updateOrInsertBuyerData(String owner, String certificate,
            String key, byte[] data, String fp) {
        try {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(BuyerDataContract.BuyerDataEntry.OWNER, owner);
            values.put(BuyerDataContract.BuyerDataEntry.CERTIFICATE, certificate);
            values.put(BuyerDataContract.BuyerDataEntry.KEY, key);
            values.put(BuyerDataContract.BuyerDataEntry.DATA, data);
            values.put(BuyerDataContract.BuyerDataEntry.FP, fp);
            return db.insertWithOnConflict(BuyerDataContract.BuyerDataEntry.TABLE_NAME, null,
                    values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to update or insert buyer data", e);
        }
        return false;
    }
}
