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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.shadows.ShadowApplicationPackageManager;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class})
public final class LockedBootCompletedReceiverTest {
    private final TestDeviceLockControllerApplication mTestApplication =
            ApplicationProvider.getApplicationContext();
    private PackageManager mPm;
    private ShadowPackageManager mShadowPackageManager;
    private DeviceStateController mStateController;

    @Before
    public void setUp() {
        mStateController = mTestApplication.getStateController();
        when(mStateController.enforcePoliciesForCurrentState()).thenReturn(
                Futures.immediateVoidFuture());
        mPm = mTestApplication.getPackageManager();
        mShadowPackageManager = Shadows.shadowOf(mTestApplication.getPackageManager());

    }

    @Test
    public void startLockTaskModeIfApplicable_whenInProvisioningState_doesNotStartLockTaskMode() {
        when(mStateController.isInProvisioningState()).thenReturn(true);
        when(mStateController.isLockedInternal()).thenReturn(true);

        LockedBootCompletedReceiver.enforceLockTaskMode(mTestApplication);

        final ComponentName componentName =
                new ComponentName(mTestApplication, BootCompletedReceiver.class);
        assertThat(mPm.getComponentEnabledSetting(componentName))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        assertThat(mShadowPackageManager.getComponentEnabledSettingFlags(componentName))
                .isEqualTo(PackageManager.DONT_KILL_APP);
    }

    @Test
    public void startLockTaskModeIfApplicable_whenNotInProvisioningState_startLockTaskMode() {
        when(mStateController.isInProvisioningState()).thenReturn(false);
        when(mStateController.isLockedInternal()).thenReturn(true);

        LockedBootCompletedReceiver.enforceLockTaskMode(mTestApplication);

        final ComponentName componentName =
                new ComponentName(mTestApplication, BootCompletedReceiver.class);
        assertThat(mPm.getComponentEnabledSetting(componentName))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertThat(mShadowPackageManager.getComponentEnabledSettingFlags(componentName))
                .isEqualTo(PackageManager.DONT_KILL_APP);
    }
}
