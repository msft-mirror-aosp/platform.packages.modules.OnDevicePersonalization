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

package com.android.ondevicepersonalization.services.policyengine

import android.util.Log

import android.app.ondevicepersonalization.UserData

import com.android.libraries.pcc.chronicle.util.MutableTypedMap
import com.android.libraries.pcc.chronicle.util.TypedMap
import com.android.libraries.pcc.chronicle.api.ConnectionRequest
import com.android.libraries.pcc.chronicle.api.ConnectionResult
import com.android.libraries.pcc.chronicle.api.ReadConnection
import com.android.libraries.pcc.chronicle.api.error.ChronicleError
import com.android.libraries.pcc.chronicle.api.error.PolicyNotFound
import com.android.libraries.pcc.chronicle.api.error.PolicyViolation
import com.android.libraries.pcc.chronicle.api.error.Disabled
import com.android.libraries.pcc.chronicle.api.ProcessorNode

import com.android.ondevicepersonalization.internal.util.LoggerFactory
import com.android.ondevicepersonalization.services.policyengine.api.ChronicleManager
import com.android.ondevicepersonalization.services.policyengine.data.UserDataReader
import com.android.ondevicepersonalization.services.policyengine.data.impl.UserDataConnectionProvider
import com.android.ondevicepersonalization.services.policyengine.policy.DataIngressPolicy
import com.android.ondevicepersonalization.services.policyengine.policy.rules.KidStatusEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.LimitedAdsTrackingEnabled

import com.android.ondevicepersonalization.services.data.user.RawUserData

class UserDataAccessor : ProcessorNode {
    private val sLogger: LoggerFactory.Logger = LoggerFactory.getLogger();
    private val TAG: String = "UserDataAccessor";
    private lateinit var policyContext: MutableTypedMap
    private val rawUserData: RawUserData = RawUserData.getInstance()

    override val requiredConnectionTypes = setOf(UserDataReader::class.java)

    private val chronicleManager: ChronicleManager = ChronicleManager.getInstance(
        connectionProviders = setOf(UserDataConnectionProvider()),
        policies = setOf(DataIngressPolicy.NPA_DATA_POLICY),
        connectionContext = TypedMap()
    )

    init {
        policyContext = MutableTypedMap()
        policyContext[KidStatusEnabled] = false
        policyContext[LimitedAdsTrackingEnabled] = false

        chronicleManager.chronicle.updateConnectionContext(TypedMap(policyContext))
        chronicleManager.failNewConnections(false)
    }

    fun getUserData(): UserData? {
        try {
            val userDataReader: UserDataReader? =
                chronicleManager.chronicle.getConnectionOrThrow(
                        ConnectionRequest(UserDataReader::class.java, this,
                                            DataIngressPolicy.NPA_DATA_POLICY)
            )
            val userData: UserData? = userDataReader?.readUserData()
            return userData
        } catch (e: ChronicleError) {
            sLogger.e(TAG + ": Expect success but connection failed with: ", e)
            return null
        }
    }
}
