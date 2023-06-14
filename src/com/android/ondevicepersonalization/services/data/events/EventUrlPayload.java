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

package com.android.ondevicepersonalization.services.data.events;

import android.annotation.NonNull;
import android.os.PersistableBundle;

import com.android.ondevicepersonalization.services.util.ParcelWrapper;

import java.io.Serializable;

/**
 * EventUrlPayload object for the ODP encrypted URLs
 */
public class EventUrlPayload implements Serializable {
    @NonNull private final ParcelWrapper<PersistableBundle> mEventParams;
    private final int mResponseType;

    public EventUrlPayload(
            @NonNull PersistableBundle params, int responseType) {
        mEventParams = new ParcelWrapper<>(params);
        mResponseType = responseType;
    }

    @NonNull public PersistableBundle getEventParams() {
        return mEventParams.get(PersistableBundle.CREATOR);
    }

    public int getResponseType() {
        return mResponseType;
    }
}
