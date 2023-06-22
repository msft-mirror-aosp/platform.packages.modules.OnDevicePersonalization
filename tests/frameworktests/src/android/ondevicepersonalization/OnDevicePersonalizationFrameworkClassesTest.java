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

import android.content.ContentValues;
import android.os.Parcel;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Unit Tests of Framework API Classes.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class OnDevicePersonalizationFrameworkClassesTest {
    /**
     * Tests that the ExecuteOutput object serializes correctly.
     */
    @Test
    public void testExecuteOutput() {
        ContentValues row = new ContentValues();
        row.put("a", 5);
        ExecuteOutput result =
                new ExecuteOutput.Builder()
                    .setRequestLogRecord(new RequestLogRecord.Builder().addRow(row).build())
                    .addRenderingConfig(new RenderingConfig.Builder().addKey("abc").build())
                    .build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ExecuteOutput result2 = ExecuteOutput.CREATOR.createFromParcel(parcel);

        assertEquals(
                5, result2.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
        assertEquals("abc", result2.getRenderingConfigs().get(0).getKeys().get(0));
    }

    /**
     * Tests that the RenderOutput object serializes correctly.
     */
    @Test
    public void testRenderOutput() {
        RenderOutput result = new RenderOutput.Builder().setContent("abc").build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RenderOutput result2 = RenderOutput.CREATOR.createFromParcel(parcel);

        assertEquals("abc", result2.getContent());
    }

    /**
     * Tests that the DownloadOutput object serializes correctly.
     */
    @Test
    public void teetDownloadOutput() {
        DownloadOutput result = new DownloadOutput.Builder()
                .addRetainedKey("abc").addRetainedKey("def").build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DownloadOutput result2 = DownloadOutput.CREATOR.createFromParcel(parcel);

        assertEquals(result, result2);
        assertEquals("abc", result2.getRetainedKeys().get(0));
        assertEquals("def", result2.getRetainedKeys().get(1));
    }

    /**
     * Tests that the WebViewEventInput object serializes correctly.
     */
    @Test
    public void testWebViewEventInput() {
        PersistableBundle params = new PersistableBundle();
        params.putInt("x", 3);
        ArrayList<ContentValues> rows = new ArrayList<>();
        rows.add(new ContentValues());
        rows.get(0).put("a", 5);
        WebViewEventInput result = new WebViewEventInput.Builder()
                .setParameters(params)
                .setRequestLogRecord(
                    new RequestLogRecord.Builder()
                        .setRows(rows)
                        .build())
                .build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        WebViewEventInput result2 = WebViewEventInput.CREATOR.createFromParcel(parcel);

        assertEquals(3, result2.getParameters().getInt("x"));
        assertEquals(
                5, result2.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
    }

    /**
     * Tests that the WebViewEventOutput object serializes correctly.
     */
    @Test
    public void testWebViewEventOutput() {
        ContentValues data = new ContentValues();
        data.put("a", 3);
        WebViewEventOutput result = new WebViewEventOutput.Builder()
                .setEventLogRecord(
                    new EventLogRecord.Builder()
                        .setType(5)
                        .setRowIndex(6)
                        .setData(data)
                        .build())
                .build();

        Parcel parcel = Parcel.obtain();
        result.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        WebViewEventOutput result2 = WebViewEventOutput.CREATOR.createFromParcel(parcel);

        assertEquals(result, result2);
        assertEquals(5, result2.getEventLogRecord().getType());
        assertEquals(6, result2.getEventLogRecord().getRowIndex());
        assertEquals(3, result2.getEventLogRecord().getData().getAsInteger("a").intValue());
    }

    /** Test for RenderingConfig class. */
    @Test
    public void testRenderingConfig() {
        RenderingConfig data = new RenderingConfig.Builder().addKey("a").addKey("b").build();
        assertEquals(2, data.getKeys().size());
        assertEquals("a", data.getKeys().get(0));
        assertEquals("b", data.getKeys().get(1));
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
        RequestLogRecord logRecord = new RequestLogRecord.Builder().setRows(rows).build();
        assertEquals(2, logRecord.getRows().size());
        assertEquals(5, logRecord.getRows().get(0).getAsInteger("a").intValue());
        assertEquals(6, logRecord.getRows().get(1).getAsInteger("b").intValue());
    }

    /** Test for RequestLogRecord class. */
    @Test
    public void testEventLogRecord() {
        ContentValues row = new ContentValues();
        row.put("a", 5);
        EventLogRecord logRecord = new EventLogRecord.Builder()
                .setType(1)
                .setRowIndex(2)
                .setData(row)
                .build();
        assertEquals(1, logRecord.getType());
        assertEquals(2, logRecord.getRowIndex());
        assertEquals(5, logRecord.getData().getAsInteger("a").intValue());
    }
}
