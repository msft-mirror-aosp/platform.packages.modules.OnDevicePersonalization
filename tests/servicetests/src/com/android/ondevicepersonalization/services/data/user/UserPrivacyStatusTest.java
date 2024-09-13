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
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.reset.ResetDataJobService;
import com.android.ondevicepersonalization.services.util.DebugUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public final class UserPrivacyStatusTest {
    private UserPrivacyStatus mUserPrivacyStatus;
    private static final int CONTROL_RESET_STATUS_CODE = 5;

    @Spy
    private Flags mSpyFlags = spy(FlagsFactory.getFlags());

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(DebugUtils.class)
            .mockStatic(FlagsFactory.class)
            .spyStatic(ResetDataJobService.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        ExtendedMockito.doReturn(mSpyFlags).when(FlagsFactory::getFlags);
        when(mSpyFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mSpyFlags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)).thenReturn(false);
        when(mSpyFlags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE)).thenReturn(false);
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

    @Test
    public void testOverrideEnabledOnDeveloperModeOverrideTrue() {
        when(mSpyFlags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)).thenReturn(true);
        when(mSpyFlags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE)).thenReturn(true);
        doReturn(true).when(() -> DebugUtils.isDeveloperModeEnabled(any()));

        assertFalse(mUserPrivacyStatus.isProtectedAudienceAndMeasurementBothDisabled());
        assertTrue(mUserPrivacyStatus.isMeasurementEnabled());
        assertTrue(mUserPrivacyStatus.isProtectedAudienceEnabled());
        assertTrue(mUserPrivacyStatus.isPersonalizationStatusEnabled());
    }

    @Test
    public void testOverrideEnabledOnDeveloperModeOverrideFalse() {
        when(mSpyFlags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)).thenReturn(true);
        when(mSpyFlags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE)).thenReturn(false);
        doReturn(true).when(() -> DebugUtils.isDeveloperModeEnabled(any()));

        assertTrue(mUserPrivacyStatus.isProtectedAudienceAndMeasurementBothDisabled());
        assertFalse(mUserPrivacyStatus.isMeasurementEnabled());
        assertFalse(mUserPrivacyStatus.isProtectedAudienceEnabled());
        assertFalse(mUserPrivacyStatus.isPersonalizationStatusEnabled());
    }

    @Test
    public void testOverrideDisabledOnNonDeveloperMode() {
        when(mSpyFlags.getStableFlag(KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE)).thenReturn(true);
        when(mSpyFlags.getStableFlag(KEY_PERSONALIZATION_STATUS_OVERRIDE_VALUE)).thenReturn(true);
        doReturn(false).when(() -> DebugUtils.isDeveloperModeEnabled(any()));

        assertFalse(mUserPrivacyStatus.isPersonalizationStatusEnabled());
    }

    @After
    public void tearDown() {
        mUserPrivacyStatus.resetUserControlForTesting();
    }
}
