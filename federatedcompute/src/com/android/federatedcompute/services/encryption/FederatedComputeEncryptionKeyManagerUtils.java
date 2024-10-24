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

package com.android.federatedcompute.services.encryption;

import android.content.Context;

import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.data.OdpEncryptionKeyDao;
import com.android.odp.module.common.data.OdpSQLiteOpenHelper;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;
import com.android.odp.module.common.http.HttpClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Wrapper class that manages the creation of the underlying {@link OdpEncryptionKeyManager} with
 * the appropriate {@link OdpEncryptionKeyManager.KeyManagerConfig} and {@link OdpSQLiteOpenHelper}.
 */
public class FederatedComputeEncryptionKeyManagerUtils {

    public static class FlagKeyManagerConfig implements OdpEncryptionKeyManager.KeyManagerConfig {

        private final Flags mFlags;
        private final FederatedComputeDbHelper mFederatedComputeDbHelper;

        FlagKeyManagerConfig(Flags flags, FederatedComputeDbHelper federatedComputeDbHelper) {
            mFlags = flags;
            this.mFederatedComputeDbHelper = federatedComputeDbHelper;
        }

        @Override
        public String getEncryptionKeyFetchUrl() {
            return mFlags.getEncryptionKeyFetchUrl();
        }

        @Override
        public int getHttpRequestRetryLimit() {
            return mFlags.getHttpRequestRetryLimit();
        }

        @Override
        public long getEncryptionKeyMaxAgeSeconds() {
            return mFlags.getFederatedComputeEncryptionKeyMaxAgeSeconds();
        }

        @Override
        public OdpSQLiteOpenHelper getSQLiteOpenHelper() {
            return mFederatedComputeDbHelper;
        }

        @Override
        public ListeningExecutorService getBackgroundExecutor() {
            return FederatedComputeExecutors.getBackgroundExecutor();
        }
    }

    /** Class is not meant to be instantiated, thin wrapper over {@link OdpEncryptionKeyManager} */
    private FederatedComputeEncryptionKeyManagerUtils() {}

    /** Returns a singleton instance for the {@link FederatedComputeEncryptionKeyManagerUtils}. */
    public static OdpEncryptionKeyManager getInstance(Context context) {
        return OdpEncryptionKeyManager.getInstance(
                context,
                new FlagKeyManagerConfig(
                        FlagsFactory.getFlags(), FederatedComputeDbHelper.getInstance(context)));
    }

    /** For testing only, returns an instance of key manager for test. */
    @VisibleForTesting
    static OdpEncryptionKeyManager getInstanceForTest(
            Clock clock,
            OdpEncryptionKeyDao encryptionKeyDao,
            Flags flags,
            HttpClient client,
            ListeningExecutorService executor,
            Context context) {
        return OdpEncryptionKeyManager.getInstanceForTesting(
                clock,
                encryptionKeyDao,
                new FlagKeyManagerConfig(
                        flags, FederatedComputeDbHelper.getInstanceForTest(context)),
                client,
                executor);
    }
}
