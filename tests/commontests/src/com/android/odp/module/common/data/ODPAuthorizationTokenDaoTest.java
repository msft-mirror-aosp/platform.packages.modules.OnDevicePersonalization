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

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ODPAuthorizationTokenDaoTest {

    private static final Context sTestContext = ApplicationProvider.getApplicationContext();
    private static final OdpEncryptionKeyDaoTest.TestDbHelper sTestDbHelper =
            new OdpEncryptionKeyDaoTest.TestDbHelper(sTestContext);

    private final Clock mClock = MonotonicClock.getInstance();

    private static final String TOKEN1 = "b3c4dc4a-768b-415d-8adb-d3aa2206b7bb";

    private static final String TOKEN2 = "3e0e1ff7-e34e-4fdc-861e-048bd59b3746";

    private static final String OWNER_IDENTIFIER1 = "atp1";

    private static final String OWNER_IDENTIFIER2 = "atp2";

    private static final long ONE_HOUR = 60 * 60 * 60 * 1000L;

    private ODPAuthorizationTokenDao mDaoUnderTest;

    @Before
    public void setUp() {
        mDaoUnderTest = ODPAuthorizationTokenDao.getInstanceForTest(sTestDbHelper);
    }

    @After
    public void cleanUp() throws Exception {
        sTestDbHelper.getWritableDatabase().close();
        sTestDbHelper.getReadableDatabase().close();
        sTestDbHelper.close();
    }

    @Test
    public void testInsertAuthToken_notExist_success() {
        SQLiteDatabase db = sTestDbHelper.getReadableDatabase();
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(0);
        ODPAuthorizationToken authToken1 = createAuthToken(OWNER_IDENTIFIER1, TOKEN1, ONE_HOUR);
        ODPAuthorizationToken authToken2 = createAuthToken(OWNER_IDENTIFIER2, TOKEN2, ONE_HOUR);

        mDaoUnderTest.insertAuthorizationToken(authToken1);
        mDaoUnderTest.insertAuthorizationToken(authToken2);
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(2);
    }

    @Test
    public void testInsertAuthToken_preExist_success() {
        SQLiteDatabase db = sTestDbHelper.getReadableDatabase();
        ODPAuthorizationToken authToken1 = createAuthToken(OWNER_IDENTIFIER1, TOKEN1, ONE_HOUR);
        ODPAuthorizationToken authToken2 = createAuthToken(OWNER_IDENTIFIER1, TOKEN2, ONE_HOUR);

        mDaoUnderTest.insertAuthorizationToken(authToken1);
        mDaoUnderTest.insertAuthorizationToken(authToken2);
        ODPAuthorizationToken storedToken =
                mDaoUnderTest.getUnexpiredAuthorizationToken(OWNER_IDENTIFIER1);

        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(1);
        assertThat(storedToken).isEqualTo(authToken2);
    }

    @Test
    public void testInsertNullAuthTokenThrows() {
        assertThrows(NullPointerException.class, this::insertNullAuthToken);
    }

    private void insertNullAuthToken() {
        ODPAuthorizationToken authToken =
                new ODPAuthorizationToken.Builder()
                        .setOwnerIdentifier(OWNER_IDENTIFIER1)
                        .setCreationTime(mClock.currentTimeMillis())
                        .setExpiryTime(mClock.currentTimeMillis() + ONE_HOUR)
                        .build();
        mDaoUnderTest.insertAuthorizationToken(authToken);
    }

    @Test
    public void testGetAuthToken_notExist_returnsNullToken() {
        ODPAuthorizationToken authToken =
                mDaoUnderTest.getUnexpiredAuthorizationToken(OWNER_IDENTIFIER1);
        assertThat(authToken).isEqualTo(null);
    }

    @Test
    public void testGetAuthToken_exist_success() {
        ODPAuthorizationToken authToken1 = createAuthToken(OWNER_IDENTIFIER1, TOKEN1, ONE_HOUR);
        ODPAuthorizationToken authToken2 = createAuthToken(OWNER_IDENTIFIER2, TOKEN2, ONE_HOUR);
        mDaoUnderTest.insertAuthorizationToken(authToken1);
        mDaoUnderTest.insertAuthorizationToken(authToken2);

        ODPAuthorizationToken storedToken1 =
                mDaoUnderTest.getUnexpiredAuthorizationToken(OWNER_IDENTIFIER1);
        ODPAuthorizationToken storedToken2 =
                mDaoUnderTest.getUnexpiredAuthorizationToken(OWNER_IDENTIFIER2);

        assertThat(storedToken1).isEqualTo(authToken1);
        assertThat(storedToken2).isEqualTo(authToken2);
    }

    @Test
    public void testGetAuthToken_expired_returnsNullToken() {
        ODPAuthorizationToken authToken1 =
                createAuthToken(OWNER_IDENTIFIER1, TOKEN1, /* ttl= */ 0L);
        ODPAuthorizationToken authToken2 =
                createAuthToken(OWNER_IDENTIFIER2, TOKEN2, /* ttl= */ 0L);
        mDaoUnderTest.insertAuthorizationToken(authToken1);
        mDaoUnderTest.insertAuthorizationToken(authToken2);

        ODPAuthorizationToken storedToken1 =
                mDaoUnderTest.getUnexpiredAuthorizationToken(OWNER_IDENTIFIER1);
        ODPAuthorizationToken storedToken2 =
                mDaoUnderTest.getUnexpiredAuthorizationToken(OWNER_IDENTIFIER2);

        assertThat(storedToken1).isEqualTo(null);
        assertThat(storedToken2).isEqualTo(null);
    }

    @Test
    public void testDeleteAuthToken_exist_success() {
        SQLiteDatabase db = sTestDbHelper.getReadableDatabase();
        ODPAuthorizationToken authToken1 = createAuthToken(OWNER_IDENTIFIER1, TOKEN1, ONE_HOUR);
        mDaoUnderTest.insertAuthorizationToken(authToken1);

        int deletedRows = mDaoUnderTest.deleteAuthorizationToken(OWNER_IDENTIFIER1);

        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(0);
        assertThat(deletedRows).isEqualTo(1);
    }

    @Test
    public void testDeleteAuthToken_notExist_success() {
        int deletedRows = mDaoUnderTest.deleteAuthorizationToken(OWNER_IDENTIFIER1);
        assertThat(deletedRows).isEqualTo(0);
    }

    @Test
    public void testDeleteAuthTokensEmpty_success() {
        long rowsDeleted = mDaoUnderTest.deleteExpiredAuthorizationTokens();
        assertThat(rowsDeleted).isEqualTo(0);
    }

    @Test
    public void testDeleteAuthTokens_success() throws Exception {
        mDaoUnderTest.insertAuthorizationToken(
                createAuthToken(/* owner= */ "o1", UUID.randomUUID().toString(), /* ttl= */ 0L));
        mDaoUnderTest.insertAuthorizationToken(
                createAuthToken(/* owner= */ "o2", UUID.randomUUID().toString(), /* ttl= */ 0L));
        mDaoUnderTest.insertAuthorizationToken(
                createAuthToken(/* owner= */ "o3", UUID.randomUUID().toString(), ONE_HOUR));

        Thread.sleep(10L);
        long rowsDeleted = mDaoUnderTest.deleteExpiredAuthorizationTokens();

        assertThat(rowsDeleted).isEqualTo(2);
    }

    private ODPAuthorizationToken createAuthToken(String owner, String token, Long ttl) {
        long now = mClock.currentTimeMillis();
        return new ODPAuthorizationToken.Builder(owner, token, now, now + ttl).build();
    }
}
