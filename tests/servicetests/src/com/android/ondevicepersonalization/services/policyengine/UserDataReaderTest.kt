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

import android.ondevicepersonalization.AppInstallStatus
import android.ondevicepersonalization.AppUsageStatus
import android.ondevicepersonalization.DeviceMetrics
import android.ondevicepersonalization.OSVersion
import android.ondevicepersonalization.Location
import android.ondevicepersonalization.LocationStatus
import android.ondevicepersonalization.UserData

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

import com.android.ondevicepersonalization.services.policyengine.api.ChronicleManager
import com.android.ondevicepersonalization.services.policyengine.data.UserDataReader
import com.android.ondevicepersonalization.services.policyengine.data.impl.UserDataConnectionProvider
import com.android.ondevicepersonalization.services.policyengine.policy.DataIngressPolicy
import com.android.ondevicepersonalization.services.policyengine.policy.rules.DevicePersonalizedAdsEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.UserPersonalizedAdsEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.AppPersonalizedAdsEnabled
import com.android.ondevicepersonalization.services.policyengine.policy.rules.RequestPersonalizedAdsEnabled

import com.android.ondevicepersonalization.services.data.user.RawUserData
import com.android.ondevicepersonalization.services.data.user.UserDataCollector

import com.google.common.truth.Truth.assertThat

import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class UserDataReaderTest : ProcessorNode {

    private lateinit var policyContext: MutableTypedMap
    private val userDataCollector: UserDataCollector =
            UserDataCollector.getInstanceForTest(ApplicationProvider.getApplicationContext())
    private val rawUserData: RawUserData = RawUserData.getInstance()
    private val TAG: String = "UserDataReaderTest"

    override val requiredConnectionTypes = setOf(UserDataReader::class.java)

    private val chronicleManager: ChronicleManager = ChronicleManager.getInstance(
        connectionProviders = setOf(UserDataConnectionProvider()),
        policies = setOf(DataIngressPolicy.NPA_DATA_POLICY),
        connectionContext = TypedMap()
    )

    @Before
    fun setUp() {
        policyContext = MutableTypedMap()
        policyContext[DevicePersonalizedAdsEnabled] = true
        policyContext[UserPersonalizedAdsEnabled] = true
        policyContext[AppPersonalizedAdsEnabled] = true
        policyContext[RequestPersonalizedAdsEnabled] = true

        chronicleManager.chronicle.updateConnectionContext(TypedMap(policyContext))
        chronicleManager.failNewConnections(false)
        userDataCollector.updateUserData(rawUserData)
    }

    @Test
    fun testUserDataConnection() {
        val result: ConnectionResult<UserDataReader>? = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(ConnectionResult.Success::class.java)
    }

    @Test
    fun testUserDataReader() {
        try {
            val userDataReader: UserDataReader? = chronicleManager.chronicle.getConnectionOrThrow(
                ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
            )
            val userData: UserData? = userDataReader?.readUserData()
            // Whether user data is null should not matter to policy engine
            if (userData != null) {
                verifyData(userData, rawUserData)
                // test real-time data update
                userDataCollector.getRealTimeData(rawUserData)
                val updatedUserData: UserData? = userDataReader.readUserData()
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
        val result: ConnectionResult<UserDataReader>? = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        assertThat(result).isNotNull()
        result?.expectFailure(PolicyViolation::class.java)
    }

    @Test
    fun testFailNewConnection() {
        chronicleManager.failNewConnections(true)
        val result: ConnectionResult<UserDataReader>? = chronicleManager.chronicle.getConnection(
            ConnectionRequest(UserDataReader::class.java, this, DataIngressPolicy.NPA_DATA_POLICY)
        )
        assertThat(result).isNotNull()
        result?.expectFailure(Disabled::class.java)
    }

    private fun verifyData(userData: UserData, ref: RawUserData) {
        assertThat(userData.getTimeSec()).isEqualTo(ref.timeMillis / 1000)
        assertThat(userData.getTimezone()).isEqualTo(ref.utcOffset)
        assertThat(userData.getOrientation()).isEqualTo(ref.orientation)
        assertThat(userData.getAvailableBytesMB()).isEqualTo(ref.availableBytesMB)
        assertThat(userData.getBatteryPct()).isEqualTo(ref.batteryPct)
        assertThat(userData.getCountry()).isEqualTo(ref.country.ordinal)
        assertThat(userData.getLanguage()).isEqualTo(ref.language.ordinal)
        assertThat(userData.getCarrier()).isEqualTo(ref.carrier.ordinal)

        val osVersion: OSVersion = userData.getOsVersions()
        assertThat(osVersion.getMajor()).isEqualTo(ref.osVersions.major)
        assertThat(osVersion.getMinor()).isEqualTo(ref.osVersions.minor)
        assertThat(osVersion.getMicro()).isEqualTo(ref.osVersions.micro)

        assertThat(userData.getConnectionType()).isEqualTo(ref.connectionType.ordinal)
        assertThat(userData.getConnectionSpeedKbps()).isEqualTo(ref.connectionSpeedKbps)
        assertThat(userData.isNetworkMetered()).isEqualTo(ref.networkMeteredStatus)

        val deviceMetrics: DeviceMetrics = userData.getDeviceMetrics()

        assertThat(deviceMetrics.getMake()).isEqualTo(ref.deviceMetrics.make.ordinal)
        assertThat(deviceMetrics.getModel()).isEqualTo(ref.deviceMetrics.model.ordinal)
        assertThat(deviceMetrics.getScreenHeights()).isEqualTo(ref.deviceMetrics.screenHeight)
        assertThat(deviceMetrics.getScreenWidth()).isEqualTo(ref.deviceMetrics.screenWidth)
        assertThat(deviceMetrics.getXdpi()).isEqualTo(ref.deviceMetrics.xdpi)
        assertThat(deviceMetrics.getYdpi()).isEqualTo(ref.deviceMetrics.ydpi)
        assertThat(deviceMetrics.getPxRatio()).isEqualTo(ref.deviceMetrics.pxRatio)

        val currentLocation: Location = userData.getCurrentLocation()

        assertThat(currentLocation.getTimeSec()).isEqualTo(rawUserData.currentLocation.timeMillis / 1000)
        assertThat(currentLocation.getLatitude()).isEqualTo(rawUserData.currentLocation.latitude)
        assertThat(currentLocation.getLongitude()).isEqualTo(rawUserData.currentLocation.longitude)
        assertThat(currentLocation.getLocationProvider()).isEqualTo(rawUserData.currentLocation.provider.ordinal)
        assertThat(currentLocation.isPreciseLocation()).isEqualTo(rawUserData.currentLocation.isPreciseLocation)

        assertThat(userData.getAppInstalledHistory().size).isEqualTo(rawUserData.appsInfo.size)

        for ((index, appStatus) in userData.getAppInstalledHistory().withIndex()) {
            assertThat(appStatus.getPackageName()).isEqualTo(rawUserData.appsInfo[index].packageName)
            assertThat(appStatus.isInstalled()).isEqualTo(rawUserData.appsInfo[index].installed)
        }

        assertThat(userData.getAppUsageHistory().size).isEqualTo(rawUserData.appUsageHistory.size)
        assertThat(userData.getLocationHistory().size).isEqualTo(rawUserData.locationHistory.size)
    }

    private fun ConnectionResult<*>.expectFailure(cls: Class<out ChronicleError>) {
        when (this) {
            is ConnectionResult.Success -> fail("Expected failure with $cls, but got success")
            is ConnectionResult.Failure -> assertThat(error).isInstanceOf(cls)
        }
    }
}
