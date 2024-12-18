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

package com.android.ondevicepersonalization.services.policyengine.data

import android.adservices.ondevicepersonalization.UserData

import com.android.libraries.pcc.chronicle.api.ReadConnection

/** Reader interface for reading the corresponding vendor table. */
interface UserDataReader : ReadConnection {
    fun readUserData(): UserData?

    fun readUserDataWithAppInstall(): UserData?
}
