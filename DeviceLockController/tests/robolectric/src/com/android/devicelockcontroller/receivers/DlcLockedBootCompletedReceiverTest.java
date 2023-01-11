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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.shadows.ShadowApplicationPackageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class},
        application = TestDeviceLockControllerApplication.class)
public class DlcLockedBootCompletedReceiverTest {
    // Non-exclusive list of components that will be disabled for secondary users but not for
    // system user.
    private static final List<String> sComponents =
            Arrays.asList("com.google.firebase.components.ComponentDiscoveryService",
                    "com.google.firebase.messaging.FirebaseMessagingService",
                    "com.google.firebase.iid.FirebaseInstanceIdReceiver");
    private final TestDeviceLockControllerApplication mTestApplication =
            ApplicationProvider.getApplicationContext();
    private PackageManager mPm;
    private ShadowPackageManager mShadowPackageManager;
    private DeviceStateController mStateController;
    private DevicePolicyController mPolicyController;

    @Before
    public void setUp() {
        mStateController = mTestApplication.getMockStateController();
        mPolicyController = mTestApplication.getMockPolicyController();
        mPm = mTestApplication.getPackageManager();
        mShadowPackageManager = Shadows.shadowOf(mTestApplication.getPackageManager());

    }

    @Test
    public void disableComponentsForNonSystemUsers_shouldNotDisableComponentsForSystemUser() {
        Shadows.shadowOf(mTestApplication.getSystemService(UserManager.class))
                .switchUser(UserHandle.USER_SYSTEM);

        DlcLockedBootCompletedReceiver.disableComponentsForNonSystemUser(mTestApplication);

        for (String component : sComponents) {
            final ComponentName componentName =
                    new ComponentName(mTestApplication, component);
            assertThat(mPm.getComponentEnabledSetting(componentName))
                    .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        }
    }

    @Test
    public void disableComponentsForNonSystemUsers_shouldDisableComponentsForSecondaryUsers() {
        final int userId = 1001;
        Shadows.shadowOf(mTestApplication.getSystemService(UserManager.class))
                .addUser(userId, "guest", UserInfo.FLAG_GUEST);
        Shadows.shadowOf(mTestApplication.getSystemService(UserManager.class))
                .switchUser(userId);

        DlcLockedBootCompletedReceiver.disableComponentsForNonSystemUser(mTestApplication);

        for (String component : sComponents) {
            final ComponentName componentName =
                    new ComponentName(mTestApplication, component);
            assertThat(mPm.getComponentEnabledSetting(componentName))
                    .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            assertThat(mShadowPackageManager.getComponentEnabledSettingFlags(componentName))
                    .isEqualTo(PackageManager.DONT_KILL_APP);
        }
    }

    @Test
    public void
            startLockTaskModeIfApplicable_whenDeviceIsInSetupState_doesNotStartLockTaskMode() {
        when(mStateController.isInSetupState()).thenReturn(true);
        when(mStateController.isLocked()).thenReturn(true);

        DlcLockedBootCompletedReceiver.startLockTaskModeIfApplicable(mTestApplication);

        verify(mPolicyController, never()).launchActivityInLockedMode();

        final ComponentName componentName =
                new ComponentName(mTestApplication, LockTaskBootCompletedReceiver.class);
        assertThat(mPm.getComponentEnabledSetting(componentName))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        assertThat(mShadowPackageManager.getComponentEnabledSettingFlags(componentName))
                .isEqualTo(PackageManager.DONT_KILL_APP);
    }

    @Test
    public void
            startLockTaskModeIfApplicable_whenDeviceIsNotInSetupState_startLockTaskMode() {
        when(mStateController.isInSetupState()).thenReturn(false);
        when(mStateController.isLocked()).thenReturn(true);

        DlcLockedBootCompletedReceiver.startLockTaskModeIfApplicable(mTestApplication);

        verify(mPolicyController).launchActivityInLockedMode();

        final ComponentName componentName =
                new ComponentName(mTestApplication, LockTaskBootCompletedReceiver.class);
        assertThat(mPm.getComponentEnabledSetting(componentName))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        assertThat(mShadowPackageManager.getComponentEnabledSettingFlags(componentName))
                .isEqualTo(PackageManager.DONT_KILL_APP);
    }
}
