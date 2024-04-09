/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QueryTest {
    @Test
    public void testBuilder() {
        long queryId = 1;
        byte[] queryData = "data".getBytes();
        ComponentName service = new ComponentName("servicePkg", "cls");
        long timeMillis = 1;
        Query query1 = new Query.Builder(
                timeMillis,
                "com.app",
                service,
                "cert",
                queryData)
                .setQueryId(queryId)
                .build();
        assertEquals(query1.getQueryId(), queryId);
        assertArrayEquals(query1.getQueryData(), queryData);
        assertEquals(query1.getService(), service);
        assertEquals(query1.getTimeMillis(), timeMillis);
        assertEquals(query1.getAppPackageName(), "com.app");
        assertEquals(query1.getServiceCertDigest(), "cert");
    }

    @Test
    public void testBuildTwiceThrows() {
        long queryId = 1;
        byte[] queryData = "data".getBytes();
        ComponentName service = new ComponentName("servicePkg", "cls");
        long timeMillis = 1;
        Query.Builder builder = new Query.Builder(
                timeMillis,
                "com.app",
                service,
                "cert",
                queryData)
                .setQueryId(queryId);
        builder.build();
        assertThrows(IllegalStateException.class, () -> builder.build());
    }
}
