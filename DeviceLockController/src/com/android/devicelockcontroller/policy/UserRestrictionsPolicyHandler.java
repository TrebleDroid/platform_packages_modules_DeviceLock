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
import android.os.Bundle;
import android.os.UserManager;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.setup.SetupParameters;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Enforces UserRestriction policies. */
final class UserRestrictionsPolicyHandler implements PolicyHandler {

    private static final String TAG = "UserRestrictionsPolicyHandler";

    private static final String[] RESTRICTIONS_ALL_BUILDS = {
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER, // TODO: this should not be needed
            UserManager.DISALLOW_CONFIG_DATE_TIME
    };

    private static final String[] RESTRICTIONS_RELEASE_BUILDS = {
            UserManager.DISALLOW_DEBUGGING_FEATURES
    };

    private final ArrayList<String> mAlwaysOnRestrictions = new ArrayList<>();
    private final ArrayList<String> mLockModeRestrictions = new ArrayList<>();

    private final ComponentName mComponentName;
    private final DevicePolicyManager mDpm;
    private final UserManager mUserManager;
    private final Context mContext;
    private final boolean mIsDebug;

    UserRestrictionsPolicyHandler(Context context, ComponentName adminComponent,
            DevicePolicyManager dpm, UserManager userManager, boolean isDebug) {
        mComponentName = adminComponent;
        mDpm = dpm;
        mUserManager = userManager;
        mContext = context;
        mIsDebug = isDebug;

        LogUtil.i(TAG, String.format(Locale.US, "Build type DEBUG = %s", isDebug));

        Collections.addAll(mAlwaysOnRestrictions, RESTRICTIONS_ALL_BUILDS);
        if (!isDebug) {
            Collections.addAll(mAlwaysOnRestrictions, RESTRICTIONS_RELEASE_BUILDS);
        }

        if (SetupParameters.getOutgoingCallsDisabled(context)) {
            mLockModeRestrictions.add(UserManager.DISALLOW_OUTGOING_CALLS);
        }
    }

    @Override
    @ResultType
    public int setPolicyForState(@DeviceState int state) {
        switch (state) {
            case DeviceState.UNPROVISIONED:
                break;
            case DeviceState.SETUP_IN_PROGRESS:
            case DeviceState.SETUP_SUCCEEDED:
            case DeviceState.SETUP_FAILED:
            case DeviceState.UNLOCKED:
            case DeviceState.KIOSK_SETUP:
                setupRestrictions(mAlwaysOnRestrictions, true);
                setupRestrictions(mLockModeRestrictions, false);
                break;
            case DeviceState.LOCKED:
                setupRestrictions(mAlwaysOnRestrictions, true);
                setupRestrictions(mLockModeRestrictions, true);
                break;
            case DeviceState.CLEARED:
                setupRestrictions(mAlwaysOnRestrictions, false);
                setupRestrictions(mLockModeRestrictions, false);
                break;
            default:
                LogUtil.e(TAG, String.format(Locale.US, "Unhandled state %d", state));
                return FAILURE;
        }

        LogUtil.v(TAG, String.format(Locale.US, "Restrictions set for %d", state));

        return SUCCESS;
    }

    @Override
    public boolean isCompliant(@DeviceState int state) {
        switch (state) {
            case DeviceState.UNPROVISIONED:
                break;
            case DeviceState.SETUP_IN_PROGRESS:
            case DeviceState.SETUP_SUCCEEDED:
            case DeviceState.SETUP_FAILED:
            case DeviceState.UNLOCKED:
            case DeviceState.KIOSK_SETUP:
                if (!checkRestrictions(mAlwaysOnRestrictions, true)
                        || !checkRestrictions(mLockModeRestrictions, false)) {
                    return false;
                }
                break;
            case DeviceState.LOCKED:
                if (!checkRestrictions(mAlwaysOnRestrictions, true)
                        || !checkRestrictions(mLockModeRestrictions, true)) {
                    return false;
                }
                break;
            case DeviceState.CLEARED:
                if (!checkRestrictions(mAlwaysOnRestrictions, false)
                        || !checkRestrictions(mLockModeRestrictions, false)) {
                    return false;
                }
                break;
            default:
                LogUtil.i(TAG, String.format(Locale.US, "Unhandled state %d", state));
                break;
        }

        return true;
    }

    @Override
    public void setSetupParametersValid() {
        if (SetupParameters.getOutgoingCallsDisabled(mContext)
                && !mLockModeRestrictions.contains(UserManager.DISALLOW_OUTGOING_CALLS)) {
            LogUtil.i(TAG, String.format(Locale.US, "add %s into lock task mode restrictions",
                        UserManager.DISALLOW_OUTGOING_CALLS));
            mLockModeRestrictions.add(UserManager.DISALLOW_OUTGOING_CALLS);
        }
    }

    private void setupRestrictions(List<String> restrictions, boolean enable) {
        Bundle userRestrictionBundle = mUserManager.getUserRestrictions();

        for (int i = 0, size = restrictions.size(); i < size; i++) {
            String restriction = restrictions.get(i);
            if (userRestrictionBundle.getBoolean(restriction, false) != enable) {
                if (enable) {
                    mDpm.addUserRestriction(mComponentName, restriction);
                    LogUtil.v(TAG, String.format(Locale.US, "enable %s restriction", restriction));
                } else {
                    mDpm.clearUserRestriction(mComponentName, restriction);
                    LogUtil.v(TAG, String.format(Locale.US, "clear %s restriction", restriction));
                }
            }
        }
        // clear the adb access restriction if we added it before
        if (!mIsDebug
                && enable
                && restrictions.contains(UserManager.DISALLOW_DEBUGGING_FEATURES)) {
            mDpm.clearUserRestriction(mComponentName, UserManager.DISALLOW_DEBUGGING_FEATURES);
            LogUtil.v(TAG, String.format(Locale.US, "clear %s restriction",
                    UserManager.DISALLOW_DEBUGGING_FEATURES));
        }
    }

    private boolean checkRestrictions(List<String> restrictions, boolean value) {
        Bundle userRestrictionBundle = mUserManager.getUserRestrictions();

        for (int i = 0, size = restrictions.size(); i < size; i++) {
            String restriction = restrictions.get(i);
            if (value != userRestrictionBundle.getBoolean(restriction, false)) {
                LogUtil.i(TAG, String.format(Locale.US, "%s restriction is not %b",
                        restriction, value));
                return false;
            }
        }

        return true;
    }
}
