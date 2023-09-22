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

package com.android.ondevicepersonalization.services.federatedcompute;

import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESULT;

import android.federatedcompute.ExampleStoreIterator;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.ListIterator;

/**
 * Implementation of ExampleStoreIterator for OnDevicePersonalization
 */
public class OdpExampleStoreIterator implements ExampleStoreIterator {

    ListIterator<byte[]> mExampleIterator;

    OdpExampleStoreIterator(List<byte[]> exampleList) {
        mExampleIterator = exampleList.listIterator();
    }

    @Override
    public void next(@NonNull IteratorCallback callback) {
        if (mExampleIterator.hasNext()) {
            Bundle result = new Bundle();
            result.putByteArray(EXTRA_EXAMPLE_ITERATOR_RESULT, mExampleIterator.next());
            callback.onIteratorNextSuccess(result);
            return;
        }
        callback.onIteratorNextSuccess(null);
    }

    @Override
    public void close() {
        // No resources to close.
    }
}
