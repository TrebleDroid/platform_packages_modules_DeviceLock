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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.KIOSK_SETUP;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PSEUDO_LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PSEUDO_UNLOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_FAILED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_PAUSED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.SETUP_SUCCEEDED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNPROVISIONED;

import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClientInterface;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

/** Handles kiosk app keepalive. */
public final class KioskKeepalivePolicyHandler implements PolicyHandler {
    private static final String TAG = "KioskKeepalivePolicyHandler";

    private final Context mContext;
    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final SetupParametersClientInterface mSetupParametersClient;

    KioskKeepalivePolicyHandler(Context context, SystemDeviceLockManager systemDeviceLockManager) {
        mContext = context;
        mSystemDeviceLockManager = systemDeviceLockManager;
        mSetupParametersClient = SetupParametersClient.getInstance();
    }

    private ListenableFuture<@ResultType Integer>
            getEnableKioskKeepaliveFuture(String packageName) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.enableKioskKeepalive(packageName,
                            mContext.getMainExecutor(),
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(SUCCESS);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to enable kiosk keepalive",
                                            ex);
                                    // Return SUCCESS since the keepalive service is optional
                                    completer.set(SUCCESS);
                                }
                            });
                    // Used only for debugging.
                    return "getEnableKioskKeepaliveFuture";
                });
    }

    private ListenableFuture<@ResultType Integer>
            getEnableKioskKeepaliveFuture() {
        return Futures.transformAsync(mSetupParametersClient.getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(FAILURE)
                        : getEnableKioskKeepaliveFuture(kioskPackageName),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<@ResultType Integer>
            getDisableKioskKeepaliveFuture() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.disableKioskKeepalive(mContext.getMainExecutor(),
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(SUCCESS);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to disable kiosk keepalive",
                                            ex);
                                    // Return SUCCESS since the keepalive service is optional
                                    completer.set(SUCCESS);
                                }
                            });
                    // Used only for debugging.
                    return "getDisableKioskKeepaliveFuture";
                });
    }

    @Override
    public ListenableFuture<@ResultType Integer> setPolicyForState(@DeviceState int state) {
        switch (state) {
            case UNPROVISIONED:
            case SETUP_IN_PROGRESS:
            case SETUP_SUCCEEDED:
            case SETUP_FAILED:
            case SETUP_PAUSED:
            case PSEUDO_LOCKED:
            case PSEUDO_UNLOCKED:
                return Futures.immediateFuture(SUCCESS);
            case LOCKED:
            case KIOSK_SETUP:
                return getEnableKioskKeepaliveFuture();
            case UNLOCKED:
            case CLEARED:
                return getDisableKioskKeepaliveFuture();
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
    }
}
