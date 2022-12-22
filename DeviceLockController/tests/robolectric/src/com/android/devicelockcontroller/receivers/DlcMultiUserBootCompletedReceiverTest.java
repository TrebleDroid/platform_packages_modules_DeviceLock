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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

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
@Config(shadows = {ShadowApplicationPackageManager.class})
public class DlcMultiUserBootCompletedReceiverTest {
    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();

    // Non-exclusive list of components that will be disabled for secondary users but not for
    // system user.
    private static final List<String> sComponents =
            Arrays.asList("com.google.firebase.components.ComponentDiscoveryService",
                    "com.google.firebase.messaging.FirebaseMessagingService",
                    "com.google.firebase.iid.FirebaseInstanceIdReceiver");

    private DlcMultiUserBootCompletedReceiver mDlcBootCompletedReceiver;

    @Before
    public void setUp() {
        mDlcBootCompletedReceiver = new DlcMultiUserBootCompletedReceiver();
    }

    @Test
    public void disableComponentsForNonSystemUsers_shouldNotDisableComponentsForSystemUser() {
        Shadows.shadowOf(mApplicationContext.getSystemService(UserManager.class))
                .switchUser(UserHandle.USER_SYSTEM);

        mDlcBootCompletedReceiver.disableComponentsForNonSystemUser(mApplicationContext);

        final PackageManager pm = mApplicationContext.getPackageManager();

        for (String component: sComponents) {
            final ComponentName componentName =
                    new ComponentName(mApplicationContext, component);
            assertThat(pm.getComponentEnabledSetting(componentName))
                    .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        }
    }

    @Test
    public void disableComponentsForNonSystemUsers_shouldDisableComponentsForSecondaryUsers() {
        final int userId = 1001;
        Shadows.shadowOf(mApplicationContext.getSystemService(UserManager.class))
                .addUser(userId, "guest", UserInfo.FLAG_GUEST);
        Shadows.shadowOf(mApplicationContext.getSystemService(UserManager.class))
                .switchUser(userId);

        mDlcBootCompletedReceiver.disableComponentsForNonSystemUser(mApplicationContext);

        final PackageManager pm = mApplicationContext.getPackageManager();
        final ShadowPackageManager spm = Shadows.shadowOf(mApplicationContext.getPackageManager());

        for (String component: sComponents) {
            final ComponentName componentName =
                    new ComponentName(mApplicationContext, component);
            assertThat(pm.getComponentEnabledSetting(componentName))
                    .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            assertThat(spm.getComponentEnabledSettingFlags(componentName))
                    .isEqualTo(PackageManager.DONT_KILL_APP);
        }
    }
}
