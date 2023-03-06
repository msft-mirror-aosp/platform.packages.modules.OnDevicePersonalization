/*
 * Copyright 2022 The Android Open Source Project
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

package android.ondevicepersonalization;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit Tests of Framework API Classes.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class OnDevicePersonalizationFrameworkClassesTest {
    /**
     * Tests that the SelectContentResult object serializes correctly.
     */
    @Test
    public void testSelectContentResult() {
        PersistableBundle eventParams = new PersistableBundle();
        eventParams.putInt("x", 6);
        SelectContentResult result =
                new SelectContentResult.Builder()
                    .addSlotResults(
                        new SlotResult.Builder().setSlotId("abc")
                            .addWinningBids(
                                new ScoredBid.Builder()
                                    .setBidId("bid1")
                                    .setPrice(5.0)
                                    .setScore(1.0)
                                    .setMetrics(new Metrics.Builder()
                                        .setIntValues(11).build())
                                    .setEventsWithMetrics(8)
                                    .setEventMetricsParameters(eventParams)
                                    .build())
                            .addRejectedBids(
                                new ScoredBid.Builder()
                                    .setBidId("bid2")
                                    .setPrice(1.0)
                                    .setScore(0.1)
                                    .build())
                            .build())
                    .build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SelectContentResult result2 = SelectContentResult.CREATOR.createFromParcel(parcel);

        SlotResult slotResult = result2.getSlotResults().get(0);
        assertEquals("abc", slotResult.getSlotId());
        assertEquals("bid1", slotResult.getWinningBids().get(0).getBidId());
        assertEquals(5.0, slotResult.getWinningBids().get(0).getPrice(), 0.0);
        assertEquals(1.0, slotResult.getWinningBids().get(0).getScore(), 0.0);
        assertEquals(11, slotResult.getWinningBids().get(0).getMetrics().getIntValues()[0]);
        assertEquals(8, slotResult.getWinningBids().get(0).getEventsWithMetrics()[0]);
        assertEquals(
                6,
                slotResult.getWinningBids().get(0).getEventMetricsParameters().getInt("x"));
        assertEquals("bid2", slotResult.getRejectedBids().get(0).getBidId());
        assertEquals(1.0, slotResult.getRejectedBids().get(0).getPrice(), 0.0);
        assertEquals(0.1, slotResult.getRejectedBids().get(0).getScore(), 0.0);
    }

    /**
     * Tests that the SlotInfo object serializes correctly.
     */
    @Test
    public void testSlotInfo() {
        SlotInfo slotInfo = new SlotInfo.Builder().setWidth(100).setHeight(50).build();

        Parcel parcel = Parcel.obtain();
        slotInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SlotInfo slotInfo2 = SlotInfo.CREATOR.createFromParcel(parcel);

        assertEquals(slotInfo, slotInfo2);
        assertEquals(100, slotInfo2.getWidth());
        assertEquals(50, slotInfo2.getHeight());
    }

    /**
     * Tests that the RenderContentResult object serializes correctly.
     */
    @Test
    public void testRenderContentResult() {
        RenderContentResult result = new RenderContentResult.Builder().setContent("abc").build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RenderContentResult result2 = RenderContentResult.CREATOR.createFromParcel(parcel);

        assertEquals(result, result2);
        assertEquals("abc", result2.getContent());
    }

    /**
     * Tests that the DownloadResult object serializes correctly.
     */
    @Test
    public void teetDownloadResult() {
        DownloadResult result = new DownloadResult.Builder()
                .addKeysToRetain("abc").addKeysToRetain("def").build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DownloadResult result2 = DownloadResult.CREATOR.createFromParcel(parcel);

        assertEquals(result, result2);
        assertEquals("abc", result2.getKeysToRetain().get(0));
        assertEquals("def", result2.getKeysToRetain().get(1));
    }

    /**
     * Tests that the Metrics object serializes correctly.
     */
    @Test
    public void testMetrics() {
        long[] intMetrics = {10, 20};
        double[] floatMetrics = {5.0};
        Metrics result = new Metrics.Builder()
                .setIntValues(intMetrics).setFloatValues(floatMetrics).build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Metrics result2 = Metrics.CREATOR.createFromParcel(parcel);

        assertEquals(result, result2);
        assertEquals(10, result2.getIntValues()[0]);
        assertEquals(20, result2.getIntValues()[1]);
        assertEquals(5.0, result2.getFloatValues()[0], 0.0);
    }

    /**
     * Tests that the EventMetricsInput object serializes correctly.
     */
    @Test
    public void testEventMetricsInput() {
        PersistableBundle params = new PersistableBundle();
        params.putInt("x", 3);
        EventMetricsInput result = new EventMetricsInput.Builder()
                .setEventType(6)
                .setEventParams(params)
                .build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        EventMetricsInput result2 = EventMetricsInput.CREATOR.createFromParcel(parcel);

        assertEquals(6, result2.getEventType());
        assertEquals(3, result2.getEventParams().getInt("x"));
    }

    /**
     * Tests that the Metrics object serializes correctly.
     */
    @Test
    public void testEventMetricsResult() {
        long[] intMetrics = {10, 20};
        double[] floatMetrics = {5.0};
        EventMetricsResult result = new EventMetricsResult.Builder()
                .setMetrics(new Metrics.Builder().setIntValues(11).build()).build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        EventMetricsResult result2 = EventMetricsResult.CREATOR.createFromParcel(parcel);

        assertEquals(result, result2);
        assertEquals(11, result2.getMetrics().getIntValues()[0]);
    }
}
