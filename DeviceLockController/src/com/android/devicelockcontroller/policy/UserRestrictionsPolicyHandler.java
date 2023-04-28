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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.util.ArraySet;

import androidx.annotation.MainThread;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.setup.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.Locale;

/** Enforces UserRestriction policies. */
final class UserRestrictionsPolicyHandler implements PolicyHandler {

    private static final String TAG = "UserRestrictionsPolicyHandler";

    private static final String[] RESTRICTIONS_ALL_BUILDS = {
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_CONFIG_DATE_TIME
    };

    private static final String[] RESTRICTIONS_RELEASE_BUILDS = {
            UserManager.DISALLOW_DEBUGGING_FEATURES
    };

    private final ArraySet<String> mAlwaysOnRestrictions = new ArraySet<>();
    private ArraySet<String> mLockModeRestrictions;

    private final DevicePolicyManager mDpm;
    private final UserManager mUserManager;
    private final boolean mIsDebug;

    UserRestrictionsPolicyHandler(DevicePolicyManager dpm, UserManager userManager,
            boolean isDebug) {
        mDpm = dpm;
        mUserManager = userManager;
        mIsDebug = isDebug;

        LogUtil.i(TAG, String.format(Locale.US, "Build type DEBUG = %s", isDebug));

        Collections.addAll(mAlwaysOnRestrictions, RESTRICTIONS_ALL_BUILDS);
        if (!isDebug) {
            Collections.addAll(mAlwaysOnRestrictions, RESTRICTIONS_RELEASE_BUILDS);
        }
    }

    @Override
    @ResultType
    public ListenableFuture<@ResultType Integer> setPolicyForState(@DeviceState int state) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        switch (state) {
            case DeviceState.UNPROVISIONED:
            case DeviceState.PSEUDO_LOCKED:
            case DeviceState.PSEUDO_UNLOCKED:
                break;
            case DeviceState.SETUP_IN_PROGRESS:
            case DeviceState.SETUP_SUCCEEDED:
            case DeviceState.SETUP_FAILED:
            case DeviceState.UNLOCKED:
            case DeviceState.KIOSK_SETUP:
                setupRestrictions(mAlwaysOnRestrictions, true);
                return Futures.transform(retrieveLockModeRestrictions(),
                        restrictions -> setupRestrictions(restrictions, false), mainHandler::post);
            case DeviceState.LOCKED:
                setupRestrictions(mAlwaysOnRestrictions, true);
                return Futures.transform(retrieveLockModeRestrictions(),
                        restrictions -> setupRestrictions(restrictions, true), mainHandler::post);
            case DeviceState.CLEARED:
                setupRestrictions(mAlwaysOnRestrictions, false);
                return Futures.transform(retrieveLockModeRestrictions(),
                        restrictions -> setupRestrictions(restrictions, false), mainHandler::post);
            default:
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }

        LogUtil.v(TAG, String.format(Locale.US, "Restrictions set for %d", state));

