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

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ComponentEnabledSetting;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.os.UserManager;
import android.util.ArraySet;

import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.DeviceLockControllerService;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Handle boot completed. This receiver runs for any user (singleUser="false").
 * Disable unneeded components for non system users.
 */
public final class DlcMultiUserBootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedBroadcastReceiver";

    private static final Set<String> sComponentAllowlist = new ArraySet<>(Arrays.asList(
            DlcMultiUserBootCompletedReceiver.class.getCanonicalName(),
            DeviceLockControllerService.class.getCanonicalName(),
            DlcBootCompletedReceiver.class.getCanonicalName(),
            DlcDeviceAdminReceiver.class.getCanonicalName()));

    private static void addComponentNamesToEnabledList(
            List<ComponentEnabledSetting> componentEnabledSettings,
            ComponentInfo[] componentInfoList) {
        if (componentInfoList == null) {
            return;
        }

        for (ComponentInfo componentInfo: componentInfoList) {
            if (!sComponentAllowlist.contains(componentInfo.name)) {
                final ComponentName componentName =
                        new ComponentName(componentInfo.packageName, componentInfo.name);
                final ComponentEnabledSetting componentEnabledSetting =
                        new ComponentEnabledSetting(componentName, COMPONENT_ENABLED_STATE_DISABLED,
                                DONT_KILL_APP);
                componentEnabledSettings.add(componentEnabledSetting);
            }
        }
    }

    @VisibleForTesting
    static void disableComponentsForNonSystemUser(Context context) {
        final UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager.isSystemUser()) {
            return;
        }

        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();
        final long flags = PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                | PackageManager.GET_PROVIDERS;
        final PackageInfo packageInfo;

        try {
            packageInfo = pm.getPackageInfo(packageName, PackageInfoFlags.of(flags));
        } catch (NameNotFoundException ex) {
            LogUtil.e(TAG, "Failed to get device lock controller components", ex);

            return;
        }

        final List<ComponentEnabledSetting> componentEnabledSettings = new ArrayList<>();

        addComponentNamesToEnabledList(componentEnabledSettings, packageInfo.services);
        addComponentNamesToEnabledList(componentEnabledSettings, packageInfo.receivers);
        addComponentNamesToEnabledList(componentEnabledSettings, packageInfo.providers);

        if (!componentEnabledSettings.isEmpty()) {
            pm.setComponentEnabledSettings(componentEnabledSettings);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.d(TAG, "Locked Boot completed");
        if (!intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            return;
        }

        disableComponentsForNonSystemUser(context);
    }
}
