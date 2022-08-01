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

package com.android.libraries.pcc.chronicle.test

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.libraries.pcc.chronicle.api.storage.EntityMetadata
import com.android.libraries.pcc.chronicle.api.storage.Timestamp
import com.android.libraries.pcc.chronicle.api.storage.toInstant
import com.android.libraries.pcc.chronicle.api.storage.toProtoTimestamp
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for Chronicle Api storage classes. */
@RunWith(AndroidJUnit4::class)
class ChronicleApiStorageTest {
    @Test
    fun testInstantToTimestamp() {
        val instant1 = Instant.ofEpochMilli(999000)
        val timestamp1 = instant1.toProtoTimestamp()
        assertThat(timestamp1.getSeconds()).isEqualTo(999)
        assertThat(timestamp1.getNanos()).isEqualTo(0)
    }

    @Test
    fun testTimestampToInstant() {
        val timestamp1 = Timestamp.newBuilder().setSeconds(901).setNanos(101).build()
        val instant1 = timestamp1.toInstant()
        assertThat(instant1.getEpochSecond()).isEqualTo(901)
        assertThat(instant1.getNano()).isEqualTo(101)
    }
}