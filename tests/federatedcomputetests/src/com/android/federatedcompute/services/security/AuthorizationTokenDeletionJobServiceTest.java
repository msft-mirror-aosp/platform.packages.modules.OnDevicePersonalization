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

package com.android.federatedcompute.services.security;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.PhFlagsTestUtil;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.ODPAuthorizationToken;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenContract;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.UUID;

public class AuthorizationTokenDeletionJobServiceTest {
    private AuthorizationTokenDeletionJobService mSpyService;

    private MockitoSession mStaticMockSession;

    private ODPAuthorizationTokenDao mSpyAuthTokenDao;

    private Context mContext;

    private Clock mClock;

    @Before
    public void setUp() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        mSpyAuthTokenDao = spy(ODPAuthorizationTokenDao.getInstanceForTest(mContext));
        mSpyService = spy(new AuthorizationTokenDeletionJobService(new TestInjector()));
        mClock = MonotonicClock.getInstance();

        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        jobScheduler.cancel(FederatedComputeJobInfo.ODP_AUTHORIZATION_TOKEN_DELETION_JOB_ID);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }

        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testOnStartJob() {
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired1"));
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired2"));
        mSpyAuthTokenDao.insertAuthorizationToken(createUnexpiredAuthToken("unexpired"));
        mSpyService.run(mock(JobParameters.class));

        verify(mSpyService).onStartJob(any());
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mSpyAuthTokenDao).deleteExpiredAuthorizationTokens();
        SQLiteDatabase db =
                FederatedComputeDbHelper.getInstanceForTest(mContext).getReadableDatabase();
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(1);
    }

    @Test
    public void testOnStartJob_failure() {
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired1"));
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken("expired2"));
        mSpyAuthTokenDao.insertAuthorizationToken(createUnexpiredAuthToken("unexpired"));
        doThrow(new SQLiteException("exception"))
                .when(mSpyAuthTokenDao)
                .deleteExpiredAuthorizationTokens();

        mSpyService.run(mock(JobParameters.class));

        verify(mSpyService).onStartJob(any());
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mSpyAuthTokenDao).deleteExpiredAuthorizationTokens();
        SQLiteDatabase db =
                FederatedComputeDbHelper.getInstanceForTest(mContext).getReadableDatabase();
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                db, ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(3);
    }

    @Test
    public void testOnStartJob_enableKillSwitch() {
        PhFlagsTestUtil.enableGlobalKillSwitch();

        mSpyService.run(mock(JobParameters.class));

        verify(mSpyService).onStartJob(any());
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mSpyAuthTokenDao, never()).deleteExpiredAuthorizationTokens();
    }

    @Test
    public void testDefaultInjector() {
        AuthorizationTokenDeletionJobService.Injector injector =
                new AuthorizationTokenDeletionJobService.Injector();

        assertThat(injector.getExecutor())
                .isEqualTo(FederatedComputeExecutors.getBackgroundExecutor());
        assertThat(injector.getODPAuthorizationTokenDao(mContext))
                .isEqualTo(ODPAuthorizationTokenDao.getInstance(mContext));
    }

    private ODPAuthorizationToken createExpiredAuthToken(String ownerId) {
        long now = mClock.currentTimeMillis();
        ODPAuthorizationToken token =
                new ODPAuthorizationToken.Builder()
                        .setAuthorizationToken(UUID.randomUUID().toString())
                        .setOwnerIdentifier(ownerId)
                        .setCreationTime(now)
                        .setExpiryTime(now)
                        .build();
        return token;
    }

    private ODPAuthorizationToken createUnexpiredAuthToken(String ownerId) {
        long now = mClock.currentTimeMillis();
        long ttl = 24 * 60 * 60 * 1000L;
        ODPAuthorizationToken token =
                new ODPAuthorizationToken.Builder()
                        .setAuthorizationToken(UUID.randomUUID().toString())
                        .setOwnerIdentifier(ownerId)
                        .setCreationTime(now)
                        .setExpiryTime(now + ttl)
                        .build();
        return token;
    }

    class TestInjector extends AuthorizationTokenDeletionJobService.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        ODPAuthorizationTokenDao getODPAuthorizationTokenDao(Context context) {
            return mSpyAuthTokenDao;
        }
    }
}
