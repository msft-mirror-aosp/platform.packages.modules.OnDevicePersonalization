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

package com.android.ondevicepersonalization.services.util;

import android.annotation.NonNull;

import java.util.Arrays;

/** A utility class to check a single entity against an allow list */
public class AllowListUtils {
    private static final String ALLOW_ALL = "*";
    private static final String SPLITTER = ",";

    /** check if an entity is in the allow list */
    public static boolean isAllowListed(final String entityName,
                                        @NonNull final String allowList) {
        if (ALLOW_ALL.equals(allowList)) {
            return true;
        }

        if (entityName == null || entityName.trim().isEmpty()) {
            return false;
        }

        return Arrays.stream(allowList.split(SPLITTER))
                .map(String::trim)
                .anyMatch(entityInAllowList -> entityInAllowList.equals(entityName));
    }
}
