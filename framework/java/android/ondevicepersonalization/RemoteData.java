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

package android.ondevicepersonalization;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.database.Cursor;
import android.os.OutcomeReceiver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Data Access Object for the REMOTE_DATA table. The REMOTE_DATA table is a read-only
 * data store that contains data that has been downloaded by the ODP platform from
 * the vendor endpoint that is declared in the package manifest.
 *
 * @hide
 */
public interface RemoteData {
    /**
     * Looks up a list of keys in the REMOTE_DATA table.
     *
     * @param keys A list of keys to look up.
     * @param executor The executor to run the result callback on.
     * @param receiver An {@link OutcomeReceiver} for a key->value map. Keys that are
     * missing in the table are not present in the map.
     */
    void lookup(
            @NonNull List<String> keys,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Map<String, byte[]>, Exception> receiver);

    /**
     * Iterates through all rows in the REMOTE_DATA table.
     *
     * @param executor The executor to run the result callback on.
     * @param receiver An {@link OutcomeReceiver} for a {@link Cursor} that provides
     * access to the table contents.
     */
    void scan(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Cursor, Exception> receiver);
}
