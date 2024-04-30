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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.events.EventStateContract;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.user.UserDataContract;
import com.android.ondevicepersonalization.services.data.vendor.VendorSettingsContract;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationDbHelperTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationDbHelper mDbHelper;
    private SQLiteDatabase mDb;
    private static final String CREATE_QUERIES_V1_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + QueriesContract.QueriesEntry.TABLE_NAME + " ("
                + QueriesContract.QueriesEntry.QUERY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + QueriesContract.QueriesEntry.TIME_MILLIS + " INTEGER NOT NULL,"
                + QueriesContract.QueriesEntry.SERVICE_NAME + " TEXT NOT NULL,"
                + QueriesContract.QueriesEntry.QUERY_DATA + " BLOB NOT NULL)";

    public static final String CREATE_QUERIES_V2_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + QueriesContract.QueriesEntry.TABLE_NAME + " ("
                + QueriesContract.QueriesEntry.QUERY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + QueriesContract.QueriesEntry.TIME_MILLIS + " INTEGER NOT NULL,"
                + QueriesContract.QueriesEntry.APP_PACKAGE_NAME + " TEXT NOT NULL,"
                + QueriesContract.QueriesEntry.SERVICE_NAME + " TEXT NOT NULL,"
                + QueriesContract.QueriesEntry.QUERY_DATA + " BLOB NOT NULL)";

    public static final String CREATE_QUERIES_V3_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + QueriesContract.QueriesEntry.TABLE_NAME + " ("
                    + QueriesContract.QueriesEntry.QUERY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + QueriesContract.QueriesEntry.TIME_MILLIS + " INTEGER NOT NULL,"
                    + QueriesContract.QueriesEntry.APP_PACKAGE_NAME + " TEXT NOT NULL,"
                    + QueriesContract.QueriesEntry.SERVICE_NAME + " TEXT NOT NULL,"
                    + QueriesContract.QueriesEntry.SERVICE_CERT_DIGEST + " TEXT NOT NULL,"
                    + QueriesContract.QueriesEntry.QUERY_DATA + " BLOB NOT NULL)";

    @Before
    public void setup() {
        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        mDb = mDbHelper.getWritableDatabase();
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testOnCreate() {
        mDbHelper.onCreate(mDb);
        assertTrue(hasEntity(VendorSettingsContract.VendorSettingsEntry.TABLE_NAME, "table"));
        assertTrue(hasEntity(QueriesContract.QueriesEntry.TABLE_NAME, "table"));
        assertTrue(hasEntity(EventsContract.EventsEntry.TABLE_NAME, "table"));
        assertTrue(hasEntity(EventStateContract.EventStateEntry.TABLE_NAME, "table"));
    }

    @Test
    public void testOnUpgradeFromV1() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            createV1Tables(db);
            mDbHelper.onUpgrade(db, 1, OnDevicePersonalizationDbHelper.DATABASE_VERSION);
            List<String> columns = getColumns(db, QueriesContract.QueriesEntry.TABLE_NAME);
            assertTrue(
                    "Column not found " + columns.toString(),
                    columns.contains(QueriesContract.QueriesEntry.APP_PACKAGE_NAME));
            assertTrue(
                    "Column not found " + columns.toString(),
                    columns.contains(QueriesContract.QueriesEntry.SERVICE_CERT_DIGEST));
            assertTrue(hasEntity(UserDataContract.AppInstall.TABLE_NAME, "table"));
        } finally {
            db.close();
        }
    }

    @Test
    public void testOnUpgradeFromV2() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            createV2Tables(db);
            mDbHelper.onUpgrade(db, 2, OnDevicePersonalizationDbHelper.DATABASE_VERSION);
            List<String> columns = getColumns(db, QueriesContract.QueriesEntry.TABLE_NAME);
            assertTrue(
                    "Column not found " + columns.toString(),
                    columns.contains(QueriesContract.QueriesEntry.SERVICE_CERT_DIGEST));
            assertTrue(hasEntity(UserDataContract.AppInstall.TABLE_NAME, "table"));
        } finally {
            db.close();
        }
    }

    @Test
    public void testOnUpgradeFromV3() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            createV3Tables(db);
            mDbHelper.onUpgrade(db, 3, OnDevicePersonalizationDbHelper.DATABASE_VERSION);
            assertTrue(hasEntity(UserDataContract.AppInstall.TABLE_NAME, "table"));
            List<String> columns = getColumns(db, UserDataContract.AppInstall.TABLE_NAME);
            assertTrue(
                    "Column not found " + columns.toString(),
                    columns.contains(UserDataContract.AppInstall.APP_LIST));
        } finally {
            db.close();
        }
    }

    @Test
    public void testGetInstance() {
        OnDevicePersonalizationDbHelper instance1 =
                OnDevicePersonalizationDbHelper.getInstance(mContext);
        OnDevicePersonalizationDbHelper instance2 =
                OnDevicePersonalizationDbHelper.getInstance(mContext);
        assertEquals(instance1, instance2);
    }

    private void createV1Tables(SQLiteDatabase db) {
        db.execSQL(VendorSettingsContract.VendorSettingsEntry.CREATE_TABLE_STATEMENT);

        // Queries and events tables.
        db.execSQL(CREATE_QUERIES_V1_STATEMENT);
        db.execSQL(EventsContract.EventsEntry.CREATE_TABLE_STATEMENT);
        db.execSQL(EventStateContract.EventStateEntry.CREATE_TABLE_STATEMENT);
    }

    private void createV2Tables(SQLiteDatabase db) {
        db.execSQL(VendorSettingsContract.VendorSettingsEntry.CREATE_TABLE_STATEMENT);

        // Queries and events tables.
        db.execSQL(CREATE_QUERIES_V2_STATEMENT);
        db.execSQL(EventsContract.EventsEntry.CREATE_TABLE_STATEMENT);
        db.execSQL(EventStateContract.EventStateEntry.CREATE_TABLE_STATEMENT);
    }

    private void createV3Tables(SQLiteDatabase db) {
        db.execSQL(VendorSettingsContract.VendorSettingsEntry.CREATE_TABLE_STATEMENT);

        // Queries and events tables.
        db.execSQL(CREATE_QUERIES_V3_STATEMENT);
        db.execSQL(EventsContract.EventsEntry.CREATE_TABLE_STATEMENT);
        db.execSQL(EventStateContract.EventStateEntry.CREATE_TABLE_STATEMENT);
    }

    private List<String> getColumns(SQLiteDatabase db, String tableName) {
        String query = "select * from " + tableName;
        Cursor cursor = db.rawQuery(query, null);
        if (cursor != null) {
            try {
                return Arrays.asList(cursor.getColumnNames());
            } finally {
                cursor.close();
            }
        }
        return Collections.emptyList();
    }

    private boolean hasEntity(String entityName, String type) {
        String query = "select DISTINCT name from sqlite_master where name = '"
                + entityName + "' and type = '" + type + "'";
        Cursor cursor = mDb.rawQuery(query, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }
}
