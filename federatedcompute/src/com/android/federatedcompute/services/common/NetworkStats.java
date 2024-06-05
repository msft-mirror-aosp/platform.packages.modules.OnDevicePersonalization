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

package com.android.federatedcompute.services.common;

import com.android.internal.util.Preconditions;
import com.android.odp.module.common.Clock;
import com.android.odp.module.common.MonotonicClock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Collection of network usage statistics. */
public class NetworkStats {
    private static final String TAG = NetworkStats.class.getSimpleName();
    private final AtomicLong mTotalBytesDownloaded;
    private final AtomicLong mTotalBytesUploaded;
    private final AtomicBoolean mStartTimeIsSet = new AtomicBoolean(false);
    private final AtomicBoolean mEndTimeIsSet = new AtomicBoolean(false);

    private long mDataTransferStartTime;

    private long mDataTransferDurationInMillis = 0L;
    private final Clock mClock;

    public NetworkStats() {
        this.mTotalBytesUploaded = new AtomicLong(0);
        this.mTotalBytesDownloaded = new AtomicLong(0);
        this.mClock = MonotonicClock.getInstance();
    }

    /** Adds download bytes to existing counter. */
    public void addBytesDownloaded(long bytesDownloaded) {
        mTotalBytesDownloaded.addAndGet(bytesDownloaded);
    }

    /** Adds upload bytes to existing counter. */
    public void addBytesUploaded(long bytesUploaded) {
        mTotalBytesUploaded.addAndGet(bytesUploaded);
    }

    /** Records the time of starting data transfer e.g. download/upload models. */
    public void recordStartTimeNow() {
        Preconditions.checkArgument(
                !mStartTimeIsSet.get(),
                "NetworkStats data transfer start time should only set once.");
        mDataTransferStartTime = mClock.elapsedRealtime();
        mStartTimeIsSet.getAndSet(true);
    }

    /** Records the time of ending data transfer e.g. download/upload models. */
    public void recordEndTimeNow() {
        Preconditions.checkArgument(
                mStartTimeIsSet.get(),
                "NetworkStats data transfer start time should be set when record end time");
        Preconditions.checkArgument(
                !mEndTimeIsSet.get(), "NetworkStats data transfer end time should only set once.");
        mDataTransferDurationInMillis = mClock.elapsedRealtime() - mDataTransferStartTime;
        mEndTimeIsSet.getAndSet(true);
    }

    public long getDataTransferDurationInMillis() {
        return mDataTransferDurationInMillis;
    }

    /** Gets total download bytes. */
    public long getTotalBytesDownloaded() {
        return mTotalBytesDownloaded.get();
    }

    /** Gets total upload bytes. */
    public long getTotalBytesUploaded() {
        return mTotalBytesUploaded.get();
    }
}
