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

package com.android.ondevicepersonalization.services.display.velocity;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

@RunWith(JUnit4.class)
public class VelocityEngineFactoryTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    // TODO(b/263180569): Add more tests to cover the different configuration options set.
    @Test
    public void renderBasicTemplate() throws Exception {
        VelocityEngine ve = VelocityEngineFactory.getVelocityEngine(mContext);
        String inputTemplate = "Hello $tool.encodeHtml($name)! I am $age.";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("name", "odp");
        bundle.putInt("age", 100);

        Template template = ve.getTemplate(createTempTemplate(inputTemplate));
        org.apache.velocity.context.Context ctx =
                VelocityEngineFactory.createVelocityContext(bundle);

        StringWriter writer = new StringWriter();
        template.merge(ctx, writer);
        String expected = "Hello odp! I am 100.";
        assertEquals(expected, writer.toString());
    }

    private String createTempTemplate(String s) throws Exception {
        File temp = File.createTempFile("VelocityEngineFactoryTests", "vm", mContext.getCacheDir());
        try (PrintWriter out = new PrintWriter(temp)) {
            out.print(s);
        }
        temp.deleteOnExit();
        return temp.getName();
    }
}
