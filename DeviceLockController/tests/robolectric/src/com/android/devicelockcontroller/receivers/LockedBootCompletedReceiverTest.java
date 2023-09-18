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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class LockedBootCompletedReceiverTest {
    public static final Intent INTENT = new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED);
    private static final long TEST_CURRENT_TIME_MILLIS = Duration.ofHours(5).toMillis();
    private static final Clock TEST_CLOCK = Clock.fixed(
            Instant.ofEpochMilli(TEST_CURRENT_TIME_MILLIS),
            ZoneOffset.UTC);
    private final TestDeviceLockControllerApplication mTestApplication =
            ApplicationProvider.getApplicationContext();
    private ProvisionStateController mProvisionStateController;
    private LockedBootCompletedReceiver mReceiver;
    private DeviceLockControllerScheduler mScheduler;

    @Before
    public void setUp() {
        mProvisionStateController = mTestApplication.getProvisionStateController();
        when(mProvisionStateController.getDevicePolicyController()).thenReturn(
                mTestApplication.getPolicyController());
        mScheduler = mTestApplication.getDeviceLockControllerScheduler();
        mReceiver = new LockedBootCompletedReceiver(TestingExecutors.sameThreadScheduledExecutor(),
                TEST_CLOCK);
    }

    @Test
    public void onReceive_shouldRecordBootTimeAndEnforcePoliciesAndDisableUserControl() {
        long bootTime = TEST_CURRENT_TIME_MILLIS - SystemClock.elapsedRealtime();
        when(mProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(UNPROVISIONED));

        mReceiver.onReceive(mTestApplication, INTENT);

        Futures.getUnchecked(Futures.submit(
                () -> assertThat(UserParameters.getBootTimeMillis(mTestApplication)).isEqualTo(
                        bootTime), Executors.newSingleThreadExecutor()));

        // TODO: Figure out how to verify DevicePolicyManager#getUserControlDisabledPackages()
        //  result.
    }

    @Test
    public void onReceive_provisionPaused_shouldRescheduleResume() {
        when(mProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(PROVISION_PAUSED));

        mReceiver.onReceive(mTestApplication, INTENT);

        verify(mScheduler).notifyRebootWhenProvisionPaused();
    }

    @Test
    public void onReceive_provisionFailed_shouldRescheduleFailedStepAndReset() {
        when(mProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(PROVISION_FAILED));
        mReceiver.onReceive(mTestApplication, INTENT);

        verify(mScheduler).notifyRebootWhenProvisionFailed();
    }
}
