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

import androidx.annotation.NonNull;

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.storage.GlobalParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

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
                    Futures.addCallback(mStateController.setNextStateForEvent(
                                    DeviceStateController.DeviceEvent.LOCK_DEVICE),
                            remoteCallbackWrapper(remoteCallback, KEY_LOCK_DEVICE_RESULT),
                            MoreExecutors.directExecutor());
                }

                @Override
                public void unlockDevice(RemoteCallback remoteCallback) {
                    Futures.addCallback(mStateController.setNextStateForEvent(
                                    DeviceStateController.DeviceEvent.UNLOCK_DEVICE),
                            remoteCallbackWrapper(remoteCallback, KEY_UNLOCK_DEVICE_RESULT),
                            MoreExecutors.directExecutor());

                }

                @Override
                public void isDeviceLocked(RemoteCallback remoteCallback) {
                    final boolean isLocked = mStateController.isLocked();
                    sendResult(IDeviceLockControllerService.KEY_IS_DEVICE_LOCKED_RESULT,
                            remoteCallback, isLocked);
                }

                @Override
                public void getDeviceIdentifier(RemoteCallback remoteCallback) {
                    final Bundle bundle = new Bundle();
                    final String deviceId = GlobalParameters.getRegisteredDeviceId(
                            DeviceLockControllerService.this);
                    // The deviceId should NOT be null because this method is only supposed to be
                    // called AFTER the provision, which will store the deviceId on the device.
                    // But the unexpected case of a null deviceId should be handled in DeviceLock
                    // service, in
                    // packages/modules/DeviceLock/service/java/com/android/server/devicelock.
                    bundle.putString(IDeviceLockControllerService.KEY_HARDWARE_ID_RESULT, deviceId);
                    remoteCallback.sendResult(bundle);
                }

                @Override
                public void clearDevice(RemoteCallback remoteCallback) {
                    Futures.addCallback(mStateController.setNextStateForEvent(
                                    DeviceStateController.DeviceEvent.CLEAR),
                            remoteCallbackWrapper(remoteCallback, KEY_CLEAR_DEVICE_RESULT),
                            MoreExecutors.directExecutor());

                }
            };

    @NonNull
    private static FutureCallback<Void> remoteCallbackWrapper(RemoteCallback remoteCallback,
            final String key) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(Void result) {
                sendResult(key, remoteCallback, true);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to lock device", t);
                sendResult(key, remoteCallback, false);
            }
        };
    }

    private static void sendResult(String key, RemoteCallback remoteCallback, boolean result) {
        final Bundle bundle = new Bundle();
        bundle.putBoolean(key, result);
        remoteCallback.sendResult(bundle);
    }

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
