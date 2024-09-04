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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.DownloadCompletedInput;
import android.adservices.ondevicepersonalization.DownloadCompletedOutput;
import android.adservices.ondevicepersonalization.EventInput;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.EventOutput;
import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.KeyValueStore;
import android.adservices.ondevicepersonalization.RenderInput;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.TrainingExampleRecord;
import android.adservices.ondevicepersonalization.TrainingExamplesInput;
import android.adservices.ondevicepersonalization.TrainingExamplesOutput;
import android.adservices.ondevicepersonalization.WebTriggerInput;
import android.adservices.ondevicepersonalization.WebTriggerOutput;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.adservices.ondevicepersonalization.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Tests of IsolatedWorker methods.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_DATA_CLASS_MISSING_CTORS_AND_GETTERS_ENABLED)
public class IsolatedWorkerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testOnExecute() throws Exception {
        IsolatedWorker worker = new TestWorker();
        WorkerResultReceiver<ExecuteOutput> receiver = new WorkerResultReceiver<>();
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("x", "y");
        worker.onExecute(new ExecuteInput("com.example.app", bundle), receiver);
    }

    @Test
    public void testOnRender() throws Exception {
        IsolatedWorker worker = new TestWorker();
        WorkerResultReceiver<RenderOutput> receiver = new WorkerResultReceiver<>();
        RenderInput input = new RenderInput(
                100, 50, new RenderingConfig.Builder().addKey("a").build());
        worker.onRender(input, receiver);
        assertEquals("abc", receiver.mResult.getContent());
    }

    @Test
    public void testOnDownloadCompleted() throws Exception {
        IsolatedWorker worker = new TestWorker();
        WorkerResultReceiver<DownloadCompletedOutput> receiver = new WorkerResultReceiver<>();
        TestKeyValueStore store = new TestKeyValueStore(
                Map.of("a", new byte[]{'A'}, "b", new byte[]{'B'}));
        worker.onDownloadCompleted(new DownloadCompletedInput(store), receiver);
        assertThat(receiver.mResult.getRetainedKeys(), containsInAnyOrder("a", "b"));
    }

    @Test
    public void testOnEvent() throws Exception {
        IsolatedWorker worker = new TestWorker();
        WorkerResultReceiver<EventOutput> receiver = new WorkerResultReceiver<>();
        EventInput input = new EventInput(
                new RequestLogRecord.Builder().build(), PersistableBundle.EMPTY);
        worker.onEvent(input, receiver);
        assertNotNull(receiver.mResult);
        assertNotNull(receiver.mResult.getEventLogRecord());
        assertEquals(1, receiver.mResult.getEventLogRecord().getType());
    }

    @Test
    public void testOnTrainingExamples() throws Exception {
        IsolatedWorker worker = new TestWorker();
        WorkerResultReceiver<TrainingExamplesOutput> receiver = new WorkerResultReceiver<>();
        TrainingExamplesInput input =
                new TrainingExamplesInput("pop", "task", new byte[] {1}, "collection_uri");
        worker.onTrainingExamples(input, receiver);
        assertNotNull(receiver.mResult);
        assertArrayEquals(new byte[]{'A'},
                receiver.mResult.getTrainingExampleRecords().get(0).getTrainingExample());
        assertArrayEquals(new byte[]{'B'},
                receiver.mResult.getTrainingExampleRecords().get(0).getResumptionToken());
    }

    @Test
    public void testOnWebTrigger() throws Exception {
        IsolatedWorker worker = new TestWorker();
        WorkerResultReceiver<WebTriggerOutput> receiver = new WorkerResultReceiver<>();
        WebTriggerInput input = new WebTriggerInput(
                Uri.parse("http://example.com"), "com.example.app", new byte[]{'A'});
        worker.onWebTrigger(input, receiver);
        assertNotNull(receiver.mResult);
        assertNotNull(receiver.mResult.getRequestLogRecord());
        assertEquals(1, receiver.mResult.getEventLogRecords().size());
    }

    class TestWorker implements IsolatedWorker {
        @Override public void onExecute(
                ExecuteInput input,
                OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
            assertEquals("com.example.app", input.getAppPackageName());
            assertEquals("y", input.getAppParams().getString("x"));
            receiver.onResult(new ExecuteOutput.Builder().build());
        }

        @Override public void onRender(
                RenderInput input,
                OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
            assertEquals(100, input.getWidth());
            assertEquals(50, input.getHeight());
            assertEquals("a", input.getRenderingConfig().getKeys().get(0));
            receiver.onResult(new RenderOutput.Builder().setContent("abc").build());
        }

        @Override public void onDownloadCompleted(
                DownloadCompletedInput input,
                OutcomeReceiver<DownloadCompletedOutput, IsolatedServiceException> receiver) {
            assertThat(input.getDownloadedContents().keySet(), containsInAnyOrder("a", "b"));
            receiver.onResult(new DownloadCompletedOutput.Builder()
                    .setRetainedKeys(new ArrayList<>(input.getDownloadedContents().keySet()))
                    .build());
        }

        @Override public void onEvent(
                EventInput input,
                OutcomeReceiver<EventOutput, IsolatedServiceException> receiver) {
            assertNotNull(input.getRequestLogRecord());
            assertTrue(input.getParameters().isEmpty());
            receiver.onResult(new EventOutput.Builder()
                    .setEventLogRecord(new EventLogRecord.Builder().setType(1).build())
                    .build());
        }

        @Override public void onTrainingExamples(
                TrainingExamplesInput input,
                OutcomeReceiver<TrainingExamplesOutput, IsolatedServiceException> receiver) {
            assertEquals("pop", input.getPopulationName());
            assertEquals("task", input.getTaskName());
            assertEquals("collection_uri", input.getCollectionName());
            assertArrayEquals(new byte[]{1}, input.getResumptionToken());
            TrainingExampleRecord data = new TrainingExampleRecord.Builder()
                    .setTrainingExample(new byte[]{'A'})
                    .setResumptionToken(new byte[]{'B'})
                    .build();
            receiver.onResult(
                    new TrainingExamplesOutput.Builder().addTrainingExampleRecord(data).build());
        }

        @Override public void onWebTrigger(
                WebTriggerInput input,
                OutcomeReceiver<WebTriggerOutput, IsolatedServiceException> receiver) {
            assertEquals("http://example.com", input.getDestinationUrl().toString());
            assertEquals("com.example.app", input.getAppPackageName());
            assertArrayEquals(new byte[]{'A'}, input.getData());
            receiver.onResult(new WebTriggerOutput.Builder()
                    .setRequestLogRecord(new RequestLogRecord.Builder().build())
                    .addEventLogRecord(new EventLogRecord.Builder().build())
                    .build());
        }
    }

    static class TestKeyValueStore implements KeyValueStore {
        private final Map<String, byte[]> mContents;
        TestKeyValueStore(Map<String, byte[]> contents) {
            mContents = contents;
        }
        @Override public Set<String> keySet() {
            return mContents.keySet();
        }
        @Override public byte[] get(String key) {
            return mContents.getOrDefault(key, null);
        }
    }

    static class WorkerResultReceiver<T> implements OutcomeReceiver<T, IsolatedServiceException> {
        T mResult = null;
        IsolatedServiceException mException = null;

        @Override public void onResult(T result) {
            mResult = result;
        }

        @Override public void onError(IsolatedServiceException e) {
            mException = e;
        }
    }
}
