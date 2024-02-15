/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.ondevicepersonalization.cts.e2e;

import android.os.OutcomeReceiver;

import java.util.concurrent.CountDownLatch;

class ResultReceiver<T> implements OutcomeReceiver<T, Exception> {
    private CountDownLatch mLatch = new CountDownLatch(1);
    private T mResult;
    private Exception mException;
    @Override public void onResult(T result) {
        mResult = result;
        mLatch.countDown();
    }
    @Override public void onError(Exception e) {
        mException = e;
        mLatch.countDown();
    }
    T getResult() throws InterruptedException {
        mLatch.await();
        return mResult;
    }
    Exception getException() throws InterruptedException {
        mLatch.await();
        return mException;
    }
}
