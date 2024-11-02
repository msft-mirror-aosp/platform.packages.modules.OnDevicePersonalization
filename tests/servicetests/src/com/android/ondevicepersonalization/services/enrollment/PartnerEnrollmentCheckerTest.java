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

package com.android.ondevicepersonalization.services.enrollment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.StableFlags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
public class PartnerEnrollmentCheckerTest {
    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(FlagsFactory.class)
            .spyStatic(StableFlags.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    private String mCallerAppAllowList;
    private String mIsolatedServiceAllowList;

    private Flags mTestFlags = new Flags() {
        @Override public String getCallerAppAllowList() {
            return mCallerAppAllowList;
        }
        @Override public String getIsolatedServiceAllowList() {
            return mIsolatedServiceAllowList;
        }
    };

    @Before
    public void setup() throws Exception {
        ExtendedMockito.doReturn(mTestFlags).when(FlagsFactory::getFlags);
    }

    @Test
    public void testIsCallerAppEnrolled() {
        mCallerAppAllowList = "app1,app2,app3,app5:certapp5";
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled("app1"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled("app"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled("app4"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled("app5"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(null));

        mCallerAppAllowList = "*";
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled("random"));
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled(""));
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled(null));

        mCallerAppAllowList = "";
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled("random"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(null));
    }

    @Test
    public void testIsIsolatedServiceEnrolled() {
        mIsolatedServiceAllowList = "svc1,svc2,svc3,svc5:certsvc5";
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("svc1"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("svc"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("svc4"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("svc5"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(null));

        mIsolatedServiceAllowList = "*";
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("random"));
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(""));
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(null));

        mIsolatedServiceAllowList = "";
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("random"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(null));
    }

}
