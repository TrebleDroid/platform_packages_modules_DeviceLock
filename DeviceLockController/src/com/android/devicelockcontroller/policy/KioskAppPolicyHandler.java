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

package com.android.devicelockcontroller.policy;

import android.app.admin.DevicePolicyManager;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;

/** Enforces restrictions on Kiosk app. */
final class KioskAppPolicyHandler implements PolicyHandler {
    private static final String TAG = "KioskAppPolicyHandler";

    private final DevicePolicyManager mDpm;

    KioskAppPolicyHandler(DevicePolicyManager dpm) {
        mDpm = dpm;
    }

    @Override
    public ListenableFuture<@ResultType Integer> setPolicyForState(@DeviceState int state) {
        switch (state) {
            case DeviceState.KIOSK_SETUP:
            case DeviceState.UNLOCKED:
            case DeviceState.LOCKED:
                return enableKioskPackageProtection(true);
            case DeviceState.CLEARED:
                return enableKioskPackageProtection(false);
            case DeviceState.UNPROVISIONED:
            case DeviceState.SETUP_IN_PROGRESS:
            case DeviceState.SETUP_SUCCEEDED:
            case DeviceState.SETUP_FAILED:
            case DeviceState.PSEUDO_LOCKED:
            case DeviceState.PSEUDO_UNLOCKED:
                return Futures.immediateFuture(SUCCESS);
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
    }

    @Override
    public ListenableFuture<Boolean> isCompliant(@DeviceState int state) {
        switch (state) {
            case DeviceState.UNLOCKED:
            case DeviceState.LOCKED:
            case DeviceState.KIOSK_SETUP:
                return isKioskPackageProtected();
            case DeviceState.CLEARED:
                return Futures.transform(isKioskPackageProtected(), result -> !result,
                        MoreExecutors.directExecutor());
            case DeviceState.UNPROVISIONED:
            case DeviceState.SETUP_IN_PROGRESS:
            case DeviceState.SETUP_SUCCEEDED:
            case DeviceState.SETUP_FAILED:
                return Futures.immediateFuture(true);
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
    }

    private ListenableFuture<Boolean> isKioskPackageProtected() {
        return Futures.transform(SetupParametersClient.getInstance().getKioskPackage(),
                packageName -> {
                    if (packageName == null) {
                        LogUtil.e(TAG, "Kiosk package is not set");
                        return false;
                    }

                    try {
                        if (!mDpm.isUninstallBlocked(null /* admin */, packageName)) {
                            return false;
                        }
                    } catch (SecurityException e) {
                        LogUtil.e(TAG, "Could not read device policy", e);
                        return false;
                    }

                    final List<String> packages;
                    try {
                        packages = mDpm.getUserControlDisabledPackages(null /* admin */);
                    } catch (SecurityException e) {
                        LogUtil.e(TAG, "Could not read device policy");
                        return false;
                    }

                    return packages != null && packages.contains(packageName);
                }, MoreExecutors.directExecutor());

    }

    private ListenableFuture<@ResultType Integer> enableKioskPackageProtection(boolean enable) {
        return Futures.transform(SetupParametersClient.getInstance().getKioskPackage(),
                packageName -> {
                    if (packageName == null) {
                        LogUtil.e(TAG, "Kiosk package is not set");
                        return FAILURE;
                    }

                    try {
                        mDpm.setUninstallBlocked(null /* admin */, packageName, enable);
                    } catch (SecurityException e) {
                        LogUtil.e(TAG, "Unable to set device policy", e);
                        return FAILURE;
                    }

                    final List<String> pkgList = new ArrayList<>();
                    if (enable) {
                        pkgList.add(packageName);
                    }

                    try {
                        mDpm.setUserControlDisabledPackages(null /* admin */, pkgList);
                    } catch (SecurityException e) {
                        LogUtil.e(TAG, "Failed to setUserControlDisabledPackages", e);
                        return FAILURE;
                    }

                    return SUCCESS;
                }, MoreExecutors.directExecutor());
    }
}
