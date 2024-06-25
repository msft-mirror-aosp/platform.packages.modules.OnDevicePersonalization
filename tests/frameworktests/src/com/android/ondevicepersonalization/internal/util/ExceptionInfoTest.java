/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.internal.util;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Unit Tests of ExceptionInfo.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ExceptionInfoTest {
    @Test
    public void testExceptionRoundTrip() {
        Exception e = createException();
        assertNotNull(e);
        byte[] serialized = ExceptionInfo.toByteArray(e, 5);
        Exception e2 = ExceptionInfo.fromByteArray(serialized);
        assertNotNull(e2);
        assertThat(e2.getMessage(), matchesPattern(".*IllegalStateException.*Exception2.*"));
        assertNotNull(e2.getCause());
        assertThat(e2.getCause().getMessage(),
                matchesPattern(".*NullPointerException.*Exception1.*"));
        String stackTrace = getStackTrace(e2);
        assertThat(stackTrace, containsString("function2"));
        assertThat(stackTrace, containsString("function1"));
    }

    private Exception createException() {
        try {
            function2();
        } catch (Exception e) {
            return e;
        }
        return null;
    }

    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void function1() {
        throw new NullPointerException("Exception1");
    }

    private static void function2() {
        try {
            function1();
        } catch (Exception e) {
            throw new IllegalStateException("Exception2", e);
        }
    }
}
