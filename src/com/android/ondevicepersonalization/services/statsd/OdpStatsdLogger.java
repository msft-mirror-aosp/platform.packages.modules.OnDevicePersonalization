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

package com.android.ondevicepersonalization.services.statsd;

import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ONDEVICEPERSONALIZATION_API_CALLED;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_REDIRECT;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_RESPONSE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EXECUTE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__FEDERATED_COMPUTE_SCHEDULE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_GET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_KEYSET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_PUT;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_REMOVE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_JOINED_EVENTS;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_REQUESTS;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__MODEL_MANAGER_RUN;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_GET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_KEYSET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REQUEST_SURFACE_PACKAGE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_DOWNLOAD_COMPLETED;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EVENT;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EXECUTE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_RENDER;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_TRAINING_EXAMPLE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_WEB_TRIGGER;

import com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog;

import java.util.Set;

/** Log API stats and client error stats to StatsD. */
public class OdpStatsdLogger {
    private static volatile OdpStatsdLogger sStatsdLogger = null;
    private static final Set<Integer> sApiNames = Set.of(
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_REDIRECT,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_RESPONSE,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EXECUTE,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__FEDERATED_COMPUTE_SCHEDULE,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_GET,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_KEYSET,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_PUT,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_REMOVE,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_JOINED_EVENTS,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_REQUESTS,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__MODEL_MANAGER_RUN,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_GET,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_KEYSET,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REQUEST_SURFACE_PACKAGE,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_DOWNLOAD_COMPLETED,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EVENT,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EXECUTE,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_RENDER,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_TRAINING_EXAMPLE,
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_WEB_TRIGGER
    );

    /** Returns an instance of {@link OdpStatsdLogger}. */
    public static OdpStatsdLogger getInstance() {
        if (sStatsdLogger == null) {
            synchronized (OdpStatsdLogger.class) {
                if (sStatsdLogger == null) {
                    sStatsdLogger = new OdpStatsdLogger();
                }
            }
        }
        return sStatsdLogger;
    }

    /** Log API call stats e.g. response code, API name etc. */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        if (!sApiNames.contains(apiCallStats.getApiName())) {
            return;
        }
        OnDevicePersonalizationStatsLog.write(
                ONDEVICEPERSONALIZATION_API_CALLED,
                apiCallStats.getApiClass(),
                apiCallStats.getApiName(),
                apiCallStats.getLatencyMillis(),
                apiCallStats.getResponseCode(),
                apiCallStats.getOverheadLatencyMillis());
    }
}
