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

package com.android.odp.module.common.encryption;

import static com.android.odp.module.common.encryption.OdpEncryptionKey.KEY_TYPE_ENCRYPTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.data.OdpEncryptionKeyDao;
import com.android.odp.module.common.data.OdpEncryptionKeyDaoTest;
import com.android.odp.module.common.data.OdpSQLiteOpenHelper;
import com.android.odp.module.common.http.HttpClient;
import com.android.odp.module.common.http.OdpHttpResponse;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class OdpEncryptionKeyManagerTest {

    private static final String DEFAULT_OVERRIDE_URL =
            "https://real-coordinator/v1alpha/publicKeys";

    private static final Map<String, List<String>> SAMPLE_RESPONSE_HEADER =
            Map.of(
                    "Cache-Control", List.of("public,max-age=6000"),
                    "Age", List.of("1"),
                    "Content-Type", List.of("json"));

    private static final String SAMPLE_RESPONSE_PAYLOAD =
                    """
{ "keys": [{ "id": "0cc9b4c9-08bd", "key": "BQo+c1Tw6TaQ+VH/b+9PegZOjHuKAFkl8QdmS0IjRj8" """
                    + "} ] }";

    private OdpEncryptionKeyManager mOdpEncryptionKeyManager;

    @Mock private HttpClient mMockHttpClient;

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private static final Clock sClock = MonotonicClock.getInstance();

    private static final TestKeyManagerConfig sKeyManagerConfig =
            new TestKeyManagerConfig(DEFAULT_OVERRIDE_URL);
    ;
    private static final OdpEncryptionKeyDaoTest.TestDbHelper sTestDbHelper =
            new OdpEncryptionKeyDaoTest.TestDbHelper(sContext);
    ;

    private static final OdpEncryptionKeyDao sEncryptionKeyDao =
            OdpEncryptionKeyDao.getInstance(sContext, sTestDbHelper);

    private static final ListeningExecutorService sTestExecutor =
            MoreExecutors.newDirectExecutorService();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        sKeyManagerConfig.mEncryptionFetchUrl = DEFAULT_OVERRIDE_URL;
        mOdpEncryptionKeyManager =
                OdpEncryptionKeyManager.getInstanceForTesting(
                        sClock,
                        sEncryptionKeyDao,
                        sKeyManagerConfig,
                        mMockHttpClient,
                        sTestExecutor);
    }

    @After
    public void tearDown() {
        // Delete all existing keys in the DAO along with resetting the singleton instance
        // to allow each test to start from a clean slate.
        sEncryptionKeyDao.deleteAllKeys();
        OdpEncryptionKeyManager.resetForTesting();
        sTestDbHelper.getWritableDatabase().close();
        sTestDbHelper.getReadableDatabase().close();
        sTestDbHelper.close();
    }

    @Test
    public void testGetTTL_fullInfo() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cache-Control", List.of("public,max-age=3600"));
        headers.put("Age", List.of("8"));

        long ttl = OdpEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(3600 - 8);
    }

    @Test
    public void testGetTTL_noCache() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Age", List.of("8"));

        long ttl = OdpEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(0);
    }

    @Test
    public void testGetTTL_noAge() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cache-Control", List.of("public,max-age=3600"));

        long ttl = OdpEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(3600);
    }

    @Test
    public void testGetTTL_empty() {
        Map<String, List<String>> headers = Collections.EMPTY_MAP;

        long ttl = OdpEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(0);
    }

    @Test
    public void testGetTTL_failedParse() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Cache-Control", List.of("public,max-age==3600"));
        headers.put("Age", List.of("8"));

        long ttl = OdpEncryptionKeyManager.getTTL(headers);

        assertThat(ttl).isEqualTo(0);
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduled_success() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<OdpEncryptionKey> keys =
                mOdpEncryptionKeyManager
                        .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                        .get();

        assertThat(keys.size()).isGreaterThan(0);
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduled_success() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<OdpEncryptionKey> keys =
                mOdpEncryptionKeyManager
                        .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                        .get();

        assertThat(keys.size()).isGreaterThan(0);
    }

    @Test
    public void testFetchAndPersistActiveKeys_EmptyUrl_throws() {
        sKeyManagerConfig.mEncryptionFetchUrl = "";
        assertThrows(
                ExecutionException.class,
                () ->
                        mOdpEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_NullUrl_throws() {
        sKeyManagerConfig.mEncryptionFetchUrl = null;
        assertThrows(
                ExecutionException.class,
                () ->
                        mOdpEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_InvalidUrl_throws() {
        sKeyManagerConfig.mEncryptionFetchUrl = "1";
        assertThrows(
                ExecutionException.class,
                () ->
                        mOdpEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduled_throws() {
        doReturn(
                        Futures.immediateFailedFuture(
                                new ExecutionException(
                                        "fetchAndPersistActiveKeys keys failed.",
                                        new IllegalStateException("http 404"))))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        assertThrows(
                ExecutionException.class,
                () ->
                        mOdpEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduled_throws() {
        doReturn(
                        Futures.immediateFailedFuture(
                                new ExecutionException(
                                        "fetchAndPersistActiveKeys keys failed.",
                                        new IllegalStateException("http 404"))))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        assertThrows(
                ExecutionException.class,
                () ->
                        mOdpEncryptionKeyManager
                                .fetchAndPersistActiveKeys(
                                        KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                                .get());
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduledNoDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        mOdpEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                .get();
        List<OdpEncryptionKey> keys =
                sEncryptionKeyDao.readEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */ );

        assertThat(keys.size()).isEqualTo(1);
        assertThat(
                        keys.stream()
                                .map(OdpEncryptionKey::getKeyIdentifier)
                                .collect(Collectors.toList()))
                .containsAtLeastElementsIn(List.of("0cc9b4c9-08bd"));
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduledNoDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        mOdpEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                .get();
        List<OdpEncryptionKey> keys =
                sEncryptionKeyDao.readEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */ );

        assertThat(keys.size()).isEqualTo(1);
        assertThat(
                        keys.stream()
                                .map(OdpEncryptionKey::getKeyIdentifier)
                                .collect(Collectors.toList()))
                .containsAtLeastElementsIn(List.of("0cc9b4c9-08bd"));
    }

    @Test
    public void testFetchAndPersistActiveKeys_scheduledWithDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());
        long currentTime = sClock.currentTimeMillis();
        sEncryptionKeyDao.insertEncryptionKey(
                new OdpEncryptionKey.Builder()
                        .setKeyIdentifier("5161e286-63e5")
                        .setPublicKey("YuOorP14obQLqASrvqbkNxyijjcAUIDx/xeMGZOyykc")
                        .setKeyType(KEY_TYPE_ENCRYPTION)
                        .setCreationTime(currentTime)
                        .setExpiryTime(currentTime)
                        .build());

        mOdpEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ true)
                .get();

        List<OdpEncryptionKey> keys =
                sEncryptionKeyDao.readEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */ );

        assertThat(keys.size()).isEqualTo(1);
    }

    @Test
    public void testFetchAndPersistActiveKeys_nonScheduledWithDeletion() throws Exception {
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());
        long currentTime = sClock.currentTimeMillis();
        sEncryptionKeyDao.insertEncryptionKey(
                new OdpEncryptionKey.Builder()
                        .setKeyIdentifier("5161e286-63e5")
                        .setPublicKey("YuOorP14obQLqASrvqbkNxyijjcAUIDx/xeMGZOyykc")
                        .setKeyType(KEY_TYPE_ENCRYPTION)
                        .setCreationTime(currentTime)
                        .setExpiryTime(currentTime)
                        .build());

        mOdpEncryptionKeyManager
                .fetchAndPersistActiveKeys(KEY_TYPE_ENCRYPTION, /* isScheduledJob= */ false)
                .get();

        List<OdpEncryptionKey> keys =
                sEncryptionKeyDao.readEncryptionKeysFromDatabase(
                        ""
                        /* selection= */ ,
                        new String[0]
                        /* selectionArgs= */ ,
                        ""
                        /* orderBy= */ ,
                        -1
                        /* count= */ );

        assertThat(keys.size()).isEqualTo(2);

        List<OdpEncryptionKey> activeKeys = sEncryptionKeyDao.getLatestExpiryNKeys(2);
        assertThat(activeKeys.size()).isEqualTo(1);
    }

    @Test
    public void testGetOrFetchActiveKeys_fetch() {
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<OdpEncryptionKey> keys =
                mOdpEncryptionKeyManager.getOrFetchActiveKeys(
                        KEY_TYPE_ENCRYPTION, /* keyCount= */ 2);

        verify(mMockHttpClient, times(1)).performRequestAsyncWithRetry(any());
        assertThat(keys.size()).isEqualTo(1);
    }

    @Test
    public void testGetOrFetchActiveKeys_noFetch() {
        long currentTime = sClock.currentTimeMillis();
        sEncryptionKeyDao.insertEncryptionKey(
                new OdpEncryptionKey.Builder()
                        .setKeyIdentifier("5161e286-63e5")
                        .setPublicKey("YuOorP14obQLqASrvqbkNxyijjcAUIDx/xeMGZOyykc")
                        .setKeyType(KEY_TYPE_ENCRYPTION)
                        .setCreationTime(currentTime)
                        .setExpiryTime(currentTime + 5000L)
                        .build());
        doReturn(
                        Futures.immediateFuture(
                                new OdpHttpResponse.Builder()
                                        .setHeaders(SAMPLE_RESPONSE_HEADER)
                                        .setPayload(SAMPLE_RESPONSE_PAYLOAD.getBytes())
                                        .setStatusCode(200)
                                        .build()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<OdpEncryptionKey> keys =
                mOdpEncryptionKeyManager.getOrFetchActiveKeys(
                        KEY_TYPE_ENCRYPTION, /* keyCount= */ 2);

        verify(mMockHttpClient, never()).performRequestAsyncWithRetry(any());
        assertThat(keys.size()).isEqualTo(1);
    }

    @Test
    public void testGetOrFetchActiveKeys_failure() {
        doReturn(Futures.immediateFailedFuture(new InterruptedException()))
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(any());

        List<OdpEncryptionKey> keys =
                mOdpEncryptionKeyManager.getOrFetchActiveKeys(
                        KEY_TYPE_ENCRYPTION, /* keyCount= */ 2);

        verify(mMockHttpClient, times(1)).performRequestAsyncWithRetry(any());
        assertThat(keys.size()).isEqualTo(0);
    }

    private static final class TestKeyManagerConfig
            implements OdpEncryptionKeyManager.KeyManagerConfig {

        // Url to be configured by the tests
        private volatile String mEncryptionFetchUrl;

        private TestKeyManagerConfig(String encryptionFetchUrl) {
            this.mEncryptionFetchUrl = encryptionFetchUrl;
        }

        @Override
        public String getEncryptionKeyFetchUrl() {
            return mEncryptionFetchUrl;
        }

        @Override
        public int getHttpRequestRetryLimit() {
            // Just some default value, not used by tests as the mock http client is used instead.
            return 3;
        }

        /** Max age in seconds for federated compute encryption keys. */
        public long getEncryptionKeyMaxAgeSeconds() {
            // FC default value
            return TimeUnit.DAYS.toSeconds(14/* duration= */ );
        }

        /** The {@link OdpSQLiteOpenHelper} instance for use by the encryption DAO. */
        public OdpSQLiteOpenHelper getSQLiteOpenHelper() {
            // Should not be used in tests as the TestDbHelper is used instead and injected directly
            // into the EncryptionKeyDao.
            return null;
        }

        /** Background executor for use in key fetch and DB updates etc. */
        public ListeningExecutorService getBackgroundExecutor() {
            return sTestExecutor;
        }
    }
}
