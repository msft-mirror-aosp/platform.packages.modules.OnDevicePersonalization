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

package com.android.federatedcompute.services.examplestore;

import android.federatedcompute.aidl.IExampleStoreIterator;

import com.android.federatedcompute.services.common.ErrorStatusException;

import com.google.internal.federated.plan.ExampleSelector;

/** Interface used to provide a reference to the IExampleStoreIterator. */
public interface ExampleStoreIteratorProvider {

    /** Returns the selected ExampleStoreIterator. */
    IExampleStoreIterator getExampleStoreIterator(
            String packageName, ExampleSelector exampleSelector)
            throws InterruptedException, ErrorStatusException;
}
