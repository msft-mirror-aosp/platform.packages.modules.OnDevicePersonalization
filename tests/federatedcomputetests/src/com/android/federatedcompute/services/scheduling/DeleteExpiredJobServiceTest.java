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

package com.android.federatedcompute.services.scheduling;

import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.odp.module.common.FileUtils.createTempFile;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FederatedComputeJobInfo;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.TaskHistory;
import com.android.federatedcompute.services.data.TaskHistoryContract;
import com.android.federatedcompute.services.sharedlibrary.spe.FederatedComputeJobScheduler;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;
import com.android.odp.module.common.data.OdpAuthorizationToken;
import com.android.odp.module.common.data.OdpAuthorizationTokenContract;
import com.android.odp.module.common.data.OdpAuthorizationTokenDao;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@MockStatic(FlagsFactory.class)
public class DeleteExpiredJobServiceTest {

    @Rule(order = 0)
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final String TAG = DeleteExpiredJobServiceTest.class.getSimpleName();
    private static final String POPULATION_NAME = "population_name";
    private static final int JOB_ID = 123;

    private static final long TEST_CURRENT_TIME = 400L;
    public static final long TEST_TTL = 200L;

    private static final String TASK_ID = "task_id";
    private static final Duration THREAD_SLEEP = Duration.ofSeconds(5);

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private static final String TEST_EXPIRED_TOKEN1 = "expired1";
    private static final String TEST_EXPIRED_TOKEN2 = "expired3";
    private static final String TEST_UNEXPIRED_TOKEN = "unexpired";

    private static final ImmutableList<String> TEST_OWNER_IDS =
            ImmutableList.of(TEST_EXPIRED_TOKEN1, TEST_EXPIRED_TOKEN2, TEST_UNEXPIRED_TOKEN);
    private DeleteExpiredJobService mSpyService;

    private OdpAuthorizationTokenDao mSpyAuthTokenDao;

    private FederatedComputeDbHelper mTestDbHelper;
    private FederatedTrainingTaskDao mTrainingTaskDao;

    private JobScheduler mJobScheduler;
    @Mock private Clock mClock;
    @Mock private Flags mMockFlag;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        doReturn(mMockFlag).when(FlagsFactory::getFlags);
        when(mMockFlag.getGlobalKillSwitch()).thenReturn(false);

        // By default, disable SPE.
        when(mMockFlag.getSpePilotJobEnabled()).thenReturn(false);

        when(mClock.currentTimeMillis()).thenReturn(TEST_CURRENT_TIME);
        when(mMockFlag.getTaskHistoryTtl()).thenReturn(TEST_TTL);

        LogUtil.i(TAG, "mSpyAuthTokenDao " + mSpyAuthTokenDao);
        mTestDbHelper = FederatedComputeDbHelper.getNonSingletonInstanceForTest(sContext);
        mSpyAuthTokenDao = spy(OdpAuthorizationTokenDao.getInstanceForTest(mTestDbHelper));
        clearTokenDao(mSpyAuthTokenDao);

        mTrainingTaskDao = FederatedTrainingTaskDao.getInstanceForTest(mTestDbHelper);
        // Force delete any existing data in the dao
        mTrainingTaskDao.deleteExpiredTaskHistory(/* deleteTime= */ Long.MAX_VALUE);
        mSpyService = spy(new DeleteExpiredJobService(new TestInjector()));

        mJobScheduler = sContext.getSystemService(JobScheduler.class);
        mJobScheduler.cancel(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
    }

    @After
    public void tearDown() {
        mTestDbHelper.getWritableDatabase().close();
        mTestDbHelper.getReadableDatabase().close();
        mTestDbHelper.close();
    }

    @Test
    public void testDefaultNoArgConstructor() {
        DeleteExpiredJobService instance = new DeleteExpiredJobService();
        assertNotNull("default no-arg constructor is required by JobService", instance);
    }

