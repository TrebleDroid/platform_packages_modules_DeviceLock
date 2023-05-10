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

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInHelper;
import com.android.devicelockcontroller.util.LogUtil;

public final class SetupFlowStarter extends BroadcastReceiver {

    private static final String TAG = "SetupFlowStarter";
    private static final String EXTRA_IS_MANDATORY = "is-mandatory";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.isDebuggable()) {
            LogUtil.w(TAG, "Adb command is not supported in non-debuggable build!");
            return;
        }

        if (!TextUtils.equals(intent.getComponent().getClassName(),
                SetupFlowStarter.class.getName())) {
            LogUtil.w(TAG, "Implicit intent should not be used!");
            return;
        }

        PolicyObjectsInterface policyObjects =
                (PolicyObjectsInterface) context.getApplicationContext();
        DevicePolicyController devicePolicyController = policyObjects.getPolicyController();
        DeviceStateController deviceStateController = policyObjects.getStateController();

        DeviceCheckInHelper.setProvisionSucceeded(deviceStateController, devicePolicyController,
                context, intent.getBooleanExtra(EXTRA_IS_MANDATORY, /* defaultValue= */ true));
    }
}
