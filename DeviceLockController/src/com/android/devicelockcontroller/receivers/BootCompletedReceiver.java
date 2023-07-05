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

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;

/**
 * Boot completed broadcast receiver to start lock task mode if applicable. This broadcast receiver
 * runs for every user on the device.
 * Note that this boot completed receiver differs with {@link SingleUserBootCompletedReceiver} in
 * the way that it runs for any users.
 */
public final class BootCompletedReceiver extends BroadcastReceiver {

    static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) return;

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();

        if (isUserProfile) {
            return;
        }

        DeviceStateController stateController =
                ((PolicyObjectsInterface) context.getApplicationContext())
                        .getPolicyController().getStateController();
        ActivityManager am =
                Objects.requireNonNull(context.getSystemService(ActivityManager.class));
        if (stateController.isLockedInternal()
                != (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED)) {
            Futures.addCallback(stateController.enforcePoliciesForCurrentState(),
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Void result) {
                            LogUtil.i(TAG, "Successfully called enforcePoliciesForCurrentState()");
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            LogUtil.e(TAG, "Failed to call enforcePoliciesForCurrentState()", t);
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }
}
