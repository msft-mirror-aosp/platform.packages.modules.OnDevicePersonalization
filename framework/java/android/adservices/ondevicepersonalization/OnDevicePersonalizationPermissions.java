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

package android.adservices.ondevicepersonalization;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * OnDevicePersonalization permission settings.
 *
 * @hide
*/

public class OnDevicePersonalizationPermissions {
    private OnDevicePersonalizationPermissions() {}

    /**
     * The permission that lets it modify ODP's enablement state.
     *
     * @hide
     */
    public static final String MODIFY_ONDEVICEPERSONALIZATION_STATE =
            "android.permission.MODIFY_ONDEVICEPERSONALIZATION_STATE";

    /**
     * verify that caller has the permission to modify ODP's enablement state.
     * @throws SecurityException otherwise.
     */
    public static void enforceCallingPermission(@NonNull Context context,
            @NonNull String permission) {
        if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Unauthorized call to ODP.");
        }
    }
}
