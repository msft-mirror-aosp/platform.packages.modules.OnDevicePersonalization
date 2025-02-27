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

package com.android.ondevicepersonalization.services;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.CalleeMetadata;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.aidl.IIsFeatureEnabledCallback;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

@RunWith(JUnit4.class)
public class FeatureStatusManagerTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = FeatureStatusManagerTest.class.getSimpleName();
    private static final long SERVICE_ENTRY_TIME = 100L;
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private volatile boolean mCallbackSuccess;
    private volatile int mResult;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        ExtendedMockito.doReturn(new TestFlags() {}).when(FlagsFactory::getFlags);
    }

    @Test
    public void testEnabledNonFlaggedFeature() {
        Set<String> nonFlaggedFeatures = new HashSet<>();
        nonFlaggedFeatures.add("featureName");
        FeatureStatusManager featureStatusManager =
                new FeatureStatusManager(
                        FlagsFactory.getFlags(),
                        new HashMap<>(),
                        nonFlaggedFeatures);
        assertThat(featureStatusManager.isFeatureEnabled("featureName")).isEqualTo(
                OnDevicePersonalizationManager.FEATURE_ENABLED);
    }

    @Test
    public void testEnabledFlaggedFeature() {
        Map<String, Supplier<Boolean>> flaggedFeatures = new HashMap<>();

        flaggedFeatures.put("featureName", (new TestFlags() {})::getEnabledFeature);
        FeatureStatusManager featureStatusManager =
                new FeatureStatusManager(
                        FlagsFactory.getFlags(),
                        flaggedFeatures,
                        new HashSet<>());
        assertThat(featureStatusManager.isFeatureEnabled("featureName")).isEqualTo(
                OnDevicePersonalizationManager.FEATURE_ENABLED);
    }

    @Test
    public void testDisabledFlaggedFeature() {
        Map<String, Supplier<Boolean>> flaggedFeatures = new HashMap<>();

        flaggedFeatures.put("featureName", (new TestFlags() {})::getDisabledFeature);
        FeatureStatusManager featureStatusManager =
                new FeatureStatusManager(
                        FlagsFactory.getFlags(),
                        flaggedFeatures,
                        new HashSet<>());
        assertThat(featureStatusManager.isFeatureEnabled("featureName")).isEqualTo(
                OnDevicePersonalizationManager.FEATURE_DISABLED);
    }

    @Test
    public void testUnsupportedFeature() {
        FeatureStatusManager featureStatusManager = new FeatureStatusManager(
                FlagsFactory.getFlags(),
                new HashMap<>(),
                new HashSet<>());
        assertThat(featureStatusManager.isFeatureEnabled("featureName")).isEqualTo(
                OnDevicePersonalizationManager.FEATURE_UNSUPPORTED);
    }

    @Test
    public void testGetFeatureStatusAndSendResult() throws InterruptedException {
        FeatureStatusManager.getFeatureStatusAndSendResult(
                "featureName",
                SERVICE_ENTRY_TIME,
                new TestIsFeatureEnabledCallback());
        mLatch.await();

        assertTrue(mCallbackSuccess);
        assertEquals(mResult, OnDevicePersonalizationManager.FEATURE_UNSUPPORTED);
    }

    class TestFlags implements Flags {

        public boolean getDisabledFeature() {
            return false;
        }

        public boolean getEnabledFeature() {
            return true;
        }
    }

    class TestIsFeatureEnabledCallback extends IIsFeatureEnabledCallback.Stub {
        @Override
        public void onResult(int result, CalleeMetadata calleeMetadata) {
            sLogger.d(TAG + " : onResult callback.");
            mCallbackSuccess = true;
            mResult = result;
            mLatch.countDown();
        }
    }
}
