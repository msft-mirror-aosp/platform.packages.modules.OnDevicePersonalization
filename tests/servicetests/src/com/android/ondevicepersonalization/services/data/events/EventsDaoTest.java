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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class EventsDaoTest {
    private static final int EVENT_TYPE_B2D = 1;
    private static final int EVENT_TYPE_CLICK = 2;
    private static final String APP_NAME = "com.app";
    private static final String TASK_IDENTIFIER = "taskIdentifier";
    private static final String SERVICE_CLASS = "TestClass";
    private static final Context sTestContext = ApplicationProvider.getApplicationContext();
    private static final ComponentName TEST_SERVICE_COMPONENT_NAME =
            new ComponentName(sTestContext.getPackageName(), SERVICE_CLASS);
    private static final String TEST_SERVICE_CERT = "AABBCCDD";
    private static final byte[] TEST_QUERY_DATA = "query".getBytes(StandardCharsets.UTF_8);

    private static final byte[] TEST_EVENT_DATA = "event".getBytes(StandardCharsets.UTF_8);

    private static final Event TEST_EVENT =
            new Event.Builder()
                    .setType(EVENT_TYPE_B2D)
                    .setEventData(TEST_EVENT_DATA)
                    .setService(TEST_SERVICE_COMPONENT_NAME)
                    .setQueryId(1L)
                    .setTimeMillis(1L)
                    .setRowIndex(0)
                    .build();
    private static final Query TEST_QUERY =
            new Query.Builder(
                            1L,
                            APP_NAME,
                            TEST_SERVICE_COMPONENT_NAME,
                            TEST_SERVICE_CERT,
                            TEST_QUERY_DATA)
                    .build();
    private static final EventState TEST_EVENT_STATE =
            new EventState.Builder()
                    .setTaskIdentifier(TASK_IDENTIFIER)
                    .setService(TEST_SERVICE_COMPONENT_NAME)
                    .setToken(new byte[] {1})
                    .build();
    private EventsDao mDao;

    @Before
    public void setup() {
        mDao = EventsDao.getInstanceForTest(sTestContext);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(sTestContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testInsertQueryAndEvent() {
        assertEquals(1, mDao.insertQuery(TEST_QUERY));
        assertEquals(1, mDao.insertEvent(TEST_EVENT));
        Event testEvent =
                new Event.Builder()
                        .setType(EVENT_TYPE_CLICK)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(1L)
                        .setTimeMillis(1L)
                        .setRowIndex(0)
                        .build();
        assertEquals(2, mDao.insertEvent(testEvent));
    }

    @Test
    public void testInsertEvents() {
        mDao.insertQuery(TEST_QUERY);
        Event testEvent =
                new Event.Builder()
                        .setType(EVENT_TYPE_CLICK)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(1L)
                        .setTimeMillis(1L)
                        .setRowIndex(0)
                        .build();
        List<Event> events = new ArrayList<>();
        events.add(TEST_EVENT);
        events.add(testEvent);
        assertTrue(mDao.insertEvents(events));
    }

    @Test
    public void testInsertEventsFalse() {
        List<Event> events = new ArrayList<>();
        events.add(TEST_EVENT);
        assertFalse(mDao.insertEvents(events));
    }

    @Test
    public void testInsertAndReadEventState() {
        assertTrue(mDao.updateOrInsertEventState(TEST_EVENT_STATE));
        assertEquals(
                TEST_EVENT_STATE, mDao.getEventState(TASK_IDENTIFIER, TEST_SERVICE_COMPONENT_NAME));
        EventState testEventState =
                new EventState.Builder()
                        .setTaskIdentifier(TASK_IDENTIFIER)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setToken(new byte[] {100})
                        .build();
        assertTrue(mDao.updateOrInsertEventState(testEventState));
        assertEquals(
                testEventState, mDao.getEventState(TASK_IDENTIFIER, TEST_SERVICE_COMPONENT_NAME));
    }


    @Test
    public void testInsertAndReadEventStatesTransaction() {
        EventState testEventState =
                new EventState.Builder()
                        .setTaskIdentifier(TASK_IDENTIFIER)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setToken(new byte[] {100})
                        .build();
        List<EventState> eventStates = new ArrayList<>();
        eventStates.add(TEST_EVENT_STATE);
        eventStates.add(testEventState);
        assertTrue(mDao.updateOrInsertEventStatesTransaction(eventStates));
        assertEquals(
                testEventState, mDao.getEventState(TASK_IDENTIFIER, TEST_SERVICE_COMPONENT_NAME));
    }
    @Test
    public void testDeleteEventState() {
        ComponentName serviceA = new ComponentName("packageA", "clsA");
        mDao.updateOrInsertEventState(TEST_EVENT_STATE);
        EventState testEventState = new EventState.Builder()
                .setTaskIdentifier(TASK_IDENTIFIER)
                .setService(serviceA)
                .setToken(new byte[]{100})
                .build();
        mDao.updateOrInsertEventState(testEventState);
        mDao.deleteEventState(TEST_SERVICE_COMPONENT_NAME);
        assertEquals(testEventState,
                mDao.getEventState(TASK_IDENTIFIER, serviceA));
        assertNull(mDao.getEventState(TASK_IDENTIFIER, TEST_SERVICE_COMPONENT_NAME));

        mDao.deleteEventState(serviceA);
        assertNull(mDao.getEventState(TASK_IDENTIFIER, serviceA));
    }

    @Test
    public void testDeleteEventsAndQueries() {
        mDao.insertQuery(TEST_QUERY);
        mDao.insertEvent(TEST_EVENT);
        long queryId2 = mDao.insertQuery(TEST_QUERY);
        Event testEvent =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId2)
                        .setTimeMillis(3L)
                        .setRowIndex(0)
                        .build();
        long eventId2 = mDao.insertEvent(testEvent);

        Query testQuery =
                new Query.Builder(
                                5L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId3 = mDao.insertQuery(testQuery);

        // Delete query1 event1. Assert query2 and event2 still exist.
        mDao.deleteEventsAndQueries(2);
        List<JoinedEvent> joinedEventList = mDao.readAllNewRows(0, 0);
        assertThat(joinedEventList).hasSize(3);
        assertEquals(
                createExpectedJoinedEvent(testEvent, TEST_QUERY, eventId2, queryId2),
                joinedEventList.get(0));
        assertEquals(
                createExpectedJoinedEvent(null, TEST_QUERY, 0, queryId2), joinedEventList.get(1));
        assertEquals(createExpectedJoinedEvent(null, testQuery, 0, queryId3),
                joinedEventList.get(2));

        // Delete query2 event2. Assert query3 still exist.
        mDao.deleteEventsAndQueries(4);
        joinedEventList = mDao.readAllNewRows(0, 0);
        assertThat(joinedEventList).hasSize(1);
        assertEquals(createExpectedJoinedEvent(null, testQuery, 0, queryId3),
                joinedEventList.get(0));
    }


    @Test
    public void testReadAllNewRowsEmptyTable() {
        List<JoinedEvent> joinedEventList = mDao.readAllNewRows(0, 0);
        assertThat(joinedEventList).isEmpty();
    }

    @Test
    public void testReadAllNewRowsForPackageEmptyTable() {
        List<JoinedEvent> joinedEventList =
                mDao.readAllNewRowsForPackage(TEST_SERVICE_COMPONENT_NAME, 0, 0);
        assertThat(joinedEventList).isEmpty();
    }

    @Test
    public void testReadAllNewRowsForPackage() {
        long queryId1 = mDao.insertQuery(TEST_QUERY);
        long eventId1 = mDao.insertEvent(TEST_EVENT);
        long queryId2 = mDao.insertQuery(TEST_QUERY);
        ComponentName serviceA = new ComponentName("packageA", "clsA");

        Query packageAQuery =
                new Query.Builder(1L, APP_NAME, serviceA, TEST_SERVICE_CERT, TEST_QUERY_DATA)
                        .build();
        long queryId3 = mDao.insertQuery(packageAQuery);

        Event packageAEvent =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(serviceA)
                        .setQueryId(queryId3)
                        .setTimeMillis(1L)
                        .setRowIndex(0)
                        .build();
        long eventId2 = mDao.insertEvent(packageAEvent);

        List<JoinedEvent> joinedEventList =
                mDao.readAllNewRowsForPackage(TEST_SERVICE_COMPONENT_NAME, 0, 0);
        assertThat(joinedEventList).hasSize(3);
        assertEquals(
                createExpectedJoinedEvent(TEST_EVENT, TEST_QUERY, eventId1, queryId1),
                joinedEventList.get(0));
        assertEquals(
                createExpectedJoinedEvent(null, TEST_QUERY, 0, queryId1), joinedEventList.get(1));
        assertEquals(
                createExpectedJoinedEvent(null, TEST_QUERY, 0, queryId2), joinedEventList.get(2));

        joinedEventList =
                mDao.readAllNewRowsForPackage(TEST_SERVICE_COMPONENT_NAME, eventId1, queryId2);
        assertThat(joinedEventList).isEmpty();

        joinedEventList =
                mDao.readAllNewRowsForPackage(TEST_SERVICE_COMPONENT_NAME, eventId1, queryId1);
        assertThat(joinedEventList).hasSize(1);
        assertEquals(
                createExpectedJoinedEvent(null, TEST_QUERY, 0, queryId2), joinedEventList.get(0));
    }

    @Test
    public void testReadAllNewRows() {
        long queryId1 = mDao.insertQuery(TEST_QUERY);
        long eventId1 = mDao.insertEvent(TEST_EVENT);
        long queryId2 = mDao.insertQuery(TEST_QUERY);
        ComponentName serviceA = new ComponentName("packageA", "clsA");

        Query packageAQuery =
                new Query.Builder(1L, APP_NAME, serviceA, TEST_SERVICE_CERT, TEST_QUERY_DATA)
                        .build();
        long queryId3 = mDao.insertQuery(packageAQuery);

        Event packageAEvent =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(serviceA)
                        .setQueryId(queryId3)
                        .setTimeMillis(1L)
                        .setRowIndex(0)
                        .build();
        long eventId2 = mDao.insertEvent(packageAEvent);

        List<JoinedEvent> joinedEventList = mDao.readAllNewRows(0, 0);
        assertThat(joinedEventList).hasSize(5);
        assertEquals(
                createExpectedJoinedEvent(TEST_EVENT, TEST_QUERY, eventId1, queryId1),
                joinedEventList.get(0));
        assertEquals(createExpectedJoinedEvent(packageAEvent, packageAQuery, eventId2, queryId3),
                joinedEventList.get(1));
        assertEquals(
                createExpectedJoinedEvent(null, TEST_QUERY, 0, queryId1), joinedEventList.get(2));
        assertEquals(
                createExpectedJoinedEvent(null, TEST_QUERY, 0, queryId2), joinedEventList.get(3));
        assertEquals(createExpectedJoinedEvent(null, packageAQuery, 0, queryId3),
                joinedEventList.get(4));

        joinedEventList = mDao.readAllNewRows(eventId2, queryId3);
        assertThat(joinedEventList).isEmpty();

        joinedEventList = mDao.readAllNewRows(eventId2, queryId2);
        assertThat(joinedEventList).hasSize(1);
        assertEquals(createExpectedJoinedEvent(null, packageAQuery, 0, queryId3),
                joinedEventList.get(0));
    }

    @Test
    public void testReadAllQueries() {
        ComponentName otherService = new ComponentName("package", "cls");
        String otherCert = "11223344";
        Query query1 =
                new Query.Builder(
                                1L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 =
                new Query.Builder(
                                10L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId2 = mDao.insertQuery(query2);
        Query query3 =
                new Query.Builder(
                                100L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId3 = mDao.insertQuery(query3);
        Query query4 =
                new Query.Builder(100L, APP_NAME, otherService, otherCert, TEST_QUERY_DATA).build();
        long queryId4 = mDao.insertQuery(query4);

        List<Query> result = mDao.readAllQueries(0, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(3);
        assertEquals(queryId1, (long) result.get(0).getQueryId());
        assertEquals(queryId2, (long) result.get(1).getQueryId());
        assertEquals(queryId3, (long) result.get(2).getQueryId());

        result = mDao.readAllQueries(0, 1000, otherService);
        assertThat(result).hasSize(1);
        assertEquals(queryId4, (long) result.get(0).getQueryId());

        result = mDao.readAllQueries(500, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).isEmpty();

        result = mDao.readAllQueries(5, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(2);
        assertEquals(queryId2, (long) result.get(0).getQueryId());
        assertEquals(queryId3, (long) result.get(1).getQueryId());
    }

    @Test
    public void testReadAllEventIds() {
        Query query1 =
                new Query.Builder(
                                1L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 =
                new Query.Builder(
                                10L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId2 = mDao.insertQuery(query2);
        Query query3 =
                new Query.Builder(
                                100L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId3 = mDao.insertQuery(query3);

        Event event1 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId1)
                        .setTimeMillis(2L)
                        .setRowIndex(0)
                        .build();
        long eventId1 = mDao.insertEvent(event1);
        Event event2 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId2)
                        .setTimeMillis(11L)
                        .setRowIndex(0)
                        .build();
        long eventId2 = mDao.insertEvent(event2);
        Event event3 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId3)
                        .setTimeMillis(101L)
                        .setRowIndex(0)
                        .build();
        long eventId3 = mDao.insertEvent(event3);

        List<Long> result = mDao.readAllEventIds(0, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(3);
        assertEquals(eventId1, (long) result.get(0));
        assertEquals(eventId2, (long) result.get(1));
        assertEquals(eventId3, (long) result.get(2));

        result = mDao.readAllEventIds(0, 1000, new ComponentName("pkg", "cls"));
        assertThat(result).isEmpty();

        result = mDao.readAllEventIds(500, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).isEmpty();

        result = mDao.readAllEventIds(5, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(2);
        assertEquals(eventId2, (long) result.get(0));
        assertEquals(eventId3, (long) result.get(1));
    }

    @Test
    public void testReadEventIdsForRequest() {
        Query query1 =
                new Query.Builder(
                                1L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 =
                new Query.Builder(
                                10L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId2 = mDao.insertQuery(query2);

        Event event1 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId1)
                        .setTimeMillis(2L)
                        .setRowIndex(0)
                        .build();
        long eventId1 = mDao.insertEvent(event1);
        Event event2 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId2)
                        .setTimeMillis(11L)
                        .setRowIndex(0)
                        .build();
        long eventId2 = mDao.insertEvent(event2);
        Event event3 =
                new Event.Builder()
                        .setType(EVENT_TYPE_CLICK)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId2)
                        .setTimeMillis(101L)
                        .setRowIndex(0)
                        .build();
        long eventId3 = mDao.insertEvent(event3);

        List<Long> result = mDao.readAllEventIdsForQuery(queryId1, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(1);
        assertEquals(eventId1, (long) result.get(0));

        result = mDao.readAllEventIdsForQuery(queryId2, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(2);
        assertEquals(eventId2, (long) result.get(0));
        assertEquals(eventId3, (long) result.get(1));

        result = mDao.readAllEventIdsForQuery(1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).isEmpty();

        result = mDao.readAllEventIdsForQuery(queryId1, new ComponentName("pkg", "cls"));
        assertThat(result).isEmpty();
    }

    @Test
    public void testReadJoinedEvents() {
        Query query1 =
                new Query.Builder(
                                1L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId1 = mDao.insertQuery(query1);
        Query query2 =
                new Query.Builder(
                                10L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId2 = mDao.insertQuery(query2);
        Query query3 =
                new Query.Builder(
                                100L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .build();
        long queryId3 = mDao.insertQuery(query3);

        Event event1 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId1)
                        .setTimeMillis(2L)
                        .setRowIndex(0)
                        .build();
        long eventId1 = mDao.insertEvent(event1);
        Event event2 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId2)
                        .setTimeMillis(11L)
                        .setRowIndex(0)
                        .build();
        long eventId2 = mDao.insertEvent(event2);
        Event event3 =
                new Event.Builder()
                        .setType(EVENT_TYPE_B2D)
                        .setEventData(TEST_EVENT_DATA)
                        .setService(TEST_SERVICE_COMPONENT_NAME)
                        .setQueryId(queryId3)
                        .setTimeMillis(101L)
                        .setRowIndex(0)
                        .build();
        long eventId3 = mDao.insertEvent(event3);

        List<JoinedEvent> result = mDao.readJoinedTableRows(0, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(3);
        assertEquals(createExpectedJoinedEvent(event1, query1, eventId1, queryId1), result.get(0));
        assertEquals(createExpectedJoinedEvent(event2, query2, eventId2, queryId2), result.get(1));
        assertEquals(createExpectedJoinedEvent(event3, query3, eventId3, queryId3), result.get(2));

        result = mDao.readJoinedTableRows(0, 1000, new ComponentName("pkg", "cls"));
        assertThat(result).isEmpty();

        result = mDao.readJoinedTableRows(500, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).isEmpty();

        result = mDao.readJoinedTableRows(5, 1000, TEST_SERVICE_COMPONENT_NAME);
        assertThat(result).hasSize(2);

        assertEquals(createExpectedJoinedEvent(event2, query2, eventId2, queryId2), result.get(0));
        assertEquals(createExpectedJoinedEvent(event3, query3, eventId3, queryId3), result.get(1));
    }

    @Test
    public void testReadSingleQuery() {
        Query query1 =
                new Query.Builder(
                                1L,
                                APP_NAME,
                                TEST_SERVICE_COMPONENT_NAME,
                                TEST_SERVICE_CERT,
                                TEST_QUERY_DATA)
                        .setQueryId(1)
                        .build();
        mDao.insertQuery(query1);
        Query query2 = mDao.readSingleQueryRow(1, TEST_SERVICE_COMPONENT_NAME);
        assertEquals(query1.getQueryId(), query2.getQueryId());
        assertEquals(query1.getTimeMillis(), query2.getTimeMillis());
        assertEquals(query1.getAppPackageName(), query2.getAppPackageName());
        assertEquals(query1.getService(), query2.getService());
        assertEquals(query1.getServiceCertDigest(), query2.getServiceCertDigest());
        assertArrayEquals(query1.getQueryData(), query2.getQueryData());
        assertNull(mDao.readSingleQueryRow(100, TEST_SERVICE_COMPONENT_NAME));
        assertNull(mDao.readSingleQueryRow(1, new ComponentName("pkg", "cls")));
    }

    @Test
    public void testReadSingleJoinedTableRow() {
        mDao.insertQuery(TEST_QUERY);
        mDao.insertEvent(TEST_EVENT);
        assertEquals(
                createExpectedJoinedEvent(TEST_EVENT, TEST_QUERY, 1, 1),
                mDao.readSingleJoinedTableRow(1, TEST_SERVICE_COMPONENT_NAME));
        assertNull(mDao.readSingleJoinedTableRow(100, TEST_SERVICE_COMPONENT_NAME));
        assertNull(mDao.readSingleJoinedTableRow(1, new ComponentName("pkg", "cls")));
    }

    @Test
    public void testReadEventStateNoEventState() {
        assertNull(mDao.getEventState(TASK_IDENTIFIER, TEST_SERVICE_COMPONENT_NAME));
    }


    @Test
    public void testInsertEventFKError() {
        assertEquals(-1, mDao.insertEvent(TEST_EVENT));
    }

    @Test
    public void testInsertQueryId() {
        assertEquals(1, mDao.insertQuery(TEST_QUERY));
        assertEquals(2, mDao.insertQuery(TEST_QUERY));
    }

    @Test
    public void testInsertEventExistingKey() {
        assertEquals(1, mDao.insertQuery(TEST_QUERY));
        assertEquals(1, mDao.insertEvent(TEST_EVENT));
        assertEquals(2, mDao.insertEvent(TEST_EVENT));
    }

    @Test
    public void testHasExistingEvent() {
        assertEquals(1, mDao.insertQuery(TEST_QUERY));
        assertEquals(1, mDao.insertEvent(TEST_EVENT));
        assertTrue(
                mDao.hasEvent(
                        TEST_EVENT.getQueryId(),
                        TEST_EVENT.getType(),
                        TEST_EVENT.getRowIndex(),
                        TEST_EVENT.getService()));
    }

    private JoinedEvent createExpectedJoinedEvent(Event event, Query query, long eventId,
            long queryId) {
        if (event == null) {
            return new JoinedEvent.Builder()
                    .setService(query.getService())
                    .setQueryData(query.getQueryData())
                    .setQueryId(queryId)
                    .setQueryTimeMillis(query.getTimeMillis())
                    .build();
        }
        return new JoinedEvent.Builder()
                .setService(event.getService())
                .setQueryId(queryId)
                .setEventId(eventId)
                .setRowIndex(event.getRowIndex())
                .setType(event.getType())
                .setEventTimeMillis(event.getTimeMillis())
                .setQueryTimeMillis(query.getTimeMillis())
                .setEventData(event.getEventData())
                .setQueryData(query.getQueryData())
                .build();
    }
}
