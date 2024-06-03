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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.data.events.EventStateContract;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.user.UserDataContract;
import com.android.ondevicepersonalization.services.data.vendor.VendorSettingsContract;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@MockStatic(FlagsFactory.class)
@MockStatic(ClientErrorLogger.class)
public final class OnDevicePersonalizationDbHelperTest {
    private Context mContext;
    private OnDevicePersonalizationDbHelper mDbHelper;
    private SQLiteDatabase mDb;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Mock private Flags mMockFlags;
    @Mock private ClientErrorLogger mMockClientErrorLogger;

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

    public static final String CREATE_EVENTS_V4_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + EventsContract.EventsEntry.TABLE_NAME + " ("
                    + EventsContract.EventsEntry.EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + EventsContract.EventsEntry.QUERY_ID + " INTEGER NOT NULL,"
                    + EventsContract.EventsEntry.ROW_INDEX + " INTEGER NOT NULL,"
                    + EventsContract.EventsEntry.SERVICE_NAME + " TEXT NOT NULL,"
                    + EventsContract.EventsEntry.TYPE + " INTEGER NOT NULL,"
                    + EventsContract.EventsEntry.TIME_MILLIS + " INTEGER NOT NULL,"
                    + EventsContract.EventsEntry.EVENT_DATA + " BLOB NOT NULL,"
                    + "FOREIGN KEY(" + EventsContract.EventsEntry.QUERY_ID + ") REFERENCES "
                        + QueriesContract.QueriesEntry.TABLE_NAME + "("
                        + QueriesContract.QueriesEntry.QUERY_ID + "),"
                    + "UNIQUE(" + EventsContract.EventsEntry.QUERY_ID + ","
                        + EventsContract.EventsEntry.ROW_INDEX + ","
                        + EventsContract.EventsEntry.SERVICE_NAME + ","
                        + EventsContract.EventsEntry.TYPE + "))";
    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        mDb = mDbHelper.getWritableDatabase();
        when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
        when(ClientErrorLogger.getInstance()).thenReturn(mMockClientErrorLogger);
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
    public void testOnUpgradeFromV4() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            createV4Tables(db);
            mDbHelper.onUpgrade(db, 4, OnDevicePersonalizationDbHelper.DATABASE_VERSION);
            assertTrue(hasEntity(EventsContract.EventsEntry.TABLE_NAME, "table"));
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

    @Test
    public void testSafeGetReadableDatabase_exceptionOccurs_validatesErrorLogging() {
        OnDevicePersonalizationDbHelper dbHelper =
                spy(OnDevicePersonalizationDbHelper.getInstanceForTest(mContext));
        Throwable tr = new SQLiteException();
        doThrow(tr).when(dbHelper).getReadableDatabase();

        SQLiteDatabase db = dbHelper.safeGetReadableDatabase();

        assertThat(db).isNull();
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP));
    }

    @Test
    public void testSafeGetWritableDatabase_exceptionOccurs_validatesErrorLogging() {
        OnDevicePersonalizationDbHelper dbHelper =
                spy(OnDevicePersonalizationDbHelper.getInstanceForTest(mContext));
        Throwable tr = new SQLiteException();
        doThrow(tr).when(dbHelper).getWritableDatabase();

        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();

        assertThat(db).isNull();
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP));
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

    private void createV4Tables(SQLiteDatabase db) {
        db.execSQL(VendorSettingsContract.VendorSettingsEntry.CREATE_TABLE_STATEMENT);

        // Queries and events tables.
        db.execSQL(CREATE_QUERIES_V3_STATEMENT);
        db.execSQL(CREATE_EVENTS_V4_STATEMENT);
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
