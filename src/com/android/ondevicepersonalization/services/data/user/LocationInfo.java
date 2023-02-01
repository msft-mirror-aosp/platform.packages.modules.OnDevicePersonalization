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

package com.android.ondevicepersonalization.services.data.user;

/** Location information. */
public class LocationInfo {
    // Time in milliseconds.
    public long timeMillis = 0;

    // Latitude.
    public double latitude = 0;

    // Longitude.
    public double longitude = 0;

    // Location provider values.
    public enum LocationProvider {
        UNKNOWN,
        GPS,
        NETWORK,
    };

    // Location provider.
    public LocationProvider provider = LocationProvider.UNKNOWN;

    // Whether the location source is precise.
    public boolean isPreciseLocation = false;

    public LocationInfo() { }

    // Constructor for a deep copy
    public LocationInfo(LocationInfo other) {
        timeMillis = other.timeMillis;
        latitude = other.latitude;
        longitude = other.longitude;
        provider = other.provider;
        isPreciseLocation = other.isPreciseLocation;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LocationInfo)) {
            return false;
        }
        LocationInfo other = (LocationInfo) o;
        return this.latitude == other.latitude && this.longitude == other.longitude;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + Double.valueOf(latitude).hashCode();
        hash = hash * 31 + Double.valueOf(longitude).hashCode();
        return hash;
    }
}
