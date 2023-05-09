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

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent.PROVISIONING_SUCCESS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.StateTransitionException;
import com.android.devicelockcontroller.storage.GlobalParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Locale;

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

        FutureCallback<Void> futureCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(Void result) {
                LogUtil.i(TAG,
                        String.format(Locale.US,
                                "State transition succeeded for event: %s",
                                DeviceStateController.eventToString(PROVISIONING_SUCCESS)));
                devicePolicyController.enqueueStartLockTaskModeWorker(
                        intent.getBooleanExtra(EXTRA_IS_MANDATORY, /* defaultValue= */ true));
            }

            @Override
            public void onFailure(Throwable t) {
                //TODO: Reset the state to where it can successfully transition.
                LogUtil.e(TAG,
                        String.format(Locale.US,
                                "State transition failed for event: %s",
                                DeviceStateController.eventToString(PROVISIONING_SUCCESS)), t);
            }
        };
        GlobalParameters.setNeedCheckIn(context, false);
        context.getMainExecutor().execute(
                () -> {
                    try {
                        Futures.addCallback(
                                deviceStateController.setNextStateForEvent(PROVISIONING_SUCCESS),
                                futureCallback, MoreExecutors.directExecutor());
                    } catch (StateTransitionException e) {
                        //TODO: Reset the state to where it can successfully transition.
                        LogUtil.e(TAG,
                                String.format(Locale.US,
                                        "State transition failed for event: %s",
                                        DeviceStateController.eventToString(PROVISIONING_SUCCESS)),
                                e);
                    }
                });
    }
}
