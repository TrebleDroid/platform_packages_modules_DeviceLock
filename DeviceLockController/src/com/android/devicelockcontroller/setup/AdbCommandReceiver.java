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

package com.android.devicelockcontroller.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.android.devicelockcontroller.util.LogUtil;

/**
 * A helper class used to receive commands from ADB.
 *
 * Used for testing purpose only.
 */
public final class AdbCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "AdbCommandReceiver";
    private static final String ACTION_OVERRIDE_DEVICE_PROVIDER_NAME =
            "com.android.devicelockcontroller.action.OVERRIDE_DEVICE_PROVIDER_NAME";

    private static final String KEY_DEVICE_PROVIDER_NAME = "key-device-provider-name";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.isDebuggable()) {
            LogUtil.w(TAG, "Adb command is only supported in debuggable build!");
        }
        String action = intent.getAction();
        if (ACTION_OVERRIDE_DEVICE_PROVIDER_NAME.equals(action)) {
            final String name = intent.getStringExtra(KEY_DEVICE_PROVIDER_NAME);
            SetupParameters.overrideDeviceProviderName(context, name);
        } else {
            LogUtil.e(TAG, "Unknown action is received, skipping");
        }
    }
}
