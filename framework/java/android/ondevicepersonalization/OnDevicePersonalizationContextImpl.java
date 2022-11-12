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
import android.ondevicepersonalization.aidl.IDataAccessService;

/**
 * Container for per-request state and APIs for code that runs in the isolated
 * process.
 *
 * @hide
 */
public class OnDevicePersonalizationContextImpl implements OnDevicePersonalizationContext {
    @NonNull RemoteData mRemoteData;

    /** @hide */
    public OnDevicePersonalizationContextImpl(@NonNull IDataAccessService binder) {
        mRemoteData = new RemoteDataImpl(binder);
    }

    @NonNull public RemoteData getRemoteData() {
        return mRemoteData;
    }
}
