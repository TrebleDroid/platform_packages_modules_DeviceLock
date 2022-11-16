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

package com.android.devicelockcontroller;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallback;

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.StateTransitionException;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * Device Lock Controller Service. This is hosted in an APK and is bound
 * by the Device Lock System Service.
 */
public final class DeviceLockControllerService extends Service {
    private static final String TAG = "DeviceLockControllerService";
    private DeviceStateController mStateController;

    private final IDeviceLockControllerService.Stub mBinder =
            new IDeviceLockControllerService.Stub() {
        @Override
        public void lockDevice(RemoteCallback remoteCallback) {
            boolean success;
            try {
                mStateController.setNextStateForEvent(
                        DeviceStateController.DeviceEvent.LOCK_DEVICE);
                success = true;
            } catch (StateTransitionException e) {
                success = false;
                LogUtil.e(TAG, "Failed to lock device", e);
            }

            final Bundle bundle = new Bundle();
            bundle.putBoolean(IDeviceLockControllerService.KEY_LOCK_DEVICE_RESULT, success);
            remoteCallback.sendResult(bundle);
        }

        @Override
        public void unlockDevice(RemoteCallback remoteCallback) {
            boolean success;
            try {
                mStateController.setNextStateForEvent(
                        DeviceStateController.DeviceEvent.UNLOCK_DEVICE);
                success = true;
            } catch (StateTransitionException e) {
                success = false;
                LogUtil.e(TAG, "Failed to unlock device", e);
            }

            final Bundle bundle = new Bundle();
            bundle.putBoolean(IDeviceLockControllerService.KEY_UNLOCK_DEVICE_RESULT, success);
            remoteCallback.sendResult(bundle);
        }

        @Override
        public void isDeviceLocked(RemoteCallback remoteCallback) {
            final boolean isLocked = mStateController.isLocked();
            final Bundle bundle = new Bundle();
            bundle.putBoolean(IDeviceLockControllerService.KEY_IS_DEVICE_LOCKED_RESULT, isLocked);
            remoteCallback.sendResult(bundle);
        }
    };

    @Override
    public void onCreate() {
        LogUtil.d(TAG, "onCreate");

        final PolicyObjectsInterface policyObjects = (PolicyObjectsInterface) getApplication();
        mStateController = policyObjects.getStateController();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
