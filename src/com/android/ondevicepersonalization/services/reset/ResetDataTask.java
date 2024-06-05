/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.reset;

import android.content.Context;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;

import java.util.Collections;

/** API to handle a user reset */
class ResetDataTask {
    private static final String TAG = ResetDataTask.class.getSimpleName();
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    /** Delete measurement data. */
    static void deleteMeasurementData() {
        sLogger.i(TAG + ": deleting measurement data.");
        Context context = OnDevicePersonalizationApplication.getAppContext();
        try {
            OnDevicePersonalizationVendorDataDao.deleteVendorTables(
                    context, Collections.emptyList());
        } catch (Exception e) {
            sLogger.e(e, TAG + ": failed to delete vendor tables");
        }
        try {
            EventsDao eventsDao = EventsDao.getInstance(context);
            eventsDao.deleteEventsAndQueries(System.currentTimeMillis());
        } catch (Exception e) {
            sLogger.e(e, TAG + ": failed to delete event tables");
        }
    }
}
