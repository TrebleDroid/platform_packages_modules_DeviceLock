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

import android.app.AppOpsManager;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.Process;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

final class AppOpsPolicyHandler implements PolicyHandler {
    private static final String TAG = "AppOpsPolicyHandler";
    // The following should be a SystemApi on AppOpsManager.
    private static final String OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION =
            "android:system_exempt_from_activity_bg_start_restriction";
    private final Context mContext;
    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final AppOpsManager mAppOpsManager;

    AppOpsPolicyHandler(Context context, SystemDeviceLockManager systemDeviceLockManager,
            AppOpsManager appOpsManager) {
        mContext = context;
        mSystemDeviceLockManager = systemDeviceLockManager;
        mAppOpsManager = appOpsManager;
    }

    private ListenableFuture<@ResultType Integer>
            getExemptFromBackgroundStartRestrictionsFuture(boolean exempt) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.setExemptFromActivityBackgroundStartRestriction(exempt,
                            mContext.getMainExecutor(),
                            new OutcomeReceiver<Void, Exception>() {
                                @Override
                                public void onResult(Void unused) {
                                    completer.set(SUCCESS);
                                }

                                @Override
                                public void onError(Exception error) {
                                    LogUtil.e(TAG, "Cannot set background start exemption", error);
                                    completer.set(FAILURE);
                                }
                            });
                    // Used only for debugging.
                    return "getExemptFromBackgroundStartRestrictionFuture";
                });
    }

    @Override
    public ListenableFuture<@ResultType Integer> setPolicyForState(@DeviceState int state) {
        switch (state) {
            case DeviceStateController.DeviceState.PSEUDO_LOCKED:
            case DeviceStateController.DeviceState.PSEUDO_UNLOCKED:
                return Futures.immediateFuture(SUCCESS);
            case DeviceStateController.DeviceState.SETUP_IN_PROGRESS:
            case DeviceStateController.DeviceState.SETUP_SUCCEEDED:
            case DeviceStateController.DeviceState.SETUP_FAILED:
            case DeviceStateController.DeviceState.UNLOCKED:
            case DeviceStateController.DeviceState.LOCKED:
            case DeviceStateController.DeviceState.KIOSK_SETUP:
                return getExemptFromBackgroundStartRestrictionsFuture(true /* exempt */);
            case DeviceStateController.DeviceState.UNPROVISIONED:
            case DeviceStateController.DeviceState.CLEARED:
                return getExemptFromBackgroundStartRestrictionsFuture(false /* exempt */);
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
    }

    @Override
    public ListenableFuture<Boolean> isCompliant(@DeviceState int state) {
        final int mode = mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION,
                Process.myUid(), mContext.getPackageName());

        switch (state) {
            case DeviceStateController.DeviceState.PSEUDO_LOCKED:
            case DeviceStateController.DeviceState.PSEUDO_UNLOCKED:
                return Futures.immediateFuture(true);
            case DeviceStateController.DeviceState.SETUP_IN_PROGRESS:
            case DeviceStateController.DeviceState.SETUP_SUCCEEDED:
            case DeviceStateController.DeviceState.SETUP_FAILED:
            case DeviceStateController.DeviceState.UNLOCKED:
            case DeviceStateController.DeviceState.LOCKED:
            case DeviceStateController.DeviceState.KIOSK_SETUP:
                return Futures.immediateFuture(mode == AppOpsManager.MODE_ALLOWED);
            case DeviceStateController.DeviceState.UNPROVISIONED:
            case DeviceStateController.DeviceState.CLEARED:
                return Futures.immediateFuture(mode == AppOpsManager.MODE_DEFAULT);
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
    }
}
