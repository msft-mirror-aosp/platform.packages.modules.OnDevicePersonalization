/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.display;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.ondevicepersonalization.RenderContentResult;
import android.ondevicepersonalization.SlotResult;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class DisplayHelperTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testGenerateHtml() {
        DisplayHelper displayHelper = new DisplayHelper(mContext);
        RenderContentResult renderContentResult = new RenderContentResult.Builder()
                .setContent("html").build();
        assertEquals("html", displayHelper.generateHtml(renderContentResult));
    }

    @Test
    @LargeTest
    public void testDisplayHtml() throws Exception {
        // Permission needed to setView in the display.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERNAL_SYSTEM_WINDOW);

        DisplayHelper displayHelper = new DisplayHelper(mContext);
        SurfaceView surfaceView = new SurfaceView(mContext);
        SlotResult slotResult = new SlotResult.Builder()
                .setSlotId("slotId").setWinningBids(new ArrayList<>()).build();
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display primaryDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        final Context windowContext = mContext.createDisplayContext(primaryDisplay);
        ListenableFuture<SurfaceControlViewHost.SurfacePackage> result =
                displayHelper.displayHtml("html", slotResult, mContext.getPackageName(),
                        surfaceView.getHostToken(), windowContext.getDisplay().getDisplayId(),
                        surfaceView.getWidth(), surfaceView.getHeight());
        // Give 2 minutes to create the webview. Should normally be ~25s.
        SurfaceControlViewHost.SurfacePackage surfacePackage =
                result.get(120, TimeUnit.SECONDS);
        assertNotNull(surfacePackage);
        surfacePackage.release();
    }
}