        return Futures.immediateFuture(SUCCESS);
    }

    @Override
    public ListenableFuture<Boolean> isCompliant(@DeviceState int state) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        switch (state) {
            case DeviceState.UNPROVISIONED:
                break;
            case DeviceState.SETUP_IN_PROGRESS:
            case DeviceState.SETUP_SUCCEEDED:
            case DeviceState.SETUP_FAILED:
            case DeviceState.UNLOCKED:
            case DeviceState.KIOSK_SETUP:
                if (checkRestrictions(mAlwaysOnRestrictions, true)) {
                    return Futures.transform(retrieveLockModeRestrictions(),
                            restrictions -> checkRestrictions(restrictions, false),
                            mainHandler::post);
                }
                break;
            case DeviceState.LOCKED:
                if (checkRestrictions(mAlwaysOnRestrictions, true)) {
                    return Futures.transform(retrieveLockModeRestrictions(),
                            restrictions -> checkRestrictions(restrictions, true),
                            mainHandler::post);
                }
                break;
            case DeviceState.CLEARED:
                if (checkRestrictions(mAlwaysOnRestrictions, false)) {
                    return Futures.transform(retrieveLockModeRestrictions(),
                            restrictions -> checkRestrictions(restrictions, false),
                            mainHandler::post);
                }
                break;
            default:
                LogUtil.i(TAG, String.format(Locale.US, "Unhandled state %d", state));
                return Futures.immediateFailedFuture(
                        new IllegalStateException(String.valueOf(state)));
        }
        return Futures.immediateFuture(false);
    }

    @MainThread
    public ListenableFuture<ArraySet<String>> retrieveLockModeRestrictions() {
        if (mLockModeRestrictions != null) return Futures.immediateFuture(mLockModeRestrictions);
        final SetupParametersClient parameters = SetupParametersClient.getInstance();
        final ListenableFuture<String> kioskPackageTask = parameters.getKioskPackage();
        final ListenableFuture<Boolean> outgoingCallsDisabledTask =
                parameters.getOutgoingCallsDisabled();
        final ListenableFuture<Boolean> installingFromUnknownSourcesDisallowedTask =
                parameters.isInstallingFromUnknownSourcesDisallowed();
        return Futures.whenAllSucceed(kioskPackageTask,
                outgoingCallsDisabledTask,
                installingFromUnknownSourcesDisallowedTask).call(() -> {
                    if (Futures.getDone(kioskPackageTask) == null) {
                        throw new IllegalStateException("Setup parameters does not exist!");
                    }
                    if (mLockModeRestrictions == null) {
                        mLockModeRestrictions = new ArraySet<>(2);
                        if (Futures.getDone(outgoingCallsDisabledTask)) {
                            mLockModeRestrictions.add(UserManager.DISALLOW_OUTGOING_CALLS);
                        }
                        if (Futures.getDone(installingFromUnknownSourcesDisallowedTask)) {
                            mLockModeRestrictions.add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
                        }
                    }
                    return mLockModeRestrictions;
                }, new Handler(Looper.getMainLooper())::post);
    }

    @ResultType
    private int setupRestrictions(ArraySet<String> restrictions, boolean enable) {
        Bundle userRestrictionBundle = mUserManager.getUserRestrictions();

        for (int i = 0, size = restrictions.size(); i < size; i++) {
            String restriction = restrictions.valueAt(i);
            if (userRestrictionBundle.getBoolean(restriction, false) != enable) {
                if (enable) {
                    mDpm.addUserRestriction(null /* admin */, restriction);
                    LogUtil.v(TAG, String.format(Locale.US, "enable %s restriction", restriction));
                } else {
                    mDpm.clearUserRestriction(null /* admin */, restriction);
                    LogUtil.v(TAG, String.format(Locale.US, "clear %s restriction", restriction));
                }
            }
        }
        // clear the adb access restriction if we added it before
        if (!mIsDebug
                && enable
                && restrictions.contains(UserManager.DISALLOW_DEBUGGING_FEATURES)) {
            mDpm.clearUserRestriction(null /* admin */, UserManager.DISALLOW_DEBUGGING_FEATURES);
            LogUtil.v(TAG, String.format(Locale.US, "clear %s restriction",
                    UserManager.DISALLOW_DEBUGGING_FEATURES));
        }
        return SUCCESS;
    }

    private boolean checkRestrictions(ArraySet<String> restrictions, boolean value) {
        Bundle userRestrictionBundle = mUserManager.getUserRestrictions();

        for (int i = 0, size = restrictions.size(); i < size; i++) {
            String restriction = restrictions.valueAt(i);
            if (value != userRestrictionBundle.getBoolean(restriction, false)) {
                LogUtil.i(TAG, String.format(Locale.US, "%s restriction is not %b",
                        restriction, value));
                return false;
            }
        }

        return true;
    }
}
