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

package com.android.devicelockcontroller.receivers;

import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;
import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Intent;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.DeviceStateController;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivityManager;

@RunWith(RobolectricTestRunner.class)
public class LockTaskBootCompletedReceiverTest {

    private static final Intent BOOT_COMPLETED_INTENT = new Intent(
            Intent.ACTION_BOOT_COMPLETED);

    private LockTaskBootCompletedReceiver mLockTaskBootCompletedReceiver;
    private DeviceStateController mStateController;
    private TestDeviceLockControllerApplication mTestApplication;
    private ShadowActivityManager mActivityManager;

    @Before
    public void setUp() {
        mTestApplication = getApplicationContext();
        mStateController = mTestApplication.getStateController();
        when(mStateController.enforcePoliciesForCurrentState()).thenReturn(
                Futures.immediateVoidFuture());
        mLockTaskBootCompletedReceiver = new LockTaskBootCompletedReceiver();
        mActivityManager = Shadows.shadowOf(
                mTestApplication.getSystemService(ActivityManager.class));
    }

    @Test
    public void onReceive_inLockedState_inLockTaskMode_doNotEnforcePolicies() {
        when(mStateController.isLockedInternal()).thenReturn(true);
        mActivityManager.setLockTaskModeState(LOCK_TASK_MODE_LOCKED);
        mLockTaskBootCompletedReceiver.onReceive(mTestApplication, BOOT_COMPLETED_INTENT);

        verify(mStateController, never()).enforcePoliciesForCurrentState();
    }

    @Test
    public void onReceive_inLockedState_notInLockTaskMode_enforcePolicies() {
        when(mStateController.isLockedInternal()).thenReturn(true);
        mActivityManager.setLockTaskModeState(LOCK_TASK_MODE_NONE);
        mLockTaskBootCompletedReceiver.onReceive(mTestApplication, BOOT_COMPLETED_INTENT);

        verify(mStateController).enforcePoliciesForCurrentState();
    }

    @Test
    public void onReceive_notInLockedState_inLockTaskMode_enforcePolicies() {
        when(mStateController.isLockedInternal()).thenReturn(false);
        mActivityManager.setLockTaskModeState(LOCK_TASK_MODE_LOCKED);
        mLockTaskBootCompletedReceiver.onReceive(mTestApplication, BOOT_COMPLETED_INTENT);

        verify(mStateController).enforcePoliciesForCurrentState();
    }

    @Test
    public void onReceive_notInLockedState_notInLockTaskMode_doNotEnforcePolicies() {
        when(mStateController.isLockedInternal()).thenReturn(false);
        mActivityManager.setLockTaskModeState(LOCK_TASK_MODE_NONE);
        mLockTaskBootCompletedReceiver.onReceive(mTestApplication, BOOT_COMPLETED_INTENT);

        verify(mStateController, never()).enforcePoliciesForCurrentState();
    }
}
