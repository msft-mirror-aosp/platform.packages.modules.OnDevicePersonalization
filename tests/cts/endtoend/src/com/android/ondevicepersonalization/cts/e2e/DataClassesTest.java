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

package com.android.ondevicepersonalization.cts.e2e;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.DownloadCompletedOutput;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.EventOutput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.FederatedComputeInput;
import android.adservices.ondevicepersonalization.FederatedComputeScheduler;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParams;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.TrainingExampleRecord;
import android.adservices.ondevicepersonalization.TrainingExamplesOutput;
import android.adservices.ondevicepersonalization.TrainingInterval;
import android.adservices.ondevicepersonalization.WebTriggerOutput;
import android.content.ComponentName;
import android.content.ContentValues;
import android.net.Uri;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Tests of Framework API Data Classes.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DataClassesTest {
    /**
     * Test builder and getters for ExecuteOutput.
     */
    @Test
    public void testExecuteOutput() {
        ContentValues row = new ContentValues();
        row.put("a", 5);
        ExecuteOutput data =
                new ExecuteOutput.Builder()
                    .setRequestLogRecord(new RequestLogRecord.Builder().addRow(row).build())
                    .setRenderingConfig(new RenderingConfig.Builder().addKey("abc").build())
                    .addEventLogRecord(new EventLogRecord.Builder().setType(1).build())
                    .setOutputData(new byte[]{1})
                    .build();

        assertEquals(
                5, data.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
        assertEquals("abc", data.getRenderingConfig().getKeys().get(0));
        assertEquals(1, data.getEventLogRecords().get(0).getType());
        assertArrayEquals(new byte[]{1}, data.getOutputData());
    }

    /**
     * Test builder and getters for RenderOutput.
     */
    @Test
    public void testRenderOutput() {
        RenderOutput data = new RenderOutput.Builder()
                .setContent("abc")
                .setTemplateId("tmpl")
                .setTemplateParams(PersistableBundle.EMPTY)
                .build();

        assertEquals("abc", data.getContent());
        assertEquals("tmpl", data.getTemplateId());
        assertTrue(data.getTemplateParams().isEmpty());
    }

    /**
     * Test builder and getters for DownloadCompletedOutput.
     */
    @Test
    public void testDownloadCompletedOutput() {
        DownloadCompletedOutput data = new DownloadCompletedOutput.Builder()
                .addRetainedKey("abc").addRetainedKey("def").build();

        assertEquals("abc", data.getRetainedKeys().get(0));
        assertEquals("def", data.getRetainedKeys().get(1));
    }

    /**
     * Test builder and getters for EventOutput.
     */
    @Test
    public void testEventOutput() {
        ContentValues data = new ContentValues();
        data.put("a", 3);
        EventOutput output = new EventOutput.Builder()
                .setEventLogRecord(
                    new EventLogRecord.Builder()
                        .setType(5)
                        .setRowIndex(6)
                        .setData(data)
                        .build())
                .build();

        assertEquals(5, output.getEventLogRecord().getType());
        assertEquals(6, output.getEventLogRecord().getRowIndex());
        assertEquals(3, output.getEventLogRecord().getData().getAsInteger("a").intValue());
    }

    /**
     * Test builder and getters for TrainingExamplesOutput.
     */
    @Test
    public void testTrainingExamplesOutput() {
        TrainingExamplesOutput data = new TrainingExamplesOutput.Builder()
                .addTrainingExampleRecord(
                        new TrainingExampleRecord.Builder()
                        .setTrainingExample(new byte[]{1})
                        .setResumptionToken(new byte[]{2})
                        .build())
                .build();

        assertArrayEquals(new byte[]{1},
                data.getTrainingExampleRecords().get(0).getTrainingExample());
        assertArrayEquals(new byte[]{2},
                data.getTrainingExampleRecords().get(0).getResumptionToken());
    }

    /** Test for RenderingConfig class. */
    @Test
    public void testRenderingConfig() {
        RenderingConfig data = new RenderingConfig.Builder().addKey("a").addKey("b").build();
        assertEquals(2, data.getKeys().size());
        assertEquals("a", data.getKeys().get(0));
        assertEquals("b", data.getKeys().get(1));
    }

    @Test
    public void testTrainingInterval() {
        TrainingInterval data = new TrainingInterval.Builder()
                .setSchedulingMode(TrainingInterval.SCHEDULING_MODE_RECURRENT)
                .setMinimumInterval(Duration.ofSeconds((5)))
                .build();

        assertEquals(5, data.getMinimumInterval().toSeconds());
        assertEquals(TrainingInterval.SCHEDULING_MODE_RECURRENT, data.getSchedulingMode());
    }

    @Test
    public void testFederatedComputeSchedulerParams() {
        TrainingInterval data = new TrainingInterval.Builder()
                .setSchedulingMode(TrainingInterval.SCHEDULING_MODE_RECURRENT)
                .setMinimumInterval(Duration.ofSeconds((5)))
                .build();
        FederatedComputeScheduler.Params params = new FederatedComputeScheduler.Params(data);

        assertEquals(5, params.getTrainingInterval().getMinimumInterval().toSeconds());
        assertEquals(TrainingInterval.SCHEDULING_MODE_RECURRENT,
                params.getTrainingInterval().getSchedulingMode());
    }

    /** Test for RequestLogRecord class. */
    @Test
    public void testRequestLogRecord() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row = new ContentValues();
        row.put("a", 5);
        rows.add(row);
        row = new ContentValues();
        row.put("b", 6);
        rows.add(row);
        RequestLogRecord logRecord = new RequestLogRecord.Builder().setRows(rows)
                .setRequestId(1).build();
        assertEquals(2, logRecord.getRows().size());
        assertEquals(5, logRecord.getRows().get(0).getAsInteger("a").intValue());
        assertEquals(6, logRecord.getRows().get(1).getAsInteger("b").intValue());
        assertEquals(1, logRecord.getRequestId());
        assertEquals(Instant.ofEpochMilli(0), logRecord.getTime());
    }

    /** Test for EventLogRecord class. */
    @Test
    public void testEventLogRecord() {
        ContentValues row = new ContentValues();
        row.put("a", 5);
        EventLogRecord logRecord = new EventLogRecord.Builder()
                .setType(1)
                .setRowIndex(2)
                .setData(row)
                .setRequestLogRecord(new RequestLogRecord.Builder().addRow(row).build())
                .build();
        assertEquals(1, logRecord.getType());
        assertEquals(2, logRecord.getRowIndex());
        assertEquals(5, logRecord.getData().getAsInteger("a").intValue());
        assertEquals(5, logRecord.getRequestLogRecord().getRows()
                .get(0).getAsInteger("a").intValue());
        assertEquals(Instant.ofEpochMilli(0), logRecord.getTime());
    }

    @Test
    public void testFederatedComputeInput() {
        FederatedComputeInput data = new FederatedComputeInput.Builder()
                .setPopulationName("abc")
                .build();
        assertEquals("abc", data.getPopulationName());
    }

    /** Test for TrainingExampleRecord class */
    @Test
    public void testTrainingExampleRecord() {
        TrainingExampleRecord data = new TrainingExampleRecord.Builder()
                .setTrainingExample(new byte[]{1, 2})
                .setResumptionToken(new byte[]{3, 4})
                .build();
        assertArrayEquals(new byte[]{1, 2}, data.getTrainingExample());
        assertArrayEquals(new byte[]{3, 4}, data.getResumptionToken());
    }

    /** Test for WebTriggerOutput class */
    @Test
    public void testWebTriggerOutput() {
        ContentValues row = new ContentValues();
        row.put("a", 5);
        WebTriggerOutput data =
                new WebTriggerOutput.Builder()
                    .setRequestLogRecord(new RequestLogRecord.Builder().addRow(row).build())
                    .setEventLogRecords(new ArrayList<>())
                    .addEventLogRecord(new EventLogRecord.Builder().setType(1).build())
                    .build();

        assertEquals(
                5, data.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
        assertEquals(1, data.getEventLogRecords().get(0).getType());
    }

    /** Test for MeasurementWebTriggerParams class */
    @Test
    public void testMeasurementWebTriggerEventParams() {
        MeasurementWebTriggerEventParams data =
                new MeasurementWebTriggerEventParams.Builder(
                        Uri.parse("http://example.com"),
                        "com.example.testapp",
                        ComponentName.createRelative("com.example.service", ".ServiceClass"))
                    .setCertDigest("ABCD")
                    .setEventData(new byte[] {1, 2, 3})
                    .build();

        assertEquals("http://example.com", data.getDestinationUrl().toString());
        assertEquals("com.example.testapp", data.getAppPackageName());
        assertEquals("com.example.service", data.getIsolatedService().getPackageName());
        assertEquals(
                "com.example.service.ServiceClass",
                data.getIsolatedService().getClassName());
        assertEquals("ABCD", data.getCertDigest());
        assertArrayEquals(new byte[]{1, 2, 3}, data.getEventData());

        data = new MeasurementWebTriggerEventParams.Builder(
                Uri.parse("http://x"),
                "x",
                ComponentName.createRelative("a", "b"))
                .setCertDigest("ABCD")
                .setEventData(new byte[] {1, 2, 3})
                .setDestinationUrl(Uri.parse("http://example.com"))
                .setAppPackageName("com.example.testapp")
                .setIsolatedService(ComponentName.createRelative(
                        "com.example.service", ".ServiceClass"))
                .build();

        assertEquals("http://example.com", data.getDestinationUrl().toString());
        assertEquals("com.example.testapp", data.getAppPackageName());
        assertEquals("com.example.service", data.getIsolatedService().getPackageName());
        assertEquals(
                "com.example.service.ServiceClass",
                data.getIsolatedService().getClassName());
        assertEquals("ABCD", data.getCertDigest());
        assertArrayEquals(new byte[]{1, 2, 3}, data.getEventData());
    }

    @Test
    public void testIsolatedServiceException() {
        assertEquals(42, new IsolatedServiceException(42).getErrorCode());
    }
}
