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

package com.android.ondevicepersonalization.services.data;

import android.content.Context;

import com.android.odp.module.common.data.OdpSQLiteOpenHelper;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Utility class that configures and provides appropriate {@link OdpEncryptionKeyManager} instance
 * for use by ODP code.
 */
public class EncryptionUtils {
    /**
     * Flag based implementation of {@link
     * com.android.odp.module.common.encryption.OdpEncryptionKeyManager.KeyManagerConfig}.
     */
    public static class FlagKeyManagerConfig implements OdpEncryptionKeyManager.KeyManagerConfig {

        private final Flags mFlags;
        private final OnDevicePersonalizationDbHelper mDbHelper;

        FlagKeyManagerConfig(Flags flags, OnDevicePersonalizationDbHelper dbHelper) {
            mFlags = flags;
            this.mDbHelper = dbHelper;
        }

        @Override
        public String getEncryptionKeyFetchUrl() {
            return mFlags.getEncryptionKeyFetchUrl();
        }

        @Override
        public int getHttpRequestRetryLimit() {
            return mFlags.getAggregatedErrorReportingHttpRetryLimit();
        }

        @Override
        public long getEncryptionKeyMaxAgeSeconds() {
            return mFlags.getEncryptionKeyMaxAgeSeconds();
        }

        @Override
        public OdpSQLiteOpenHelper getSQLiteOpenHelper() {
            return mDbHelper;
        }

        @Override
        public ListeningExecutorService getBackgroundExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        @Override
        public ListeningExecutorService getBlockingExecutor() {
            return OnDevicePersonalizationExecutors.getBlockingExecutor();
        }
    }

    private EncryptionUtils() {}

    /**
     * Returns an instance of the {@link OdpEncryptionKeyManager}.
     *
     * <p>Creates a {@link FlagKeyManagerConfig} for use by the {@link OdpEncryptionKeyManager} that
     * reflects current relevant flag values.
     *
     * @param context calling context.
     */
    public static OdpEncryptionKeyManager getEncryptionKeyManager(Context context) {
        return OdpEncryptionKeyManager.getInstance(
                context,
                new FlagKeyManagerConfig(
                        FlagsFactory.getFlags(),
                        OnDevicePersonalizationDbHelper.getInstance(context)));
    }
}
