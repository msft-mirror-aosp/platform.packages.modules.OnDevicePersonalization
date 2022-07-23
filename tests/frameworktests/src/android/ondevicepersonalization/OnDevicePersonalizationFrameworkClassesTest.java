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
     * Tests that the Bid object serializes correctly.
     */
    @Test
    public void testBidSerialization() {
        Bid.Builder builder = new Bid.Builder();
        builder.setKey("key1");
        builder.setPrice(10.0);
        builder.setAdm("[adm]");
        builder.setAdomain("example.com");
        Bid bid = builder.build();

        assertEquals("key1", bid.getKey());
        assertEquals(10.0, bid.getPrice(), 0.01);
        assertEquals("[adm]", bid.getAdm());
        assertEquals("example.com", bid.getAdomain());

        Parcel parcel = Parcel.obtain();
        bid.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Bid bid2 = Bid.CREATOR.createFromParcel(parcel);

        assertEquals("key1", bid2.getKey());
        assertEquals(10.0, bid2.getPrice(), 0.01);
        assertEquals("[adm]", bid2.getAdm());
        assertEquals("example.com", bid2.getAdomain());
    }
}
