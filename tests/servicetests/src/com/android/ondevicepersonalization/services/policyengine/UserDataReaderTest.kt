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

package com.android.ondevicepersonalization.services.policyengine

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Test

import android.util.Log

import com.android.libraries.pcc.chronicle.util.MutableTypedMap
import com.android.libraries.pcc.chronicle.util.TypedMap
import com.android.libraries.pcc.chronicle.api.ConnectionRequest
import com.android.libraries.pcc.chronicle.api.ConnectionResult
import com.android.libraries.pcc.chronicle.api.ReadConnection
import com.android.libraries.pcc.chronicle.api.error.ChronicleError
import com.android.libraries.pcc.chronicle.api.error.PolicyNotFound
import com.android.libraries.pcc.chronicle.api.error.PolicyViolation
import com.android.libraries.pcc.chronicle.api.ProcessorNode

import com.android.ondevicepersonalization.services.policyengine.api.ChronicleManager
import com.android.ondevicepersonalization.services.policyengine.data.FinalUserData
import com.android.ondevicepersonalization.services.policyengine.data.UserDataReader
import com.android.ondevicepersonalization.services.policyengine.data.impl.UserDataConnectionProvider
import com.android.ondevicepersonalization.services.policyengine.policy.DataIngressPolicy
import com.android.ondevicepersonalization.services.policyengine.policy.rules.DevicePersonalizedAdsEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.UserPersonalizedAdsEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.AppPersonalizedAdsEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.RequestPersonalizedAdsEnabled

import com.android.ondevicepersonalization.services.data.user.UserData
import com.android.ondevicepersonalization.services.data.user.UserDataCollector

import com.google.common.truth.Truth.assertThat

import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class UserDataReaderTest : ProcessorNode {

    private lateinit var chronicleManager: ChronicleManager
    private lateinit var policyContext: MutableTypedMap
    private val userDataCollector: UserDataCollector =
            UserDataCollector.getInstanceForTest(ApplicationProvider.getApplicationContext())
    private val rawUserData: UserData = UserData.getInstance()
    private val TAG: String = "UserDataReaderTest"

    override val requiredConnectionTypes = setOf(UserDataReader::class.java)

    @Before
    fun setUp() {
        policyContext = MutableTypedMap()
        policyContext[DevicePersonalizedAdsEnabled] = true
        policyContext[UserPersonalizedAdsEnabled] = true
        policyContext[AppPersonalizedAdsEnabled] = true
        policyContext[RequestPersonalizedAdsEnabled] = true

        chronicleManager = ChronicleManager(
            connectionProviders = setOf(UserDataConnectionProvider()),
            policies = setOf(DataIngressPolicy.NPA_DATA_POLICY),
            connectionContext = TypedMap(policyContext)
        )
        userDataCollector.initializeUserData(rawUserData)
    }

    @Test
    fun testUserDataConnection() {
        val result = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        assertThat(result).isInstanceOf(ConnectionResult.Success::class.java)
    }

    @Test
    fun testUserDataReader() {
        try {
            val userDataReader: UserDataReader = chronicleManager.chronicle.getConnectionOrThrow(
                ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
            )
            val userData: FinalUserData? = userDataReader.readUserData()
            // Whether user data is null should not matter to policy engine
            if (userData != null) {
                verifyData(userData, rawUserData)
                // test data update
                userDataCollector.getRealTimeData(rawUserData)
                val updatedUserData: FinalUserData? = userDataReader.readUserData()
                if (updatedUserData != null) {
                    verifyData(updatedUserData, rawUserData)
                }
            }
        } catch (e: ChronicleError) {
            Log.e(TAG, "Expect success but connection failed with: ", e)
        }
    }

    @Test
    fun testFailedConnectionContext() {
        policyContext[UserPersonalizedAdsEnabled] = false
        chronicleManager.chronicle.updateConnectionContext(TypedMap(policyContext))
        val result = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        result.expectFailure(PolicyViolation::class.java)
    }

    private fun verifyData(userData: FinalUserData, ref: UserData) {
        assertThat(userData.timeSec).isEqualTo(ref.timeMillis / 1000)
        assertThat(userData.timezone).isEqualTo(ref.utcOffset)
        assertThat(userData.orientation).isEqualTo(ref.orientation)
        assertThat(userData.availableBytesMB).isEqualTo(ref.availableBytesMB)
        assertThat(userData.batteryPct).isEqualTo(ref.batteryPct)
        assertThat(userData.country).isEqualTo(ref.country.ordinal)
        assertThat(userData.language).isEqualTo(ref.language.ordinal)
        assertThat(userData.carrier).isEqualTo(ref.carrier.ordinal)

        assertThat(userData.osVersions.major).isEqualTo(ref.osVersions.major)
        assertThat(userData.osVersions.minor).isEqualTo(ref.osVersions.minor)
        assertThat(userData.osVersions.micro).isEqualTo(ref.osVersions.micro)

        assertThat(userData.connectionType).isEqualTo(ref.connectionType.ordinal)
        assertThat(userData.connectionSpeedKbps).isEqualTo(ref.connectionSpeedKbps)
        assertThat(userData.networkMeteredStatus).isEqualTo(ref.networkMeteredStatus)

        assertThat(userData.deviceMetrics.make).isEqualTo(
            ref.deviceMetrics.make.ordinal)
        assertThat(userData.deviceMetrics.model).isEqualTo(
            ref.deviceMetrics.model.ordinal)
        assertThat(userData.deviceMetrics.screenHeightDp).isEqualTo(
            ref.deviceMetrics.screenHeight)
        assertThat(userData.deviceMetrics.screenWidthDp).isEqualTo(
            ref.deviceMetrics.screenWidth)
        assertThat(userData.deviceMetrics.xdpi).isEqualTo(
            ref.deviceMetrics.xdpi)
        assertThat(userData.deviceMetrics.ydpi).isEqualTo(
            ref.deviceMetrics.ydpi)
        assertThat(userData.deviceMetrics.pxRatio).isEqualTo(
            ref.deviceMetrics.pxRatio)

        assertThat(userData.currentLocation.timeSec).isEqualTo(
            rawUserData.currentLocation.timeMillis / 1000)
        assertThat(userData.currentLocation.latitude).isEqualTo(
            rawUserData.currentLocation.latitude)
        assertThat(userData.currentLocation.longitude).isEqualTo(
            rawUserData.currentLocation.longitude)
        assertThat(userData.currentLocation.locationProvider).isEqualTo(
            rawUserData.currentLocation.provider.ordinal)
        assertThat(userData.currentLocation.isPreciseLocation).isEqualTo(
            rawUserData.currentLocation.isPreciseLocation)

        assertThat(userData.appInstalledHistory.size).isEqualTo(rawUserData.appsInfo.size)
        for ((index, appStatus) in userData.appInstalledHistory.withIndex()) {
            assertThat(appStatus.packageName).isEqualTo(rawUserData.appsInfo[index].packageName)
            assertThat(appStatus.installed).isEqualTo(rawUserData.appsInfo[index].installed)
        }

        assertThat(userData.appUsageHistory.size).isEqualTo(rawUserData.appUsageHistory.size)
        assertThat(userData.locationHistory.size).isEqualTo(rawUserData.locationHistory.size)
    }

    private fun ConnectionResult<*>.expectFailure(cls: Class<out ChronicleError>) {
        when (this) {
            is ConnectionResult.Success -> fail("Expected failure with $cls, but got success")
            is ConnectionResult.Failure -> assertThat(error).isInstanceOf(cls)
        }
    }
}
