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

import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import android.federatedcompute.ResultHandlingService;
import android.federatedcompute.common.ExampleConsumption;
import android.federatedcompute.common.TrainingOptions;

import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of ResultHandlingService for OnDevicePersonalization
 */
public class OdpResultHandlingService extends ResultHandlingService {
    @Override
    public void handleResult(TrainingOptions trainingOptions, boolean success,
            List<ExampleConsumption> exampleConsumptionList, Consumer<Integer> callback) {
        // TODO(278106108): Implement this method
        callback.accept(STATUS_SUCCESS);
    }
}
