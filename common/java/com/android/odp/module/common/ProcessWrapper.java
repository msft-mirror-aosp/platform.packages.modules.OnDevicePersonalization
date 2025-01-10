/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.odp.module.common;

import android.os.Process;

/**
 * A wrapper that provides access to android.os.Process class methods and details. This helps
 * tests by providing a mockable object and not impacting other Process class level interactions.
 */
public class ProcessWrapper {
    private ProcessWrapper() {}

    /** Returns whether the provided UID belongs to an sdk sandbox process. */
    public static boolean isSdkSandboxUid(int uid) {
        return Process.isSdkSandboxUid(uid);
    }

    /** Returns the app uid corresponding to an sdk sandbox uid.
     * @throws IllegalArgumentException if input is not an sdk sandbox uid
     */
    public static int getAppUidForSdkSandboxUid(int uid) {
        return Process.getAppUidForSdkSandboxUid(uid);
    }
}
