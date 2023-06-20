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
import android.annotation.Nullable;
import android.ondevicepersonalization.aidl.IDataAccessService;

import java.util.Objects;

/**
 * An opaque token that identifies the current request.
 *
 * @hide
 */
public class RequestToken {
    @NonNull private IDataAccessService mDataAccessService;
    @Nullable private UserData mUserData;

    /** @hide */
    RequestToken(
            @NonNull IDataAccessService binder,
            @Nullable UserData userData) {
        mDataAccessService = Objects.requireNonNull(binder);
        mUserData = userData;
    }

    /** @hide */
    @NonNull IDataAccessService getDataAccessService() {
        return mDataAccessService;
    }

    /** @hide */
    @Nullable UserData getUserData() {
        return mUserData;
    }
}
