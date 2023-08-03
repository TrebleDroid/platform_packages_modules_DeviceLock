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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionStateController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class BootCompletedReceiverTest {

    private static final Intent BOOT_COMPLETED_INTENT = new Intent(
            Intent.ACTION_BOOT_COMPLETED);

    private BootCompletedReceiver mBootCompletedReceiver;
    private ProvisionStateController mStateController;
    private TestDeviceLockControllerApplication mTestApplication;

    @Before
    public void setUp() {
        mTestApplication = getApplicationContext();
        mStateController = mTestApplication.getProvisionStateController();
        mBootCompletedReceiver = new BootCompletedReceiver();
    }

    @Test
    public void onReceive_shouldInitStateAndDisableSelf() {
        mBootCompletedReceiver.onReceive(mTestApplication, BOOT_COMPLETED_INTENT);

        verify(mStateController).initState();
        assertThat(mTestApplication.getPackageManager().getComponentEnabledSetting(
                new ComponentName(mTestApplication, BootCompletedReceiver.class))).isEqualTo(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }
}
