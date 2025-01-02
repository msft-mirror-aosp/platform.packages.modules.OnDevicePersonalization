/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.download;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public final class DownloadedFileParserTest {
    private String mTestInput = "{"
            + "\"unknownString\": \"ignored\", "
            + "\"unknownArray\": [\"ignored1\", \"ignored2\"], "
            + "\"unknownObject\": {\"key\": \"val\"}, "
            + "\"syncToken\": 1010, "
            + "\"contents\": ["
            + "{\"key\": \"key1\", \"data\": \"val1\"}, "
            + "{\"key\": \"key2\", \"data\": \"val2\", \"encoding\": \"utf8\"}, "
            + "{\"key\": \"key3\", \"data\": \"dmFsMw==\", \"encoding\": \"base64\"} "
            + "]}";

    @Test
    public void testParseJson() throws Exception {
        ParsedFileContents result = DownloadedFileParser.parseJson(
                new ByteArrayInputStream(mTestInput.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1010, result.getSyncToken());
        var vendorDataMap = result.getVendorDataMap();
        assertNotNull(vendorDataMap);
        assertEquals("key1", vendorDataMap.get("key1").getKey());
        assertArrayEquals("val1".getBytes(), vendorDataMap.get("key1").getData());
        assertEquals("key2", vendorDataMap.get("key2").getKey());
        assertArrayEquals("val2".getBytes(), vendorDataMap.get("key2").getData());
        assertEquals("key3", vendorDataMap.get("key3").getKey());
        assertArrayEquals("val3".getBytes(), vendorDataMap.get("key3").getData());
    }
}
