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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public class EventsDaoTest {
    private static final int EVENT_TYPE_B2D = 1;
    private static final int EVENT_TYPE_CLICK = 2;
    private static final String TASK_IDENTIFIER = "taskIdentifier";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private EventsDao mDao;
    private final Event mTestEvent = new Event.Builder()
            .setType(EVENT_TYPE_B2D)
            .setEventData("event".getBytes(StandardCharsets.UTF_8))
            .setServicePackageName(mContext.getPackageName())
            .setQueryId(1L)
            .setTimeMillis(1L)
            .setRowIndex(0)
            .build();

    private final Query mTestQuery = new Query.Builder()
            .setTimeMillis(1L)
            .setServicePackageName(mContext.getPackageName())
            .setQueryData("query".getBytes(StandardCharsets.UTF_8))
            .build();

    private final EventState mEventState = new EventState.Builder()
            .setTaskIdentifier(TASK_IDENTIFIER)
            .setServicePackageName(mContext.getPackageName())
            .setQueryId(1L)
            .setEventId(1L)
            .build();

    @Before
    public void setup() {
        mDao = EventsDao.getInstanceForTest(mContext);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testInsertQueryAndEvent() {
        assertEquals(1, mDao.insertQuery(mTestQuery));
        assertEquals(1, mDao.insertEvent(mTestEvent));
        Event testEvent = new Event.Builder()
                .setType(EVENT_TYPE_CLICK)
                .setEventData("event".getBytes(StandardCharsets.UTF_8))
                .setServicePackageName(mContext.getPackageName())
                .setQueryId(1L)
                .setTimeMillis(1L)
                .setRowIndex(0)
                .build();
        assertEquals(2, mDao.insertEvent(testEvent));
    }

    @Test
    public void testInsertAndReadEventState() {
        assertTrue(mDao.updateOrInsertEventState(mEventState));
        assertEquals(mEventState, mDao.getEventState(TASK_IDENTIFIER, mContext.getPackageName()));
        EventState testEventState = new EventState.Builder()
                .setTaskIdentifier(TASK_IDENTIFIER)
                .setServicePackageName(mContext.getPackageName())
                .setQueryId(5L)
                .setEventId(7L)
                .build();
        assertTrue(mDao.updateOrInsertEventState(testEventState));
        assertEquals(testEventState,
                mDao.getEventState(TASK_IDENTIFIER, mContext.getPackageName()));
    }

    @Test
    public void testReadEventStateNoEventState() {
        assertNull(mDao.getEventState(TASK_IDENTIFIER, mContext.getPackageName()));
    }


    @Test
    public void testInsertEventFKError() {
        assertEquals(-1, mDao.insertEvent(mTestEvent));
    }

    @Test
    public void testInsertQueryId() {
        assertEquals(1, mDao.insertQuery(mTestQuery));
        assertEquals(2, mDao.insertQuery(mTestQuery));
    }

    @Test
    public void testInsertEventExistingKey() {
        assertEquals(1, mDao.insertQuery(mTestQuery));
        assertEquals(1, mDao.insertEvent(mTestEvent));
        assertEquals(-1, mDao.insertEvent(mTestEvent));
    }

}
