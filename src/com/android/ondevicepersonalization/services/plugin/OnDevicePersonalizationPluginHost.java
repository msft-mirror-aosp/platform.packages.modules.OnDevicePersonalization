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

package com.android.ondevicepersonalization.services.plugin;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;

import com.android.ondevicepersonalization.libraries.plugin.PluginContext;
import com.android.ondevicepersonalization.libraries.plugin.PluginHost;

import com.google.common.collect.ImmutableSet;

import java.util.Objects;

/** Plugin Support code shared between the managing process and the isolated process. */
public class OnDevicePersonalizationPluginHost implements PluginHost {
    private static final String MANAGING_SERVICE_CONNECTOR_KEY = "managingserviceconnector";
    private final Context mApplicationContext;

    public OnDevicePersonalizationPluginHost(@NonNull Context applicationContext) {
        mApplicationContext = Objects.requireNonNull(applicationContext);
    }

    @Override
    public ImmutableSet<String> getClassLoaderAllowedPackages(String pluginId) {
        return ImmutableSet.of(
                "com.android.ondevicepersonalization.services",
                "com.android.ondevicepersonalization.libraries");
    }

    /** Creates a PluginContext in the isolated process. */
    @Override
    @Nullable
    public PluginContext createPluginContext(
            @NonNull String pluginId, @Nullable Bundle initData) {
        IManagingServiceConnector connector = IManagingServiceConnector.Stub.asInterface(
                Objects.requireNonNull(
                        initData.getBinder(MANAGING_SERVICE_CONNECTOR_KEY)));
        return new OnDevicePersonalizationPluginContext(connector);
    }

    /** Serializes data needed to create the PluginContext in the isolated process. */
    @Override
    @Nullable
    public Bundle createPluginContextInitData(@NonNull String pluginId) {
        // TODO(b/249345663): Encode appPackageName and vendorPackageName into pluginId, then parse
        // pluginId to extract the appPackageName and vendorPackageName and create a
        // ManagingServiceConnector customized to the app and vendor package.
        ManagingServiceConnector connector = new ManagingServiceConnector(mApplicationContext);
        Bundle initData = new Bundle();
        initData.putBinder(MANAGING_SERVICE_CONNECTOR_KEY, connector);
        return initData;
    }
}
