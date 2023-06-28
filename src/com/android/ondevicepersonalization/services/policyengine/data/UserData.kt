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

/**
 * This abstract data class is intended to mirror {@link android.ondevicepersonalization.UserData},
 * which represents the policy-cleared user data instance.
 *
 * Chronicle requires a Kotlin data class to represent the data entity, but
 * Kotlin code is not allowed in public APIs, so this mirror class is created
 * as a workaround. See b/268739079 to track future solutions.
 *
 * If one class is updated, the other one should also be updated to match.
 */
data class UserData (
    val timestampSeconds: Long,
    val timezoneUtcOffsetMins: Int,
    val orientation: Int,
    val availableStorageMB: Long,
    val batteryPercentage: Int,
    val carrier: Int,
    val connectionType: Int,
    val connectionSpeedKbps: Long,
    val networkMetered: Boolean,
    val appInstalledHistory: List<AppInstallStatus>,
    val appUsageHistory: List<AppUsageStatus>,
    val currentLocation: Location,
    val locationHistory: List<LocationStatus>,
)

data class AppInstallStatus (
    val packageName: String,
    val installed: Boolean
)

data class AppUsageStatus (
    val packageName: String,
    val totalTimeUsedMillis: Long
)

data class Location (
    val timestampSeconds: Long,
    val latitude: Double,
    val longitude: Double,
    val locationProvider: Int,
    val preciseLocation: Boolean
)

data class LocationStatus (
    val latitude: Double,
    val longitude: Double,
    val durationMillis: Long
)
