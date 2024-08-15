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

package android.adservices.ondevicepersonalization;

import android.annotation.FlaggedApi;

import com.android.adservices.ondevicepersonalization.flags.Flags;

/**
 * The result returned by {@link FederatedComputeScheduler#schedule(FederatedComputeScheduleRequest,
 * android.os.OutcomeReceiver)} when successful. Currently empty will be extended in the future.
 */
@FlaggedApi(Flags.FLAG_FCP_SCHEDULE_WITH_OUTCOME_RECEIVER_ENABLED)
public final class FederatedComputeScheduleResponse {

    /** Constructor used by platform code within {@link FederatedComputeScheduler}. */
    FederatedComputeScheduleResponse() {
        // Currently class is empty, will be extended to include metadata about scheduled jobs.
    }
}
