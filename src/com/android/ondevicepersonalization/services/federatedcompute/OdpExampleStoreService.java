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

import android.federatedcompute.ExampleStoreService;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * Implementation of ExampleStoreService for OnDevicePersonalization
 */
public class OdpExampleStoreService extends ExampleStoreService {
    @Override
    public void startQuery(@NonNull Bundle params, QueryCallback callback) {
        // TODO(278106108): Validate params and pass to iterator
        callback.onStartQuerySuccess(
                OdpExampleStoreIteratorFactory.getInstance().createIterator(new ArrayList<>(),
                        new ArrayList<>()));
    }
}
