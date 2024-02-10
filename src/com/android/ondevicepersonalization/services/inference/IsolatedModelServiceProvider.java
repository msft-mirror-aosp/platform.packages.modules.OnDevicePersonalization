/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.inference;

import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.content.Context;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;

/**
 * Provides {@link IsolatedModelService}.
 */
public class IsolatedModelServiceProvider {
    public static final String ISOLATED_MODEL_SERVICE_NAME =
            "com.android.ondevicepersonalization.services.inference.IsolatedModelService";
    private AbstractServiceBinder<IIsolatedModelService> mModelService;


    /**
     * Returns {@link IIsolatedModelService}.
     */
    public IIsolatedModelService getModelService(Context context) {
        // TODO(b/323304647): bind to shared isolated process.
        mModelService =
                AbstractServiceBinder.getServiceBinderByServiceName(
                        context,
                        ISOLATED_MODEL_SERVICE_NAME,
                        context.getPackageName(),
                        IIsolatedModelService.Stub::asInterface);
        return mModelService.getService(Runnable::run);
    }

    /**
     * Unbind {@link IsolatedModelService}.
     */
    public void unBindFromModelService() {
        mModelService.unbindFromService();
    }
}
