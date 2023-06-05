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

package com.android.ondevicepersonalization.services.request;

import android.ondevicepersonalization.RenderingData;
import android.ondevicepersonalization.RequestLogRecord;

import com.android.ondevicepersonalization.services.util.ParcelWrapper;

import java.io.Serializable;

/**
 * A Serializable wrapper for the data used internally for rendering.
 */
class SlotWrapper implements Serializable {
    private ParcelWrapper<RequestLogRecord> mWrappedLogRecord;
    private int mSlotIndex;
    private ParcelWrapper<RenderingData> mWrappedRenderingData;
    private String mServicePackageName;
    private long mQueryId;

    SlotWrapper(
            RequestLogRecord logInfo, int slotIndex, RenderingData slotRenderingInfo,
            String servicePackageName, long queryId) {
        mWrappedLogRecord = new ParcelWrapper<>(logInfo);
        mWrappedRenderingData = new ParcelWrapper<>(slotRenderingInfo);
        mServicePackageName = servicePackageName;
        mSlotIndex = slotIndex;
        mQueryId = queryId;
    }

    RequestLogRecord getLogRecord() {
        return mWrappedLogRecord.get(RequestLogRecord.CREATOR);
    }

    int getSlotIndex() {
        return mSlotIndex;
    }

    RenderingData getRenderingData() {
        return mWrappedRenderingData.get(RenderingData.CREATOR);
    }

    String getServicePackageName() {
        return mServicePackageName;
    }

    long getQueryId() {
        return mQueryId;
    }
}
