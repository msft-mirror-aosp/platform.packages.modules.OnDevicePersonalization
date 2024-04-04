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

package com.android.ondevicepersonalization.services.data.user;

import static android.app.job.JobScheduler.RESULT_SUCCESS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.reset.ResetDataJobService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public final class UserPrivacyStatusTest {
    private UserPrivacyStatus mUserPrivacyStatus;
    private static final int CONTROL_RESET_STATUS_CODE = 5;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .addStaticMockFixtures(TestableDeviceConfig::new)
            .spyStatic(ResetDataJobService.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        PhFlagsTestUtil.disablePersonalizationStatusOverride();
        mUserPrivacyStatus = UserPrivacyStatus.getInstance();
        doReturn(RESULT_SUCCESS).when(ResetDataJobService::schedule);
    }

    @Test
    public void testEmptyUserControlCache() {
        assertFalse(mUserPrivacyStatus.isUserControlCacheValid());
    }

    @Test
    public void testUpdateControlWithValidCacheControlGiven() {
        mUserPrivacyStatus.updateUserControlCache(
                        UserPrivacyStatus.CONTROL_GIVEN_STATUS_CODE,
                        UserPrivacyStatus.CONTROL_GIVEN_STATUS_CODE);
        assertTrue(mUserPrivacyStatus.isUserControlCacheValid());
        assertTrue(mUserPrivacyStatus.isProtectedAudienceEnabled());
        assertTrue(mUserPrivacyStatus.isMeasurementEnabled());
        verify(ResetDataJobService::schedule, times(0));
    }

    @Test
    public void testUpdateControlWithValidCacheControlRevoked() {
        mUserPrivacyStatus.updateUserControlCache(
                    UserPrivacyStatus.CONTROL_REVOKED_STATUS_CODE,
                    UserPrivacyStatus.CONTROL_REVOKED_STATUS_CODE);
        assertTrue(mUserPrivacyStatus.isUserControlCacheValid());
        assertFalse(mUserPrivacyStatus.isProtectedAudienceEnabled());
        assertFalse(mUserPrivacyStatus.isMeasurementEnabled());
        verify(ResetDataJobService::schedule);
    }

    @Test
    public void testUpdateControlWithValidCacheDataReset() {
        mUserPrivacyStatus.updateUserControlCache(CONTROL_RESET_STATUS_CODE,
                        CONTROL_RESET_STATUS_CODE);
        assertTrue(mUserPrivacyStatus.isUserControlCacheValid());
        assertTrue(mUserPrivacyStatus.isProtectedAudienceEnabled());
        assertTrue(mUserPrivacyStatus.isMeasurementEnabled());
        verify(ResetDataJobService::schedule);
    }

    @Test
    public void testExpiredUserControlCache() {
        mUserPrivacyStatus.invalidateUserControlCacheForTesting();
        assertFalse(mUserPrivacyStatus.isUserControlCacheValid());
    }

    @After
    public void tearDown() {
        mUserPrivacyStatus.resetUserControlForTesting();
    }
}
