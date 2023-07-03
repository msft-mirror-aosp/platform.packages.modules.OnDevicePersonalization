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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.federatedcompute.ExampleStoreIterator;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OdpExampleStoreIteratorTest {
    private final CountDownLatch mLatch = new CountDownLatch(1);

    private boolean mIteratorCallbackOnSuccessCalled = false;
    private boolean mIteratorCallbackOnFailureCalled = false;

    @Test
    public void testNext() {
        OdpExampleStoreIterator it = new OdpExampleStoreIterator();
        it.next(new TestIteratorCallback());
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
    }

    public class TestIteratorCallback implements ExampleStoreIterator.IteratorCallback {

        @Override
        public boolean onIteratorNextSuccess(Bundle result) {
            mIteratorCallbackOnSuccessCalled = true;
            mLatch.countDown();
            return true;
        }

        @Override
        public void onIteratorNextFailure(int errorCode) {
            mIteratorCallbackOnSuccessCalled = true;
            mLatch.countDown();
        }
    }
}
