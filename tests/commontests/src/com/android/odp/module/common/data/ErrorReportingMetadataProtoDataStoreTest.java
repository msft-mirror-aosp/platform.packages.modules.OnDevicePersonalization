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

package com.android.odp.module.common.data;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.odp.module.common.proto.ErrorReportingMetadata;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Timestamp;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ErrorReportingMetadataProtoDataStoreTest {

    private static final Context sTestContext = ApplicationProvider.getApplicationContext();
    private static final ListeningExecutorService sTestExecutor =
            MoreExecutors.newDirectExecutorService();

    private static final long TEST_EPOCH_SECONDS = 1733795133L;

    private static final int TIMEOUT_SEC = 5;
    private static final Timestamp TEST_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(TEST_EPOCH_SECONDS).build();

    private ErrorReportingMetadataStore mInstanceUnderTest = null;

    @Test
    public void getInstance_returnsSingletonInstance() {
        mInstanceUnderTest =
                ErrorReportingMetadataProtoDataStore.getInstance(sTestContext, sTestExecutor);

        assertThat(mInstanceUnderTest)
                .isSameInstanceAs(
                        ErrorReportingMetadataProtoDataStore.getInstance(
                                sTestContext, sTestExecutor));
    }

    @Test
    public void setAndGet_successful() throws Exception {
        mInstanceUnderTest =
                ErrorReportingMetadataProtoDataStore.getInstance(sTestContext, sTestExecutor);
        ErrorReportingMetadata testMetadata =
                ErrorReportingMetadataProtoDataStore.getMetadata(TEST_EPOCH_SECONDS);

        ErrorReportingMetadata returnedSetData = wait(mInstanceUnderTest.set(testMetadata));
        ErrorReportingMetadata returnedGetData = wait(mInstanceUnderTest.get());

        assertThat(returnedSetData.getLastSuccessfulUpload()).isEqualTo(TEST_TIMESTAMP);
        assertThat(returnedGetData.getLastSuccessfulUpload()).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    public void getWithoutSet_returnsDefaultInstance() throws Exception {
        mInstanceUnderTest =
                ErrorReportingMetadataProtoDataStore.getInstance(sTestContext, sTestExecutor);

        ErrorReportingMetadata returnedData = wait(mInstanceUnderTest.get());

        assertTrue(
                ErrorReportingMetadataProtoDataStore.isErrorReportingMetadataUninitialized(
                        returnedData));
    }

    private static <T> T wait(ListenableFuture<T> future) throws Exception {
        return future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }
}
