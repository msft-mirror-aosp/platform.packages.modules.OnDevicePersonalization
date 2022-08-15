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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import com.android.libraries.pcc.chronicle.api.policy.annotation.annotation
import com.android.libraries.pcc.chronicle.api.policy.annotation.AnnotationParam
import com.android.libraries.pcc.chronicle.api.policy.annotation.AnnotationBuilder


@RunWith(AndroidJUnit4::class)
class ChronicleApiPolicyAnnotationTest {
    @Test
    fun testAnnotationEmptyBlock() {
        val actual = annotation("ttl")

        assertThat(actual.name).isEqualTo("ttl")
        assertThat(actual.params).isEmpty()
    }

    @Test
    fun testAnnotationNonEmptyBlock() {
        val actual = annotation("ttl") {
            param("stringParam", "My String Value")
            param("intParam", 42)
            param("boolParam", true)
        }

        assertThat(actual.name).isEqualTo("ttl")
        assertThat(actual.params).hasSize(3)
        assertThat(actual.getParam("intParam")).isEqualTo(AnnotationParam.Num(42))
        assertThat(actual.getStringParam("stringParam")).isEqualTo("My String Value")
        assertThat(actual.getOptionalStringParam("Param not Found!")).isEqualTo(null)
    }
}