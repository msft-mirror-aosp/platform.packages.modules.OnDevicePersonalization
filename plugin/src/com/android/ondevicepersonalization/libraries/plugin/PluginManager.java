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

package com.android.ondevicepersonalization.libraries.plugin;

/** Entry-point interface for creating {@link PluginController}. */
public interface PluginManager {

    /**
     * Create PluginController handle.
     *
     * <p>Creating the PluginController attempts to bind to a service, which is used to load &
     * execute the plugin. If the bindService() call fails, that means the service cannot be found
     * and a ServiceNotFoundException is thrown.
     */
    PluginController createPluginController(PluginInfo info) throws ServiceNotFoundException;

    /** Exception indicating that the service was not found. */
    class ServiceNotFoundException extends Exception {
        public ServiceNotFoundException(String message) {
            super(message);
        }
    }
}
