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

package com.android.federatedcompute.services.encryption;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.data.OdpEncryptionKeyDao;
import com.android.odp.module.common.encryption.OdpEncryptionKeyManager;
import com.android.odp.module.common.http.HttpClient;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class FederatedComputeEncryptionKeyManagerUtilsTest {

    private static final Map<String, List<String>> SAMPLE_RESPONSE_HEADER =
            Map.of(
                    "Cache-Control", List.of("public,max-age=6000"),
                    "Age", List.of("1"),
                    "Content-Type", List.of("json"));

    private static final String SAMPLE_RESPONSE_PAYLOAD =
                    """
{ "keys": [{ "id": "0cc9b4c9-08bd", "key": "BQo+c1Tw6TaQ+VH/b+9PegZOjHuKAFkl8QdmS0IjRj8" """
                    + "} ] }";

    @Mock private HttpClient mMockHttpClient;

    @Mock private OdpEncryptionKeyDao mMockEncryptionKeyDao;

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private Clock mClock;

    private Flags mMockFlags;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mClock = MonotonicClock.getInstance();
        mMockFlags = Mockito.mock(Flags.class);
        String overrideUrl = "https://real-coordinator/v1alpha/publicKeys";
        doReturn(overrideUrl).when(mMockFlags).getEncryptionKeyFetchUrl();
    }

    @After
    public void tearDown() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(sContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testGetInstance() {
        OdpEncryptionKeyManager instanceUnderTest =
                FederatedComputeEncryptionKeyManagerUtils.getInstance(sContext);
        OdpEncryptionKeyManager secondInstance =
                FederatedComputeEncryptionKeyManagerUtils.getInstance(sContext);

        assertThat(instanceUnderTest).isSameInstanceAs(secondInstance);
        assertNotNull(instanceUnderTest);
        assertThat(instanceUnderTest).isInstanceOf(OdpEncryptionKeyManager.class);
        assertThat(instanceUnderTest.getKeyManagerConfigForTesting().getSQLiteOpenHelper())
                .isSameInstanceAs(FederatedComputeDbHelper.getInstance(sContext));
    }

    @Test
    public void testGetInstanceForTesting() {
        OdpEncryptionKeyManager instanceUnderTest =
                FederatedComputeEncryptionKeyManagerUtils.getInstanceForTest(
                        mClock,
                        mMockEncryptionKeyDao,
                        mMockFlags,
                        mMockHttpClient,
                        MoreExecutors.newDirectExecutorService(),
                        sContext);
        OdpEncryptionKeyManager secondInstance =
                FederatedComputeEncryptionKeyManagerUtils.getInstanceForTest(
                        mClock,
                        mMockEncryptionKeyDao,
                        mMockFlags,
                        mMockHttpClient,
                        MoreExecutors.newDirectExecutorService(),
                        sContext);

        assertThat(instanceUnderTest).isSameInstanceAs(secondInstance);
        assertNotNull(instanceUnderTest);
        assertThat(instanceUnderTest).isInstanceOf(OdpEncryptionKeyManager.class);
        assertThat(instanceUnderTest.getKeyManagerConfigForTesting().getSQLiteOpenHelper())
                .isSameInstanceAs(FederatedComputeDbHelper.getInstanceForTest(sContext));
    }
}
