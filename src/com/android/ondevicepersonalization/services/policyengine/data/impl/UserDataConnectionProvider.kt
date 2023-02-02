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

package com.android.ondevicepersonalization.services.policyengine.data.impl

import com.android.ondevicepersonalization.services.data.user.UserDataDao
import com.android.ondevicepersonalization.services.data.user.UserData
import com.android.ondevicepersonalization.services.data.user.AppInfo
import com.android.ondevicepersonalization.services.data.user.LocationInfo
import com.android.ondevicepersonalization.services.data.user.UserDataCollector
import com.android.libraries.pcc.chronicle.api.Connection
import com.android.libraries.pcc.chronicle.api.ConnectionProvider
import com.android.libraries.pcc.chronicle.api.ConnectionRequest
import com.android.libraries.pcc.chronicle.api.DataType
import com.android.libraries.pcc.chronicle.api.ManagedDataType
import com.android.libraries.pcc.chronicle.api.ManagementStrategy
import com.android.libraries.pcc.chronicle.api.StorageMedia

import com.android.ondevicepersonalization.services.policyengine.data.FINAL_USER_DATA_GENERATED_DTD
import com.android.ondevicepersonalization.services.policyengine.data.FinalUserData
import com.android.ondevicepersonalization.services.policyengine.data.OSVersion
import com.android.ondevicepersonalization.services.policyengine.data.AppUsage
import com.android.ondevicepersonalization.services.policyengine.data.AppStatus
import com.android.ondevicepersonalization.services.policyengine.data.DeviceMetrics
import com.android.ondevicepersonalization.services.policyengine.data.Location
import com.android.ondevicepersonalization.services.policyengine.data.LocationResult
import com.android.ondevicepersonalization.services.policyengine.data.UserDataReader

import java.time.Duration

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [ConnectionProvider] implementation for ODA use data. */
class UserDataConnectionProvider() : ConnectionProvider {
    override val dataType: DataType =
        ManagedDataType(
            FINAL_USER_DATA_GENERATED_DTD,
            ManagementStrategy.Stored(false, StorageMedia.MEMORY, Duration.ofDays(30)),
            setOf(UserDataReader::class.java)
        )

    override fun getConnection(connectionRequest: ConnectionRequest<out Connection>): Connection {
        return UserDataReaderImpl()
    }

    class UserDataReaderImpl : UserDataReader {
        override fun readUserData(): FinalUserData? {
            val rawUserData: UserData? = UserData.getInstance();
            if (rawUserData == null) {
                return null;
            }

            // TODO(b/267013762): more privacy-preserving processing may be needed
            return FinalUserData(
                timeSec = rawUserData.timeMillis / 1000,
                timezone = rawUserData.utcOffset,
                orientation = rawUserData.orientation,
                availableBytesMB = rawUserData.availableBytesMB,
                batteryPct = rawUserData.batteryPct,
                country = rawUserData.country.ordinal,
                language = rawUserData.language.ordinal,
                carrier = rawUserData.carrier.ordinal,
                osVersions = OSVersion(
                    major = rawUserData.osVersions.major,
                    minor = rawUserData.osVersions.minor,
                    micro = rawUserData.osVersions.micro
                ),
                connectionType = rawUserData.connectionType.ordinal,
                networkMeteredStatus = rawUserData.networkMeteredStatus,
                connectionSpeedKbps = rawUserData.connectionSpeedKbps,
                deviceMetrics = DeviceMetrics(
                    make = rawUserData.deviceMetrics.make.ordinal,
                    model = rawUserData.deviceMetrics.model.ordinal,
                    screenHeightDp = rawUserData.deviceMetrics.screenHeight,
                    screenWidthDp = rawUserData.deviceMetrics.screenWidth,
                    xdpi = rawUserData.deviceMetrics.xdpi,
                    ydpi = rawUserData.deviceMetrics.ydpi,
                    pxRatio = rawUserData.deviceMetrics.pxRatio,
                ),
                appInstalledHistory = getAppInstalledHistory(rawUserData.appsInfo),
                currentLocation = Location(
                    rawUserData.currentLocation.timeMillis / 1000,
                    rawUserData.currentLocation.latitude,
                    rawUserData.currentLocation.longitude,
                    rawUserData.currentLocation.provider.ordinal,
                    rawUserData.currentLocation.isPreciseLocation
                ),
                appUsageHistory = getAppUsageHistory(rawUserData.appUsageHistory),
                locationHistory = getLocationHistory(rawUserData.locationHistory)
            )
        }

        private fun getAppInstalledHistory(list: List<AppInfo>): List<AppStatus> {
            var res = ArrayList<AppStatus>()
            for (appInfo in list) {
                res.add(AppStatus(appInfo.packageName, appInfo.installed))
            }
            return res
        }

        private fun getAppUsageHistory(map: Map<String, Long>): List<AppUsage> {
            var res = ArrayList<AppUsage>()
            map.forEach {
                (key, value) -> res.add(AppUsage(key, value))
            }
            return res.sortedWith(compareBy({ it.totalTimeUsedMillis }))
        }

        private fun getLocationHistory(map: Map<LocationInfo, Long>): List<LocationResult> {
            var res = ArrayList<LocationResult>()
            map.forEach {
                (key, value) -> res.add(LocationResult(
                    key.latitude,
                    key.longitude,
                    value
                ))
            }
            return res.sortedWith(compareBy({ it.durationMillis }))
        }
    }
}
