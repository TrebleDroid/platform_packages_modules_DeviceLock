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

package com.android.devicelockcontroller.debug;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.StringDef;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.annotation.Retention;

/**
 * A {@link BroadcastReceiver} that can handle reset, lock, unlock command.
 * <p>
 * Note:
 * Reboot device are {@link DeviceLockCommandReceiver#onReceive(Context, Intent)} has been called to
 * take effect.
 */
public final class DeviceLockCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "DeviceLockCommandReceiver";
    private static final String EXTRA_COMMAND = "command";

    @Retention(SOURCE)
    @StringDef({
            Commands.RESET,
            Commands.LOCK,
            Commands.UNLOCK,
    })
    private @interface Commands {
        String RESET = "reset";
        String LOCK = "lock";
        String UNLOCK = "unlock";
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.isDebuggable()) {
            throw new SecurityException("This should never be run in production build!");
        }

        if (!TextUtils.equals(intent.getComponent().getClassName(), getClass().getName())) {
            throw new IllegalArgumentException("Intent does not match this class!");
        }

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();
        if (isUserProfile) {
            LogUtil.w(TAG, "Broadcast should not target user profiles");
            return;
        }

        @Commands
        String command = String.valueOf(intent.getStringExtra(EXTRA_COMMAND));
        switch (command) {
            case Commands.RESET:
                UserParameters.clear(context);
                Futures.addCallback(Futures.whenAllSucceed(
                                        SetupParametersClient.getInstance().clear(),
                                        GlobalParametersClient.getInstance().clear())
                                .call(() -> null,
                                        MoreExecutors.directExecutor()),
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(Object v) {
                                LogUtil.i(TAG, "Successfully reset to unprovisioned state.");
                                Process.killProcess(Process.myPid());
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(TAG, "Failed to reset to unprovisioned state!", t);
                            }
                        }, MoreExecutors.directExecutor());
                break;
            case Commands.LOCK:
                if (UserParameters.setDeviceStateSync(context, DeviceState.LOCKED)) {
                    LogUtil.i(TAG, "Successfully put device into locked state!");
                    Process.killProcess(Process.myPid());
                } else {
                    LogUtil.w(TAG, "Failed to put device into locked state!");
                }
                break;
            case Commands.UNLOCK:
                if (UserParameters.setDeviceStateSync(context, DeviceState.UNLOCKED)) {
                    LogUtil.i(TAG, "Successfully to put device into unlocked state!");
                    Process.killProcess(Process.myPid());
                } else {
                    LogUtil.w(TAG, "Failed to put device into locked state!");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported command: " + command);
        }
    }
}
