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

package android.ondevicepersonalization;

import android.annotation.NonNull;
import android.os.Bundle;
import android.os.OutcomeReceiver;

import java.util.List;

/**
 * Interface for On-Device download handling. Download handling runs in the OnDevicePersonalization
 * sandbox and returns row keys of downloaded data to be kept on-device.
 *
 * @hide
 */
public interface DownloadHandler {
    /**
     * Filters data downloaded onto the device
     * @param params Data provided by the calling app or SDK.
     * @param odpContext The {@link OnDevicePersonalizationContext} for this request.
     * @param odpOutcomeReceiver Callback for caller to use to return list of keys to keep
     * TODO(b/239479120): Add additional parameters needed to provide access to
     *                    existing data and finalize return type.
     */
    void filterData(@NonNull Bundle params,
            @NonNull OnDevicePersonalizationContext odpContext,
            @NonNull OutcomeReceiver<List<String>, Exception> odpOutcomeReceiver);
}
