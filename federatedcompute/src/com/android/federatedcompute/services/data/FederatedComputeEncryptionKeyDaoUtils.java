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

package com.android.federatedcompute.services.data;

import android.annotation.NonNull;
import android.content.Context;

import com.android.odp.module.common.data.OdpEncryptionKeyDao;

import com.google.common.annotations.VisibleForTesting;

/**
 * Wrapper class that manages the creation of the underlying {@link OdpEncryptionKeyDao} with the
 * appropriate {@link com.android.odp.module.common.data.OdpSQLiteOpenHelper}.
 */
public class FederatedComputeEncryptionKeyDaoUtils {
    private static final String TAG = FederatedComputeEncryptionKeyDaoUtils.class.getSimpleName();

    /** Class is not meant to be instantiated, thin wrapper over {@link OdpEncryptionKeyDao} */
    private FederatedComputeEncryptionKeyDaoUtils() {}

    /** Returns an instance of {@link FederatedComputeEncryptionKeyDaoUtils} given a context. */
    @NonNull
    public static OdpEncryptionKeyDao getInstance(Context context) {
        return OdpEncryptionKeyDao.getInstance(
                context, FederatedComputeDbHelper.getInstance(context));
    }

    /**
     * Helper method to get instance of {@link FederatedComputeEncryptionKeyDaoUtils} for use in
     * tests.
     *
     * <p>Public for use in unit tests.
     */
    @VisibleForTesting
    public static OdpEncryptionKeyDao getInstanceForTest(Context context) {
        return OdpEncryptionKeyDao.getInstance(
                context, FederatedComputeDbHelper.getInstanceForTest(context));
    }
}
