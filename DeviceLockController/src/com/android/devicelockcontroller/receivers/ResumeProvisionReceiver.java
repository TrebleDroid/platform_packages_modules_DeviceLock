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

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent.PROVISION_RESUME;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PROVISION_IN_PROGRESS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * A broadcast receiver to trigger {@link DeviceStateController.DeviceEvent#PROVISION_RESUME} and
 * change state to {@link DeviceStateController.DeviceState#PROVISION_IN_PROGRESS}
 */
public final class ResumeProvisionReceiver extends BroadcastReceiver {

    public static final String TAG = "ResumeProvisionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ResumeProvisionReceiver.class.getName().equals(
                intent.getComponent().getClassName())) {
            throw new IllegalArgumentException("Can not handle implicit intent!");
        }
        DeviceStateController stateController =
                ((PolicyObjectsInterface) context.getApplicationContext()).getStateController();
        Futures.addCallback(stateController.setNextStateForEvent(PROVISION_RESUME),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Integer newState) {
                        LogUtil.v(TAG, "DeviceState is: " + newState);
                        if (PROVISION_IN_PROGRESS != newState) {
                            onFailure(new IllegalStateException(
                                    "New state is not PROVISION_IN_PROGRESS. New state is: "
                                            + newState));
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(
                                "Failed to transit state from PROVISION_PAUSED to "
                                        + "PROVISION_IN_PROGRESS",
                                t);
                    }
                }, MoreExecutors.directExecutor());
    }
}
