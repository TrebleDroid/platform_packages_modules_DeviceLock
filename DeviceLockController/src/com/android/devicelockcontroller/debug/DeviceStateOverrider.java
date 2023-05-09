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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.Locale;

public final class DeviceStateOverrider extends BroadcastReceiver {

    private static final int UNKNOWN_STATE = -1;
    private static final String TAG = "DeviceStateOverrider";
    private static final String EXTRA_NEW_STATE = "new-state";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.isDebuggable()) {
            LogUtil.w(TAG, "Adb command is not supported in non-debuggable build!");
            return;
        }

        if (!TextUtils.equals(intent.getComponent().getClassName(), getClass().getName())) {
            LogUtil.w(TAG, "Implicit intent should not be used!");
            return;
        }

        @DeviceState
        int newState = intent.getIntExtra(EXTRA_NEW_STATE, UNKNOWN_STATE);
        switch (newState) {
            case DeviceState.UNPROVISIONED -> {
                UserParameters.clear(context);
                LogUtil.i(TAG, "State has been set to UNPROVISIONED!");
            }
            //TODO: should support overriding other states as well.
            default -> LogUtil.w(TAG, String.format(Locale.US, "Unsupported state: %d!", newState));
        }
    }
}
