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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.os.UserManager;

import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.storage.UserParameters;

import java.time.Clock;
import java.time.Instant;

/**
 * Boot completed broadcast receiver to initialize the user. This broadcast receiver
 * runs for every user on the device.
 * Note that this receiver will disable itself after the initial run.
 */
public final class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) return;

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();

        if (isUserProfile) {
            return;
        }


        Instant bootTimeStamp = Instant.now(Clock.systemUTC()).minusMillis(
                SystemClock.elapsedRealtime());
        UserParameters.setBootTimeMillis(context, bootTimeStamp.toEpochMilli());

        ProvisionStateController userStateController =
                ((PolicyObjectsInterface) context.getApplicationContext())
                        .getProvisionStateController();
        userStateController.initState();
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, this.getClass()),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
}
