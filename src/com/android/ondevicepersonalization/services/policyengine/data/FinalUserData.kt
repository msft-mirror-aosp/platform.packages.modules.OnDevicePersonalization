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

/* Policy-cleared user data representation. */
data class FinalUserData (
    val timeSec: Long,
    val timezone: Int,
    val orientation: Int,
    val availableBytesMB: Int,
    val batteryPct: Int,
    val country: Int,
    val language: Int,
    val carrier: Int,
    val osVersions: OSVersion,
    val connectionType: Int,
    val connectionSpeedKbps: Int,
    val networkMeteredStatus: Boolean,
    val deviceMetrics: DeviceMetrics,
    val appInstalledHistory: List<AppStatus>,
    val appUsageHistory: List<AppUsage>,
    val currentLocation: Location,
    val locationHistory: List<LocationResult>,
)

data class OSVersion (
    val major: Int,
    val minor: Int,
    val micro: Int
)

data class DeviceMetrics (
    val make: Int,
    val model: Int,
    val screenHeightDp: Int,
    val screenWidthDp: Int,
    val xdpi: Float,
    val ydpi: Float,
    val pxRatio: Float
)

data class AppStatus (
    val packageName: String,
    val installed: Boolean
)

data class AppUsage (
    val packageName: String,
    val totalTimeUsedMillis: Long
)

data class Location (
    val timeSec: Long,
    val latitude: Double,
    val longitude: Double,
    val locationProvider: Int,
    val isPreciseLocation: Boolean
)

data class LocationResult (
    val latitude: Double,
    val longitude: Double,
    val durationMillis: Long
)
