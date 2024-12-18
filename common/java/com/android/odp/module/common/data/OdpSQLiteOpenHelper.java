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

package com.android.odp.module.common.data;

import android.annotation.Nullable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public abstract class OdpSQLiteOpenHelper extends SQLiteOpenHelper {

    public OdpSQLiteOpenHelper(
            @Nullable Context context,
            @Nullable String name,
            @Nullable SQLiteDatabase.CursorFactory factory,
            int version) {
        super(context, name, factory, version);
    }

    /**
     * Wraps {@link SQLiteOpenHelper#getReadableDatabase()} to catch {@code SQLiteException} and log
     * error.
     */
    @Nullable
    public abstract SQLiteDatabase safeGetReadableDatabase();

    /**
     * Wraps {@link SQLiteOpenHelper#getReadableDatabase()} to catch {@code SQLiteException} and log
     * error.
     */
    @Nullable
    public abstract SQLiteDatabase safeGetWritableDatabase();
}
