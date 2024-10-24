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

package com.android.federatedcompute.services.data;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.federatedcompute.services.data.FederatedTraningTaskContract.FEDERATED_TRAINING_TASKS_TABLE;
import static com.android.federatedcompute.services.data.ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE;
import static com.android.odp.module.common.encryption.OdpEncryptionKeyContract.ENCRYPTION_KEY_TABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.FederatedTraningTaskContract.FederatedTrainingTaskColumns;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@MockStatic(FlagsFactory.class)
@MockStatic(ClientErrorLogger.class)
public final class FederatedComputeDbHelperTest {
    private Context mContext;

    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Mock private Flags mMockFlags;
    @Mock private ClientErrorLogger mMockClientErrorLogger;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
        when(ClientErrorLogger.getInstance()).thenReturn(mMockClientErrorLogger);
    }

    @After
    public void cleanUp() throws Exception {
        FederatedComputeDbHelper.resetInstance();
    }

    @Test
    public void onCreate() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        assertThat(db).isNotNull();
        assertThat(DatabaseUtils.queryNumEntries(db, FEDERATED_TRAINING_TASKS_TABLE)).isEqualTo(0);
        assertThat(DatabaseUtils.queryNumEntries(db, ENCRYPTION_KEY_TABLE)).isEqualTo(0);
        assertThat(DatabaseUtils.queryNumEntries(db, ODP_AUTHORIZATION_TOKEN_TABLE)).isEqualTo(0);

        // query number of tables
        ArrayList<String> tableNames = new ArrayList<String>();
        Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                tableNames.add(cursor.getString(cursor.getColumnIndex("name")));
                cursor.moveToNext();
            }
        }
        String[] expectedTables = {
            FEDERATED_TRAINING_TASKS_TABLE, ENCRYPTION_KEY_TABLE, ODP_AUTHORIZATION_TOKEN_TABLE
        };
        // android metadata table also exists in the database
        assertThat(tableNames).containsAtLeastElementsIn(expectedTables);
    }

    @Test
    public void testSafeGetReadableDatabase_exceptionOccurs_validatesErrorLogging() {
        FederatedComputeDbHelper dbHelper =
                spy(FederatedComputeDbHelper.getInstanceForTest(mContext));
        Throwable tr = new SQLiteException();
        doThrow(tr).when(dbHelper).getReadableDatabase();

        SQLiteDatabase db = dbHelper.safeGetReadableDatabase();

        assertThat(db).isNull();
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void testSafeGetWriteDatabase_exceptionOccurs_validatesErrorLogging() {
        FederatedComputeDbHelper dbHelper =
                spy(FederatedComputeDbHelper.getInstanceForTest(mContext));
        Throwable tr = new SQLiteException();
        doThrow(tr).when(dbHelper).getWritableDatabase();

        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();

        assertThat(db).isNull();
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void testOnUpgrade_fromVersion1To2_migratesDataAndCreatesIndex() {
        // 1. Create a database file directly with version 1 and the old schema
        File dbFile = new File(mContext.getFilesDir(), "test_federatedcompute.db");
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        db.setVersion(1);
        db.execSQL(
                "CREATE TABLE "
                        + FEDERATED_TRAINING_TASKS_TABLE
                        + " ( "
                        + FederatedTrainingTaskColumns._ID
                        + " INTEGER PRIMARY KEY, "
                        + FederatedTrainingTaskColumns.APP_PACKAGE_NAME
                        + " TEXT NOT NULL, "
                        + FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID
                        + " INTEGER, "
                        + FederatedTrainingTaskColumns.OWNER_ID
                        + " TEXT NOT NULL, "
                        + FederatedTrainingTaskColumns.OWNER_ID_CERT_DIGEST
                        + " TEXT NOT NULL, "
                        + FederatedTrainingTaskColumns.POPULATION_NAME
                        + " TEXT NOT NULL,"
                        + FederatedTrainingTaskColumns.SERVER_ADDRESS
                        + " TEXT NOT NULL,"
                        + FederatedTrainingTaskColumns.INTERVAL_OPTIONS
                        + " BLOB, "
                        + FederatedTrainingTaskColumns.CONTEXT_DATA
                        + " BLOB, "
                        + FederatedTrainingTaskColumns.CREATION_TIME
                        + " INTEGER NOT NULL, "
                        + FederatedTrainingTaskColumns.LAST_SCHEDULED_TIME
                        + " INTEGER, "
                        + FederatedTrainingTaskColumns.LAST_RUN_START_TIME
                        + " INTEGER, "
                        + FederatedTrainingTaskColumns.LAST_RUN_END_TIME
                        + " INTEGER, "
                        + FederatedTrainingTaskColumns.EARLIEST_NEXT_RUN_TIME
                        + " INTEGER NOT NULL, "
                        + FederatedTrainingTaskColumns.CONSTRAINTS
                        + " BLOB, "
                        + FederatedTrainingTaskColumns.SCHEDULING_REASON
                        + " INTEGER, "
                        + FederatedTrainingTaskColumns.RESCHEDULE_COUNT
                        + " INTEGER, "
                        + "UNIQUE("
                        + FederatedTrainingTaskColumns.JOB_SCHEDULER_JOB_ID
                        + "))"); // Old schema
        ContentValues values = new ContentValues();
        values.put(FederatedTrainingTaskColumns.OWNER_ID, "com.example.app/ExampleTask");
        values.put(FederatedTrainingTaskColumns.APP_PACKAGE_NAME, "com.example.app");
        values.put(FederatedTrainingTaskColumns.OWNER_ID_CERT_DIGEST, "123CERT");
        values.put(FederatedTrainingTaskColumns.POPULATION_NAME, "popName");
        values.put(FederatedTrainingTaskColumns.SERVER_ADDRESS, "server.com");
        values.put(FederatedTrainingTaskColumns.CREATION_TIME, 123);
        values.put(FederatedTrainingTaskColumns.EARLIEST_NEXT_RUN_TIME, 123);
        long taskDbId = db.insertOrThrow(FEDERATED_TRAINING_TASKS_TABLE, "", values);
        assertThat(taskDbId).isNotEqualTo(-1L);
        db.close();

        // 2. Get an instance of the helper, which should trigger the upgrade
        FederatedComputeDbHelper dbHelper =
                FederatedComputeDbHelper.getNonSingletonInstanceForTest(mContext, db.getPath());
        db = dbHelper.getWritableDatabase();

        // 3. Verify that the new columns exist and the data is migrated
        Cursor cursor =
                db.query(FEDERATED_TRAINING_TASKS_TABLE, null, null, null, null, null, null);
        assertThat(cursor.moveToFirst()).isTrue();
        assertThat(cursor.getColumnIndex(FederatedTrainingTaskColumns.OWNER_PACKAGE))
                .isNotEqualTo(-1);
        assertThat(cursor.getColumnIndex(FederatedTrainingTaskColumns.OWNER_CLASS))
                .isNotEqualTo(-1);
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(FederatedTrainingTaskColumns.OWNER_PACKAGE)))
                .isEqualTo("com.example.app");
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(FederatedTrainingTaskColumns.OWNER_CLASS)))
                .isEqualTo("ExampleTask");
        cursor.close();

        // 4. Verify that the index is created
        Cursor indexCursor =
                db.rawQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='index'"
                                + " AND name='idx_package_name'",
                        null);
        assertThat(indexCursor.moveToFirst()).isTrue();
        assertThat(indexCursor.getInt(0)).isEqualTo(1); // Index should exist
        indexCursor.close();

        // 5. Clean up (delete the test database file)
        db.close();
        dbFile.delete();
    }
}
