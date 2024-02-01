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

package com.android.ondevicepersonalization.services;

/**
 * OnDevicePersonalization Feature Flags interface. This Flags interface hold the default values
 * of flags. The default values in this class must match with the default values in PH since we
 * will migrate to Flag Codegen in the future. With that migration, the Flags.java file will be
 * generated from the GCL.
 */
public interface Flags {
    /**
     * Global OnDevicePersonalization Kill Switch. This overrides all other killswitches.
     * The default value is true which means OnDevicePersonalization is disabled.
     * This flag is used for ramp-up and emergency turning off the whole module.
     */
    boolean GLOBAL_KILL_SWITCH = true;

    /**
     * P/H flag to override the personalization status for end-to-end tests.
     * The default value is false, which means UserPrivacyStatus#personalizationStatus is not
     * override by PERSONALIZATION_STATUS_OVERRIDE_VALUE. If true, returns the personalization
     * status in PERSONALIZATION_STATUS_OVERRIDE_VALUE.
     */
    boolean ENABLE_PERSONALIZATION_STATUS_OVERRIDE = false;

    /**
     * Value of the personalization status, if ENABLE_PERSONALIZATION_STATUS_OVERRIDE is true.
     */
    boolean PERSONALIZATION_STATUS_OVERRIDE_VALUE = false;

    /**
     * Deadline for calls from ODP to isolated services.
     */
    int ISOLATED_SERVICE_DEADLINE_SECONDS = 30;

    /**
     * Default value for the list of trusted partner app names.
     */
    String DEFAULT_TRUSTED_PARTNER_APPS_LIST = "";

    /**
     * Default value for the shared isolated process feature.
     */
    boolean DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED = false;

    String DEFAULT_CALLER_APP_ALLOW_LIST =
            "android.ondevicepersonalization,"
                    + "android.ondevicepersonalization.test.scenario,"
                    + "com.android.federatedcompute.services,"
                    + "com.android.libraries.pcc.chronicle.test,"
                    + "com.android.ondevicepersonalization,"
                    + "com.android.ondevicepersonalization.cts.e2e,"
                    + "com.android.ondevicepersonalization.federatedcomputetests,"
                    + "com.android.ondevicepersonalization.libraries.plugin,"
                    + "com.android.ondevicepersonalization.manualtests,"
                    + "com.android.ondevicepersonalization.plugintests,"
                    + "com.android.ondevicepersonalization.services,"
                    + "com.android.ondevicepersonalization.servicetests,"
                    + "com.android.ondevicepersonalization.systemserviceapitests,"
                    + "com.android.ondevicepersonalization.systemserviceimpltests,"
                    + "com.android.ondevicepersonalization.testing.sampleservice,"
                    + "com.example.odpclient,"
                    + "com.example.odpsamplenetwork,"
                    + "com.example.odptargetingapp1,"
                    + "com.example.odptargetingapp2";

    String DEFAULT_ISOLATED_SERVICE_ALLOW_LIST =
            "android.ondevicepersonalization,"
                    + "android.ondevicepersonalization.test.scenario,"
                    + "com.android.federatedcompute.services,"
                    + "com.android.libraries.pcc.chronicle.test,"
                    + "com.android.ondevicepersonalization,"
                    + "com.android.ondevicepersonalization.cts.e2e,"
                    + "com.android.ondevicepersonalization.federatedcomputetests,"
                    + "com.android.ondevicepersonalization.libraries.plugin,"
                    + "com.android.ondevicepersonalization.manualtests,"
                    + "com.android.ondevicepersonalization.plugintests,"
                    + "com.android.ondevicepersonalization.services,"
                    + "com.android.ondevicepersonalization.servicetests,"
                    + "com.android.ondevicepersonalization.systemserviceapitests,"
                    + "com.android.ondevicepersonalization.systemserviceimpltests,"
                    + "com.android.ondevicepersonalization.testing.sampleservice,"
                    + "com.example.odpclient,"
                    + "com.example.odpsamplenetwork,"
                    + "com.example.odptargetingapp1,"
                    + "com.example.odptargetingapp2";

    default boolean getGlobalKillSwitch() {
        return GLOBAL_KILL_SWITCH;
    }

    default boolean isPersonalizationStatusOverrideEnabled() {
        return ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
    }

    default boolean getPersonalizationStatusOverrideValue() {
        return PERSONALIZATION_STATUS_OVERRIDE_VALUE;
    }

    default int getIsolatedServiceDeadlineSeconds() {
        return ISOLATED_SERVICE_DEADLINE_SECONDS;
    }

    default String getTrustedPartnerAppsList() {
        return DEFAULT_TRUSTED_PARTNER_APPS_LIST;
    }

    default boolean isSharedIsolatedProcessFeatureEnabled() {
        return DEFAULT_SHARED_ISOLATED_PROCESS_FEATURE_ENABLED;
    }

    default String getCallerAppAllowList() {
        return DEFAULT_CALLER_APP_ALLOW_LIST;
    }

    default String getIsolatedServiceAllowList() {
        return DEFAULT_ISOLATED_SERVICE_ALLOW_LIST;
    }
}
