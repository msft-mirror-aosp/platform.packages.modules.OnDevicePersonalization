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

package com.android.ondevicepersonalization.services.util;

import static org.junit.Assert.assertEquals;

import android.ondevicepersonalization.ExecuteOutput;
import android.ondevicepersonalization.Metrics;
import android.ondevicepersonalization.SlotResult;

import com.android.ondevicepersonalization.services.fbs.Bid;
import com.android.ondevicepersonalization.services.fbs.EventFields;
import com.android.ondevicepersonalization.services.fbs.QueryFields;
import com.android.ondevicepersonalization.services.fbs.Slot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.ByteBuffer;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationFlatbufferUtilsTests {

    private static final double DELTA = 0.001;

    @Test
    public void testCreateEventData() {
        Metrics metrics = new Metrics.Builder().setLongValues(1, 2).setDoubleValues(1, 2).build();
        byte[] eventData = OnDevicePersonalizationFlatbufferUtils.createEventData(metrics);

        EventFields eventFields = EventFields.getRootAsEventFields(ByteBuffer.wrap(eventData));
        assertEquals(2, eventFields.metrics().longValuesLength());
        assertEquals(1, eventFields.metrics().longValues(0));
        assertEquals(2, eventFields.metrics().longValues(1));
        assertEquals(2, eventFields.metrics().doubleValuesLength());
        assertEquals(1, eventFields.metrics().doubleValues(0), DELTA);
        assertEquals(2, eventFields.metrics().doubleValues(1), DELTA);
    }

    @Test
    public void testCreateEventDataNullMetrics() {
        byte[] eventData = OnDevicePersonalizationFlatbufferUtils.createEventData(null);

        EventFields eventFields = EventFields.getRootAsEventFields(ByteBuffer.wrap(eventData));
        assertEquals(null, eventFields.metrics());
    }

    @Test
    public void testCreateQueryDataNullSlotResults() {
        ExecuteOutput selectContentResult = new ExecuteOutput.Builder().setSlotResults(
                null).build();
        byte[] queryData = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                selectContentResult);

        QueryFields queryFields = QueryFields.getRootAsQueryFields(ByteBuffer.wrap(queryData));
        assertEquals(0, queryFields.slotsLength());
    }

    @Test
    public void testCreateQueryData() {
        ExecuteOutput selectContentResult = new ExecuteOutput.Builder()
                .addSlotResults(
                        new SlotResult.Builder()
                                .setSlotId("abc")
                                .addBids(
                                        new android.ondevicepersonalization.Bid.Builder()
                                                .setBidId("bid1")
                                                .setRendered(true)
                                                .setPrice(5.0)
                                                .setScore(1.0)
                                                .setMetrics(new Metrics.Builder()
                                                        .setLongValues(11).build())
                                                .build())
                                .addBids(
                                        new android.ondevicepersonalization.Bid.Builder()
                                                .setBidId("bid2")
                                                .setPrice(1.0)
                                                .setScore(0.1)
                                                .build())
                                .build())
                .build();
        byte[] queryData = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                selectContentResult);

        QueryFields queryFields = QueryFields.getRootAsQueryFields(ByteBuffer.wrap(queryData));
        assertEquals(1, queryFields.slotsLength());
        Slot slot = queryFields.slots(0);
        assertEquals("abc", slot.id());
        assertEquals(2, slot.bidsLength());
        Bid winningBid = slot.bids(0);
        assertEquals("bid1", winningBid.id());
        assertEquals(true, winningBid.rendered());
        assertEquals(5.0, winningBid.price(), DELTA);
        assertEquals(1.0, winningBid.score(), DELTA);
        assertEquals(11, winningBid.metrics().longValues(0));
        assertEquals(0, winningBid.metrics().doubleValuesLength());

        Bid rejectedBid = slot.bids(1);
        assertEquals("bid2", rejectedBid.id());
        assertEquals(false, rejectedBid.rendered());
        assertEquals(1.0, rejectedBid.price(), DELTA);
        assertEquals(0.1, rejectedBid.score(), DELTA);
        assertEquals(null, rejectedBid.metrics());
    }
}
