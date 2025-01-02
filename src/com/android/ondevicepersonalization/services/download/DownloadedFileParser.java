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

import android.util.JsonReader;

import com.android.ondevicepersonalization.services.data.vendor.VendorData;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses the downloaded file.
 */
class DownloadedFileParser {
    public static ParsedFileContents parseJson(InputStream in) throws IOException {
        long syncToken = -1;
        Map<String, VendorData> vendorDataMap = null;

        try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("syncToken")) {
                    syncToken = reader.nextLong();
                } else if (name.equals("contents")) {
                    vendorDataMap = readContentsArray(reader);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        return new ParsedFileContents(syncToken, vendorDataMap);
    }

    private static Map<String, VendorData> readContentsArray(JsonReader reader)
            throws IOException {
        Map<String, VendorData> vendorDataMap = new HashMap<>();
        reader.beginArray();
        while (reader.hasNext()) {
            VendorData data = readContent(reader);
            if (data != null) {
                vendorDataMap.put(data.getKey(), data);
            }
        }
        reader.endArray();

        return vendorDataMap;
    }

    private static VendorData readContent(JsonReader reader) throws IOException {
        String key = null;
        byte[] data = null;
        String encoding = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("key")) {
                key = reader.nextString();
            } else if (name.equals("data")) {
                data = reader.nextString().getBytes(StandardCharsets.UTF_8);
            } else if (name.equals("encoding")) {
                encoding = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (key == null || data == null) {
            return null;
        }
        if (encoding != null && !encoding.isBlank()) {
            if (encoding.strip().equalsIgnoreCase("base64")) {
                data = Base64.getDecoder().decode(data);
            } else if (!encoding.strip().equalsIgnoreCase("utf8")) {
                return null;
            }
        }
        return new VendorData.Builder().setKey(key).setData(data).build();
    }

    private DownloadedFileParser() {}
}
