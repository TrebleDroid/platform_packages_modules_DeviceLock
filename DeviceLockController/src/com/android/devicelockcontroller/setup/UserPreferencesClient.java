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

package com.android.devicelockcontroller.setup;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * A class used to access User Preferences from a secondary user.
 */
public final class UserPreferencesClient extends DlcClient {
    @SuppressLint("StaticFieldLeak") // Only holds application context.
    private static UserPreferencesClient sUserPreferencesClient;

    private UserPreferencesClient(@NonNull Context context,
            @NonNull ComponentName componentName) {
        super(context, componentName);
    }

    private UserPreferencesClient(@NonNull Context context) {
        this(context, new ComponentName(context, UserPreferencesService.class));
    }

    /**
     * Get the UserPreferencesClient singleton instance.
     */
    @MainThread
    public static UserPreferencesClient getInstance() {
        if (sUserPreferencesClient == null) {
            final Context applicationContext = DeviceLockControllerApplication.getAppContext();
            sUserPreferencesClient = new UserPreferencesClient(applicationContext);
        }

        return sUserPreferencesClient;
    }

    /**
     * Checks if lock task mode is active.
     *
     * @return True if lock task mode is active.
     */
    public ListenableFuture<Boolean> isLockTaskModeActive() {
        return call(new Callable<Boolean>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Boolean call() throws Exception {
                return IUserPreferencesService.Stub.asInterface(mDlcService)
                        .isLockTaskModeActive();
            }
        });
    }

    /**
     * Sets the current lock task mode state.
     *
     * @param isActive New state.
     */
    public ListenableFuture<Void> setLockTaskModeActive(boolean isActive) {
        return call(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Void call() throws Exception {
                IUserPreferencesService.Stub.asInterface(mDlcService)
                        .setLockTaskModeActive(isActive);
                return null;
            }
        });
    }

    /**
     * Gets the current device state.
     *
     * @return the current device state.
     */
    public ListenableFuture<Integer> getDeviceState() {
        return call(new Callable<Integer>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Integer call() throws Exception {
                return IUserPreferencesService.Stub.asInterface(mDlcService).getDeviceState();
            }
        });
    }

    /**
     * Sets the current device state.
     *
     * @param state   New state.
     */
    public ListenableFuture<Void> setDeviceState(@DeviceState int state) {
        return call(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Void call() throws Exception {
                IUserPreferencesService.Stub.asInterface(mDlcService).setDeviceState(state);
                return null;
            }
        });
    }

    /**
     * Gets the name of the package overriding home.
     *
     * @return Package overriding home.
     */
    public ListenableFuture<String> getPackageOverridingHome() {
        return call(new Callable<String>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public String call() throws Exception {
                return IUserPreferencesService.Stub.asInterface(mDlcService)
                        .getPackageOverridingHome();
            }
        });
    }

    /**
     * Sets the name of the package overriding home.
     *
     * @param packageName Package overriding home.
     */
    public ListenableFuture<Void> setPackageOverridingHome(@Nullable String packageName) {
        return call(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Void call() throws Exception {
                IUserPreferencesService.Stub.asInterface(mDlcService)
                        .setPackageOverridingHome(packageName);
                return null;
            }
        });
    }

    /**
     * Gets the list of packages allowlisted in lock task mode.
     *
     * @return List of packages that are allowed in lock task mode.
     */
    public ListenableFuture<List<String>> getLockTaskAllowlist() {
        return call(new Callable<List<String>>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public List<String> call() throws Exception {
                return IUserPreferencesService.Stub.asInterface(mDlcService)
                        .getLockTaskAllowlist();
            }
        });
    }

    /**
     * Sets the list of packages allowlisted in lock task mode.
     *
     * @param allowlist List of packages that are allowed in lock task mode.
     */
    public ListenableFuture<Void> setLockTaskAllowlist(List<String> allowlist) {
        return call(new Callable<Void>() {
            @Override
            @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
            public Void call() throws Exception {
                IUserPreferencesService.Stub.asInterface(mDlcService)
                        .setLockTaskAllowlist(allowlist);
                return null;
            }
        });
    }
}
