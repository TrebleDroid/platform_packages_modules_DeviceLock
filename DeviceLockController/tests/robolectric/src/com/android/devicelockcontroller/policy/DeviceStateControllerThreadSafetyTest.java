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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent.PROVISION_PAUSE;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent.PROVISION_READY;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PROVISION_PAUSED;

import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class DeviceStateControllerThreadSafetyTest {
    private DeviceStateControllerImpl mDeviceStateController;

    private static final int NUMBER_OF_THREADS = 100;

    @Before
    public void setUp() {
        TestDeviceLockControllerApplication testApplication =
                ApplicationProvider.getApplicationContext();
        UserParameters.setDeviceState(testApplication,
                DeviceStateController.DeviceState.UNPROVISIONED);
        mDeviceStateController = new DeviceStateControllerImpl(testApplication);
    }

    @Test
    public void setNextStateForEvent_shouldEnforcePolicyOnlyOnceForAnEvent_whenMultithreading() {
        mDeviceStateController.setNextStateForEvent(PROVISION_READY);
        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        AtomicInteger numberPolicyEnforced = new AtomicInteger(/* initialValue= */0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(NUMBER_OF_THREADS);
        // Adds a listener to the device state controller and count device policy enforcements.
        mDeviceStateController.addCallback((int ignored) -> {
            numberPolicyEnforced.incrementAndGet();
            return Futures.immediateVoidFuture();
        });

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    fail(String.format("Test threads interrupted, exception: %s", e));
                }
                mDeviceStateController.setNextStateForEvent(PROVISION_PAUSE);
                finishLatch.countDown();
            });
        }
        // This aims to increase the possibility of a race condition by letting all the threads wait
        // for the CountDownLatch and unblock them at the same time.
        startLatch.countDown();

        try {
            finishLatch.await();
            Truth.assertThat(numberPolicyEnforced.get()).isEqualTo(/* expected= */1);
        } catch(InterruptedException e) {
            fail(String.format("Test threads interrupted, exception: %s", e));
        }
    }

    @Test
    public void setNextStateForEvent_shouldReturnNextStateOnlyOnceForAnEvent_whenMultithreading() {
        mDeviceStateController.setNextStateForEvent(PROVISION_READY);
        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        Collection<Future<Integer>> results = Collections.synchronizedCollection(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(NUMBER_OF_THREADS);

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    fail(String.format("Test threads interrupted, exception: %s", e));
                }
                results.add(mDeviceStateController.setNextStateForEvent(PROVISION_PAUSE));
                finishLatch.countDown();
            });
        }
        // This aims to increase the possibility of a race condition by letting all the threads wait
        // for the CountDownLatch and unblock them at the same time.
        startLatch.countDown();

        try {
            finishLatch.await();
        } catch(InterruptedException e) {
            fail(String.format("Test threads interrupted, exception: %s", e));
        }
        int numberSuccessStateChange = 0;
        for (Future<Integer> state : results) {
            try {
                if (state.get() == PROVISION_PAUSED) {
                    numberSuccessStateChange++;
                }
            } catch (ExecutionException | InterruptedException ignored) {
                // We expect ONLY 1 thread to successfully return the next state, so most of the
                // results returned will produce an exception as expected.
            }
        }
        Truth.assertThat(numberSuccessStateChange).isEqualTo(/* expected= */1);
    }
}
