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

package com.android.federatedcompute.internal.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import com.android.internal.annotations.GuardedBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Function;

class AndroidServiceBinder<T> extends AbstractServiceBinder<T> {
    private static final String TAG = AndroidServiceBinder.class.getSimpleName();

    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5000;
    private final String mServiceIntentAction;
    private final Function<IBinder, T> mBinderConverter;
    private final Context mContext;
    // Concurrency mLock.
    private final Object mLock = new Object();
    // A CountDownloadLatch which will be opened when the connection is established or any error
    // occurs.
    private CountDownLatch mConnectionCountDownLatch;

    @GuardedBy("mLock")
    private T mService;

    @GuardedBy("mLock")
    private ServiceConnection mServiceConnection;

    AndroidServiceBinder(
            @NonNull Context context,
            @NonNull String serviceIntentAction,
            @NonNull Function<IBinder, T> converter) {
        this.mServiceIntentAction = serviceIntentAction;
        this.mContext = context;
        this.mBinderConverter = converter;
    }

    @Override
    public T getService(@NonNull Executor executor) {
        synchronized (mLock) {
            if (mService != null) {
                return mService;
            }
            if (mServiceConnection == null) {
                Intent intent = new Intent(mServiceIntentAction);
                ComponentName serviceComponent = resolveComponentName(intent);
                if (serviceComponent == null) {
                    LogUtil.e(TAG, "Invalid component for %s intent", mServiceIntentAction);
                    throw new IllegalStateException(
                            String.format(
                                    "Invalid component for %s service", mServiceIntentAction));
                }
                intent.setComponent(serviceComponent);
                // This latch will open when the connection is established or any error occurs.
                mConnectionCountDownLatch = new CountDownLatch(1);
                mServiceConnection = new GenericServiceConnection();
                boolean result =
                        mContext.bindService(
                                intent, Context.BIND_AUTO_CREATE, executor, mServiceConnection);
                if (!result) {
                    mServiceConnection = null;
                    throw new IllegalStateException(
                            String.format(
                                    "Unable to bind to the service %s", mServiceIntentAction));
                } else {
                    LogUtil.i(TAG, "bindService() %s succeeded...", mServiceIntentAction);
                }
            } else {
                LogUtil.i(TAG, "bindService() %s already pending...", mServiceIntentAction);
            }
        }
        // release the lock to let connection to set the mFcpService
        try {
            mConnectionCountDownLatch.await(BINDER_CONNECTION_TIMEOUT_MS, MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Thread interrupted"); // TODO Handle it better.
        }
        synchronized (mLock) {
            if (mService == null) {
                throw new IllegalStateException(
                        String.format(
                                "Failed to connect to the service %s", mServiceIntentAction));
            }
            return mService;
        }
    }

    /**
     * Find the ComponentName of the service, given its intent and package manager.
     *
     * @return ComponentName of the service. Null if the service is not found.
     */
    @Nullable
    private ComponentName resolveComponentName(@NonNull Intent intent) {
        List<ResolveInfo> services = mContext.getPackageManager().queryIntentServices(intent, 0);
        if (services == null || services.isEmpty()) {
            LogUtil.e(TAG, "Failed to find service %s!", intent.getAction());
            return null;
        }

        for (int i = 0; i < services.size(); i++) {
            ServiceInfo serviceInfo = services.get(i).serviceInfo;
            if (serviceInfo == null) {
                LogUtil.e(TAG, "Failed to find %s serviceInfo.", intent.getAction());
                return null;
            }
            // There should only be one matching service inside the given package.
            // If there's more than one, return the first one found.
            LogUtil.d(TAG, "Resolved component with pkg %s, class %s",
                    serviceInfo.packageName, serviceInfo.name);
            return new ComponentName(serviceInfo.packageName, serviceInfo.name);
        }
        LogUtil.e(TAG, "Didn't find any matching service %s.", intent.getAction());
        return null;
    }

    public void unbindFromService() {
        synchronized (mLock) {
            if (mServiceConnection != null) {
                LogUtil.d(TAG, "unbinding %s...", mServiceIntentAction);
                mContext.unbindService(mServiceConnection);
            }
            mServiceConnection = null;
            mService = null;
        }
    }

    private class GenericServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogUtil.d(TAG, "onServiceConnected " + mServiceIntentAction);
            synchronized (mLock) {
                mService = mBinderConverter.apply(service);
            }
            mConnectionCountDownLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtil.d(TAG, "onServiceDisconnected " + mServiceIntentAction);
            unbindFromService();
            mConnectionCountDownLatch.countDown();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            LogUtil.e(TAG, "onBindingDied " + mServiceIntentAction);
            unbindFromService();
            mConnectionCountDownLatch.countDown();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            LogUtil.e(TAG, "onNullBinding shouldn't happen. " + mServiceIntentAction);
            unbindFromService();
            mConnectionCountDownLatch.countDown();
        }
    }
}
