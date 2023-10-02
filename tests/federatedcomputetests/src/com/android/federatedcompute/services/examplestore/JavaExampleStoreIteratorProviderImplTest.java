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

package com.android.federatedcompute.services.examplestore;

import static android.federatedcompute.common.ClientConstants.EXAMPLE_STORE_ACTION;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.common.Flags;

import com.google.internal.federated.plan.ExampleSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class JavaExampleStoreIteratorProviderImplTest {
    private static final long TIMEOUT_SECS = 5L;
    private static final String EXPECTED_COLLECTION_NAME =
            "/federatedcompute.examplestoretest/test_collection";

    private ExampleStoreIteratorProviderImpl mExampleStoreIteratorProvider;
    private ExampleStoreServiceProviderImpl mExampleStoreServiceProvider;
    private Context mContext = ApplicationProvider.getApplicationContext();

    private String mPackageName;

    private Intent mIntent;
    @Mock private Flags mMockFlags;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPackageName = mContext.getPackageName();
        mExampleStoreServiceProvider = new ExampleStoreServiceProviderImpl(mContext, mMockFlags);
        mIntent = new Intent();
        mIntent.setAction(EXAMPLE_STORE_ACTION).setPackage(mPackageName);
        mIntent.setData(
                new Uri.Builder().scheme("app").authority(mPackageName).path("collection").build());
        when(mMockFlags.getAppHostedExampleStoreTimeoutSecs()).thenReturn(TIMEOUT_SECS);
        mExampleStoreIteratorProvider =
                new ExampleStoreIteratorProviderImpl(mExampleStoreServiceProvider, mMockFlags);
    }

    @After
    public void cleanup() {
        mExampleStoreServiceProvider.unbindService();
    }

    @Test
    public void testGetExampleStoreIterator() throws Exception {
        ExampleSelector exampleSelector =
                ExampleSelector.newBuilder().setCollectionUri(EXPECTED_COLLECTION_NAME).build();
        IExampleStoreIterator iterator =
                mExampleStoreIteratorProvider.getExampleStoreIterator(
                        mPackageName, exampleSelector);
        assertNotNull(iterator);
    }

    @Test
    public void testGetExampleStoreIterator_fail() throws Exception {
        ExampleSelector exampleSelector =
                ExampleSelector.newBuilder().setCollectionUri("bad_collection_name").build();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mExampleStoreIteratorProvider.getExampleStoreIterator(
                                mPackageName, exampleSelector));
    }
}
