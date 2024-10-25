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
package com.android.ondevicepersonalization.testing.utils;

import static org.junit.Assert.assertTrue;

import android.os.OutcomeReceiver;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A synchronous wrapper around OutcomeReceiver for testing.
 */
public class ResultReceiver<T> implements OutcomeReceiver<T, Exception> {
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final Duration mDeadline;
    private T mResult = null;
    private Exception mException = null;
    private boolean mSuccess = false;
    private boolean mError = false;
    private boolean mCalled = false;

    /** Creates a ResultReceiver. */
    public ResultReceiver() {
        this(Duration.ofSeconds(60));
    }

    /** Creates a ResultReceiver with a deadline. */
    public ResultReceiver(Duration deadline) {
        mDeadline = deadline;
    }

    private void await() throws InterruptedException {
        if (mDeadline != null) {
            assertTrue(mLatch.await(mDeadline.toMillis(), TimeUnit.MILLISECONDS));
        } else {
            mLatch.await();
        }
    }

    @Override public void onResult(T result) {
        mCalled = true;
        mSuccess = true;
        mResult = result;
        mLatch.countDown();
    }

    @Override public void onError(Exception e) {
        mCalled = true;
        mError = true;
        mException = e;
        mLatch.countDown();
    }

    /** Returns the result passed to the OutcomeReceiver. */
    public T getResult() throws InterruptedException {
        await();
        return mResult;
    }

    /** Returns the exception passed to the OutcomeReceiver. */
    public Exception getException() throws InterruptedException {
        await();
        return mException;
    }

    /** Returns true if onResult() was called. */
    public boolean isSuccess() throws InterruptedException {
        await();
        return mSuccess;
    }

    /** Returns true if onError() was called. */
    public boolean isError() throws InterruptedException {
        await();
        return mError;
    }

    /** Returns true if onResult() or onError() was called. */
    public boolean isCalled() throws InterruptedException {
        await();
        return mCalled;
    }

    /** Returns the exception message. */
    public String getErrorMessage() throws InterruptedException {
        try {
            await();
        } catch (Exception e) {
            return "ResultReceiver failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage();
        }
        if (mException != null) {
            return mException.getClass().getSimpleName()
                    + ": " + mException.getMessage();
        }
        return "Error: " + mError;
    }
}
