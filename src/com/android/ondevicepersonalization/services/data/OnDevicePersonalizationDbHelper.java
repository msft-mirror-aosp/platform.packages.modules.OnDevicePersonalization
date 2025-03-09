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

import android.annotation.Nullable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.odp.module.common.data.OdpSQLiteOpenHelper;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.data.events.EventStateContract;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.user.UserDataContract;
import com.android.ondevicepersonalization.services.data.vendor.VendorSettingsContract;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;

import java.util.List;

/** Helper to manage the OnDevicePersonalization database. */
public class OnDevicePersonalizationDbHelper extends OdpSQLiteOpenHelper {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationDbHelper";

    public static final int DATABASE_VERSION = 5;
    private static final String DATABASE_NAME = "ondevicepersonalization.db";

    private static volatile OnDevicePersonalizationDbHelper sSingleton = null;

    private OnDevicePersonalizationDbHelper(Context context, String dbName) {
        super(context, dbName, null, DATABASE_VERSION);
    }

    /** Returns an instance of the OnDevicePersonalizationDbHelper given a context. */
    public static OnDevicePersonalizationDbHelper getInstance(Context context) {
        if (sSingleton == null) {
            synchronized (OnDevicePersonalizationDbHelper.class) {
                if (sSingleton == null) {
                    sSingleton =
                            new OnDevicePersonalizationDbHelper(
                                    context.getApplicationContext(), DATABASE_NAME);
                }
            }
        }
        return sSingleton;
    }

    /**
     * Returns an instance of the OnDevicePersonalizationDbHelper given a context. This is used for
     * testing only.
     */
    @VisibleForTesting
    public static OnDevicePersonalizationDbHelper getInstanceForTest(Context context) {
        synchronized (OnDevicePersonalizationDbHelper.class) {
            if (sSingleton == null) {
                // Use null database name to make it in-memory
                sSingleton = new OnDevicePersonalizationDbHelper(context, null);
            }
            return sSingleton;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(VendorSettingsContract.VendorSettingsEntry.CREATE_TABLE_STATEMENT);

        // Queries and events tables.
        db.execSQL(QueriesContract.QueriesEntry.CREATE_TABLE_STATEMENT);
        db.execSQL(EventsContract.EventsEntry.CREATE_TABLE_STATEMENT);
        db.execSQL(EventStateContract.EventStateEntry.CREATE_TABLE_STATEMENT);
        db.execSQL(UserDataContract.AppInstall.CREATE_TABLE_STATEMENT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        sLogger.d(TAG + ": DB upgrade from " + oldVersion + " to " + newVersion);

        if (oldVersion < 2) {
            execSqlIgnoreError(db, QueriesContract.QueriesEntry.UPGRADE_V1_TO_V2_STATEMENT);
        }

        if (oldVersion < 3) {
            execSqlIgnoreError(db, QueriesContract.QueriesEntry.UPGRADE_V2_TO_V3_STATEMENT);
        }

        if (oldVersion < 4) {
            execSqlIgnoreError(db, UserDataContract.AppInstall.CREATE_TABLE_STATEMENT);
        }

        if (oldVersion < 5) {
            execSqlIgnoreError(db, EventsContract.EventsEntry.UPGRADE_V4_TO_V5_STATEMENTS);
        }
    }

    private void execSqlIgnoreError(SQLiteDatabase db, List<String> sqls) {
        for (String sql : sqls) {
            execSqlIgnoreError(db, sql);
        }
    }

    private void execSqlIgnoreError(SQLiteDatabase db, String sql) {
        try {
            db.execSQL(sql);
        } catch (Exception e) {
            // Ignore upgrade errors on upgrade after downgrade: the column is already present.
            sLogger.w(TAG + ": error ", e);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        sLogger.d(TAG + ": DB downgrade from " + newVersion + " to " + oldVersion);
        // All data is retained for the package between upgrades and rollbacks. Update the
        // DB version to the oldVersion, but maintain the data and schema from the new Version. It
        // is assumed that the new version will be fully backward compatible.
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
        db.enableWriteAheadLogging();
    }

    /** Wraps getWritableDatabase to catch SQLiteException and log error. */
    @Override
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            sLogger.e(e, TAG + ": Failed to get a writeable database");
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP);
            return null;
        }
    }

    /** Wraps getReadableDatabase to catch SQLiteException and log error. */
    @Override
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            sLogger.e(e, TAG + ": Failed to get a readable database");
            ClientErrorLogger.getInstance()
                    .logErrorWithExceptionInfo(
                            e,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP);
            return null;
        }
    }
}
