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
import android.content.ComponentName;
import android.content.Context;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.setup.SetupParameters;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Enforces restrictions on Kiosk app. */
final class KioskAppPolicyHandler implements PolicyHandler {
    private static final String TAG = "KioskAppPolicyHandler";

    private final Context mContext;
    private final ComponentName mComponent;
    private final DevicePolicyManager mDpm;

    KioskAppPolicyHandler(Context context, ComponentName component, DevicePolicyManager dpm) {
        mContext = context;
        mComponent = component;
        mDpm = dpm;
    }

    @Override
    @ResultType
    public int setPolicyForState(@DeviceState int state) {
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
                return SUCCESS;
            default:
                LogUtil.e(TAG, String.format(Locale.US, "Invalid State %d", state));
                return FAILURE;
        }
    }

    @Override
    public boolean isCompliant(@DeviceState int state) {
        switch (state) {
            case DeviceState.UNLOCKED:
            case DeviceState.LOCKED:
            case DeviceState.KIOSK_SETUP:
                return isKioskPackageProtected();
            case DeviceState.CLEARED:
                return !isKioskPackageProtected();
            case DeviceState.UNPROVISIONED:
            case DeviceState.SETUP_IN_PROGRESS:
            case DeviceState.SETUP_SUCCEEDED:
            case DeviceState.SETUP_FAILED:
                return true;
            default:
                LogUtil.e(TAG, String.format(Locale.US, "Invalid State %d", state));
                return false;
        }
    }

    private boolean isKioskPackageProtected() {
        final String packageName = SetupParameters.getKioskPackage(mContext);
        if (packageName == null) {
            LogUtil.e(TAG, "Kiosk package is not set");
            return false;
        }

        try {
            if (!mDpm.isUninstallBlocked(mComponent, packageName)) {
                return false;
            }
        } catch (SecurityException e) {
            LogUtil.e(TAG, "Could not read device policy", e);
            return false;
        }

        final List<String> packages;
        try {
            packages = mDpm.getUserControlDisabledPackages(mComponent);
        } catch (SecurityException e) {
            LogUtil.e(TAG, "Could not read device policy");
            return false;
        }

        return packages != null && packages.contains(packageName);
    }

    @ResultType
    private int enableKioskPackageProtection(boolean enable) {
        final String packageName = SetupParameters.getKioskPackage(mContext);
        if (packageName == null) {
            LogUtil.e(TAG, "Kiosk package is not set");
            return FAILURE;
        }

        try {
            mDpm.setUninstallBlocked(mComponent, packageName, enable);
        } catch (SecurityException e) {
            LogUtil.e(TAG, "Unable to set device policy", e);
            return FAILURE;
        }

        final List<String> pkgList = new ArrayList<>();
        if (enable) {
            pkgList.add(packageName);
        }

        try {
            mDpm.setUserControlDisabledPackages(mComponent, pkgList);
        } catch (SecurityException e) {
            LogUtil.e(TAG, "Failed to setUserControlDisabledPackages", e);
            return FAILURE;
        }

        return SUCCESS;
    }
}