    @Test
    public void deleteExpiredAuthToken_success() throws Exception {
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken(TEST_EXPIRED_TOKEN1));
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken(TEST_EXPIRED_TOKEN2));
        mSpyAuthTokenDao.insertAuthorizationToken(createUnexpiredAuthToken(TEST_UNEXPIRED_TOKEN));

        mSpyService.onStartJob(mock(JobParameters.class));

        // TODO(b/326444021): remove thread sleep after use JobServiceCallback.
        Thread.sleep(THREAD_SLEEP.toMillis());
        verify(mSpyService).jobFinished(any(), eq(false));
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                mTestDbHelper.getReadableDatabase(),
                                OdpAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(1);
    }

    @Test
    public void deleteExpiredAuthToken_failure() throws Exception {
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken(TEST_EXPIRED_TOKEN1));
        mSpyAuthTokenDao.insertAuthorizationToken(createExpiredAuthToken(TEST_EXPIRED_TOKEN2));
        mSpyAuthTokenDao.insertAuthorizationToken(createUnexpiredAuthToken(TEST_UNEXPIRED_TOKEN));
        doThrow(new SQLiteException("exception"))
                .when(mSpyAuthTokenDao)
                .deleteExpiredAuthorizationTokens();

        mSpyService.onStartJob(mock(JobParameters.class));

        // TODO(b/326444021): remove thread sleep after use JobServiceCallback.
        Thread.sleep(THREAD_SLEEP.toMillis());
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mSpyAuthTokenDao).deleteExpiredAuthorizationTokens();
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                mTestDbHelper.getReadableDatabase(),
                                OdpAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE))
                .isEqualTo(3);
    }

    @Test
    public void deletedExpiredTaskHistory_success() throws Exception {
        // Ensure the task history table is empty prior to the test.
        assertThat(
                        DatabaseUtils.queryNumEntries(
                                mTestDbHelper.getReadableDatabase(),
                                TaskHistoryContract.TaskHistoryEntry.TABLE_NAME))
                .isEqualTo(0);
        // record1 is expired because its contribution time (100) < TEST_CURRENT_TIME (400) -
        // TEST_TTL (200). The DeleteExpiredJobService should delete it and remove it from the
        // TrainingTaskDao.
        TaskHistory record1 =
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setTaskId(TASK_ID)
                        .setContributionRound(15)
                        .setTotalParticipation(3)
                        .setContributionTime(100)
                        .build();
        TaskHistory record2 =
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setTaskId(TASK_ID)
                        .setContributionRound(15)
                        .setTotalParticipation(3)
                        .setContributionTime(300)
                        .build();

        assertTrue(mTrainingTaskDao.updateOrInsertTaskHistory(record1));
        assertTrue(mTrainingTaskDao.updateOrInsertTaskHistory(record2));
        assertThat(mTrainingTaskDao.getTaskHistoryList(JOB_ID, POPULATION_NAME, TASK_ID))
                .containsExactly(record1, record2);

        mSpyService.onStartJob(mock(JobParameters.class));

        // TODO(b/326444021): remove thread sleep after use JobServiceCallback.
        Thread.sleep(THREAD_SLEEP.toMillis());
        verify(mSpyService).jobFinished(any(), eq(false));
        assertThat(mTrainingTaskDao.getTaskHistoryList(JOB_ID, POPULATION_NAME, TASK_ID))
                .containsExactly(record2);
    }

    @Test
    public void enableKillSwitch() {
        when(mMockFlag.getGlobalKillSwitch()).thenReturn(true);
        doReturn(mJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);

        assertThat(
                        DeleteExpiredJobService.scheduleJobIfNeeded(
                                sContext, FlagsFactory.getFlags(), /* forceSchedule= */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);

        assertNotNull(mJobScheduler.getPendingJob(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID));
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());

        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        assertTrue(result);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mSpyAuthTokenDao, never()).deleteExpiredAuthorizationTokens();
        assertNull(mJobScheduler.getPendingJob(FederatedComputeJobInfo.DELETE_EXPIRED_JOB_ID));
    }

    @Test
    @MockStatic(FederatedComputeJobScheduler.class)
    public void testOnStartJob_speEnabled() {
        // Enable SPE.
        when(mMockFlag.getSpePilotJobEnabled()).thenReturn(true);

        // Mock OdpJobScheduler to not actually schedule the job.
        FederatedComputeJobScheduler mockedScheduler = mock(FederatedComputeJobScheduler.class);
        doReturn(mockedScheduler).when(() -> FederatedComputeJobScheduler.getInstance(any()));

        assertThat(mSpyService.onStartJob(mock(JobParameters.class))).isFalse();

        // Verify SPE scheduler has rescheduled the job.
        verify(mockedScheduler).schedule(any(), any());
    }

    @Test
    public void testDefaultInjector() {
        DeleteExpiredJobService.Injector injector = new DeleteExpiredJobService.Injector();

        assertThat(injector.getExecutor())
                .isEqualTo(FederatedComputeExecutors.getBackgroundExecutor());
        assertThat(injector.getODPAuthorizationTokenDao(sContext))
                .isEqualTo(
                        OdpAuthorizationTokenDao.getInstance(
                                FederatedComputeDbHelper.getInstance(sContext)));
    }

    @Test
    public void deleteCacheDirectory_success() throws Exception {
        when(mMockFlag.getTempFileTtlMillis()).thenReturn(0L);
        createTempFile("input", ".ckp");
        createTempFile("output", ".ckp");
        // Verify cache directory has created files.
        long matchFileCount =
                Arrays.stream(mContext.getCacheDir().listFiles())
                        .filter(f -> mSpyService.isFileMatched(f.getName()))
                        .count();
        assertThat(matchFileCount).isEqualTo(2);

        mSpyService.onStartJob(mock(JobParameters.class));

        Thread.sleep(THREAD_SLEEP.toMillis());
        verify(mSpyService).jobFinished(any(), eq(false));

        // Verify cache directory is empty after deletion job.
        File[] files = mContext.getCacheDir().listFiles();
        matchFileCount =
                Arrays.stream(mContext.getCacheDir().listFiles())
                        .filter(f -> mSpyService.isFileMatched(f.getName()))
                        .count();
        assertThat(matchFileCount).isEqualTo(0);
    }

    @Test
    public void deleteCacheDirectory_fileNotMatch() throws Exception {
        when(mMockFlag.getTempFileTtlMillis()).thenReturn(0L);
        createTempFile("metadata", ".ckp");
        assertThat(mContext.getCacheDir().listFiles()).isNotEmpty();

        mSpyService.onStartJob(mock(JobParameters.class));

        Thread.sleep(THREAD_SLEEP.toMillis());
        verify(mSpyService).jobFinished(any(), eq(false));

        // Verify cache directory is empty after deletion job.
        File[] files = mContext.getCacheDir().listFiles();
        long matchFileCount =
                Arrays.stream(mContext.getCacheDir().listFiles())
                        .filter(f -> mSpyService.isFileMatched(f.getName()))
                        .count();
        assertThat(files.length).isAtLeast(1);
        assertThat(matchFileCount).isEqualTo(0);
    }

    private static void clearTokenDao(OdpAuthorizationTokenDao tokenDao) {
        // Force clear any existing auth tokens before tests

        for (String ownerIdentifier : TEST_OWNER_IDS) {
            tokenDao.deleteAuthorizationToken(ownerIdentifier);
        }
    }

    private static OdpAuthorizationToken createExpiredAuthToken(String ownerId) {
        // Create an already expired token with expiry time in the past.
        long now = MonotonicClock.getInstance().currentTimeMillis();
        OdpAuthorizationToken token =
                new OdpAuthorizationToken.Builder()
                        .setAuthorizationToken(UUID.randomUUID().toString())
                        .setOwnerIdentifier(ownerId)
                        .setCreationTime(now)
                        .setExpiryTime(now - 10)
                        .build();
        return token;
    }

    private static OdpAuthorizationToken createUnexpiredAuthToken(String ownerId) {
        // Create an unexpired token with a TTL of 24 hours.
        long now = MonotonicClock.getInstance().currentTimeMillis();
        long ttl = 24 * 60 * 60 * 1000L;
        OdpAuthorizationToken token =
                new OdpAuthorizationToken.Builder()
                        .setAuthorizationToken(UUID.randomUUID().toString())
                        .setOwnerIdentifier(ownerId)
                        .setCreationTime(now)
                        .setExpiryTime(now + ttl)
                        .build();
        return token;
    }

    private class TestInjector extends DeleteExpiredJobService.Injector {
        @Override
        ListeningExecutorService getExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        OdpAuthorizationTokenDao getODPAuthorizationTokenDao(Context context) {
            return mSpyAuthTokenDao;
        }

        @Override
        FederatedTrainingTaskDao getTrainingTaskDao(Context context) {
            return mTrainingTaskDao;
        }

        @Override
        File getCacheDir(Context context) {
            return mContext.getCacheDir();
        }

        @Override
        Clock getClock() {
            return mClock;
        }

        @Override
        Flags getFlags() {
            return mMockFlag;
        }

        @Override
        long getMinimumTempFileTtlMillis() {
            return 0;
        }
    }
}
