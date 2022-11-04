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

package com.android.ondevicepersonalization.services.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.ondevicepersonalization.libraries.plugin.PluginInfo;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PluginUtilsTest {
    @Test
    public void testGetArchiveList_NullApkList() throws Exception {
        assertTrue(PluginUtils.getArchiveList(null).isEmpty());
    }

    @Test
    public void testGetArchiveList() throws Exception {
        String[] apks = {"apk1", "apk2", "", null, "apk3"};
        ImmutableList<PluginInfo.ArchiveInfo> result = PluginUtils.getArchiveList(apks);
        assertEquals(3, result.size());
        assertEquals("apk1", result.get(0).packageName());
        assertEquals("apk2", result.get(1).packageName());
        assertEquals("apk3", result.get(2).packageName());
    }
}
