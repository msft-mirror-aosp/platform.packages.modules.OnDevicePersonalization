/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.odp.module.common.data;

import static com.android.odp.module.common.encryption.OdpEncryptionKeyContract.ENCRYPTION_KEY_TABLE;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.encryption.OdpEncryptionKey;
import com.android.odp.module.common.encryption.OdpEncryptionKeyContract.OdpEncryptionColumns;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/** DAO for accessing encryption key table that stores {@link OdpEncryptionKey}s. */
public class OdpEncryptionKeyDao {
    private static final String TAG = OdpEncryptionKeyDao.class.getSimpleName();

    private final OdpSQLiteOpenHelper mDbHelper;

    private final Clock mClock;

    private static volatile OdpEncryptionKeyDao sSingletonInstance;

    private OdpEncryptionKeyDao(OdpSQLiteOpenHelper dbHelper, Clock clock) {
        mDbHelper = dbHelper;
        mClock = clock;
    }

    /** Returns an instance of {@link OdpEncryptionKeyDao} given a context. */
    @NonNull
    public static OdpEncryptionKeyDao getInstance(Context context, OdpSQLiteOpenHelper dbHelper) {
        if (sSingletonInstance == null) {
            synchronized (OdpEncryptionKeyDao.class) {
                if (sSingletonInstance == null) {
                    sSingletonInstance =
                            new OdpEncryptionKeyDao(dbHelper, MonotonicClock.getInstance());
                }
            }
        }
        return sSingletonInstance;
    }

    /**
     * Insert a key to the encryption_key table.
     *
     * @param key the {@link OdpEncryptionKey} to insert into DB.
     * @return Whether the key was inserted successfully.
     */
    public boolean insertEncryptionKey(OdpEncryptionKey key) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }

        ContentValues values = new ContentValues();
        values.put(OdpEncryptionColumns.KEY_IDENTIFIER, key.getKeyIdentifier());
        values.put(OdpEncryptionColumns.PUBLIC_KEY, key.getPublicKey());
        values.put(OdpEncryptionColumns.KEY_TYPE, key.getKeyType());
        values.put(OdpEncryptionColumns.CREATION_TIME, key.getCreationTime());
        values.put(OdpEncryptionColumns.EXPIRY_TIME, key.getExpiryTime());

        long insertedRowId =
                db.insertWithOnConflict(
                        ENCRYPTION_KEY_TABLE, "", values, SQLiteDatabase.CONFLICT_REPLACE);
        return insertedRowId != -1;
    }

    /**
     * Read from encryption key table given selection, order and limit conditions.
     *
     * @return a list of matching {@link OdpEncryptionKey}s.
     */
    @VisibleForTesting
    public List<OdpEncryptionKey> readEncryptionKeysFromDatabase(
            String selection, String[] selectionArgs, String orderBy, int count) {
        List<OdpEncryptionKey> keyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }

        String[] selectColumns = {
            OdpEncryptionColumns.KEY_IDENTIFIER,
            OdpEncryptionColumns.PUBLIC_KEY,
            OdpEncryptionColumns.KEY_TYPE,
            OdpEncryptionColumns.CREATION_TIME,
            OdpEncryptionColumns.EXPIRY_TIME
        };

        Cursor cursor = null;
        try {
            cursor =
                    db.query(
                            ENCRYPTION_KEY_TABLE,
                            selectColumns,
                            selection,
                            selectionArgs,
                            /* groupBy= */ null,
                            /* having= */ null,
                            /* orderBy= */ orderBy,
                            /* limit= */ String.valueOf(count));
            while (cursor.moveToNext()) {
                OdpEncryptionKey.Builder encryptionKeyBuilder =
                        new OdpEncryptionKey.Builder()
                                .setKeyIdentifier(
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                        OdpEncryptionColumns.KEY_IDENTIFIER)))
                                .setPublicKey(
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow(
                                                        OdpEncryptionColumns.PUBLIC_KEY)))
                                .setKeyType(
                                        cursor.getInt(
                                                cursor.getColumnIndexOrThrow(
                                                        OdpEncryptionColumns.KEY_TYPE)))
                                .setCreationTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        OdpEncryptionColumns.CREATION_TIME)))
                                .setExpiryTime(
                                        cursor.getLong(
                                                cursor.getColumnIndexOrThrow(
                                                        OdpEncryptionColumns.EXPIRY_TIME)));
                keyList.add(encryptionKeyBuilder.build());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return keyList;
    }

    /**
     * @return latest expired keys (order by expiry time).
     */
    public List<OdpEncryptionKey> getLatestExpiryNKeys(int count) {
        String selection = OdpEncryptionColumns.EXPIRY_TIME + " > ?";
        String[] selectionArgs = {String.valueOf(mClock.currentTimeMillis())};
        // reverse order of expiry time
        String orderBy = OdpEncryptionColumns.EXPIRY_TIME + " DESC";
        return readEncryptionKeysFromDatabase(selection, selectionArgs, orderBy, count);
    }

    /**
     * Delete expired keys.
     *
     * @return number of keys deleted.
     */
    public int deleteExpiredKeys() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }
        String whereClause = OdpEncryptionColumns.EXPIRY_TIME + " < ?";
        String[] whereArgs = {String.valueOf(mClock.currentTimeMillis())};
        int deletedRows = db.delete(ENCRYPTION_KEY_TABLE, whereClause, whereArgs);
        LogUtil.d(TAG, "Deleted %s expired keys from database", deletedRows);
        return deletedRows;
    }

    /** Test only method to clear the database of all keys, independent of expiry time etc. */
    @VisibleForTesting
    public int deleteAllKeys() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            throw new SQLiteException(TAG + ": Failed to open database.");
        }
        int deletedRows =
                db.delete(ENCRYPTION_KEY_TABLE, /* whereClause= */ null, /* whereArgs= */ null);
        LogUtil.d(TAG, "Force deleted %s keys from database", deletedRows);
        return deletedRows;
    }
}
