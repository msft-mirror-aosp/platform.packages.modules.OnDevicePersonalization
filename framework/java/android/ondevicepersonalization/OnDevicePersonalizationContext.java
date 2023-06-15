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

import android.annotation.NonNull;

/**
 * Container for per-request state and APIs for code that runs in the isolated process.
 *
 * @hide
 */
public interface OnDevicePersonalizationContext {
    /**
     * Returns a DAO for the REMOTE_DATA table.
     * @return A {@link KeyValueStore} object that provides access to the REMOTE_DATA table.
     */
    @NonNull KeyValueStore getRemoteData();

    /**
     * Returns a DAO for the LOCAL_DATA table.
     * @return A {@link MutableKeyValueStore} object that provides access to the LOCAL_DATA table.
     */
    @NonNull MutableKeyValueStore getLocalData();

    /** Returns an {@link EventUrlProvider} for the current request. */
    @NonNull EventUrlProvider getEventUrlProvider();
}
