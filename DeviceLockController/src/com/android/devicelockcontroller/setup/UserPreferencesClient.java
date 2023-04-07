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
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> isLockTaskModeActive() {
        return call(() -> IUserPreferencesService.Stub.asInterface(mDlcService)
                        .isLockTaskModeActive());
    }

    /**
     * Sets the current lock task mode state.
     *
     * @param isActive New state.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setLockTaskModeActive(boolean isActive) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService)
                    .setLockTaskModeActive(isActive);
            return null;
        });
    }

    /**
     * Gets the current device state.
     *
     * @return the current device state.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Integer> getDeviceState() {
        return call(() -> IUserPreferencesService.Stub.asInterface(
                        mDlcService).getDeviceState());
    }

    /**
     * Sets the current device state.
     *
     * @param state New state.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setDeviceState(@DeviceState int state) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService).setDeviceState(state);
            return null;
        });
    }

    /**
     * Gets the name of the package overriding home.
     *
     * @return Package overriding home.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getPackageOverridingHome() {
        return call(() -> IUserPreferencesService.Stub.asInterface(mDlcService)
                        .getPackageOverridingHome());
    }

    /**
     * Sets the name of the package overriding home.
     *
     * @param packageName Package overriding home.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setPackageOverridingHome(@Nullable String packageName) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService)
                    .setPackageOverridingHome(packageName);
            return null;
        });
    }

    /**
     * Gets the list of packages allowlisted in lock task mode.
     *
     * @return List of packages that are allowed in lock task mode.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<List<String>> getLockTaskAllowlist() {
        return call(() -> IUserPreferencesService.Stub.asInterface(mDlcService)
                        .getLockTaskAllowlist());
    }

    /**
     * Sets the list of packages allowlisted in lock task mode.
     *
     * @param allowlist List of packages that are allowed in lock task mode.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setLockTaskAllowlist(List<String> allowlist) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService)
                    .setLockTaskAllowlist(allowlist);
            return null;
        });
    }

    /**
     * Checks if a check-in request needs to be performed.
     *
     * @return true if check-in request needs to be performed.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> needCheckIn() {
        return call(() -> IUserPreferencesService.Stub.asInterface(mDlcService)
                        .needCheckIn());
    }

    /**
     * Sets the value of whether this device needs to perform check-in request.
     *
     * @param needCheckIn new state of whether the device needs to perform check-in request.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setNeedCheckIn(boolean needCheckIn) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService)
                    .setNeedCheckIn(needCheckIn);
            return null;
        });
    }

    /**
     * Gets the unique identifier that is regisered to DeviceLock backend server.
     *
     * @return The registered device unique identifier; null if device has never checked in with
     * backed server.
     */
    @Nullable
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getRegisteredDeviceId() {
        return call(() -> IUserPreferencesService.Stub.asInterface(mDlcService)
                        .getRegisteredDeviceId());
    }

    /**
     * Set the unique identifier that is registered to DeviceLock backend server.
     *
     * @param registeredDeviceId The registered device unique identifier.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setRegisteredDeviceId(String registeredDeviceId) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService)
                    .setRegisteredDeviceId(registeredDeviceId);
            return null;
        });
    }

    /**
     * Check if provision should be forced.
     *
     * @return True if the provision should be forced without any delays.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> isProvisionForced() {
        return call(() -> IUserPreferencesService.Stub.asInterface(mDlcService)
                        .isProvisionForced());
    }

    /**
     * Set provision is forced
     *
     * @param isForced The new value of the forced provision flag.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setProvisionForced(boolean isForced) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService)
                    .setProvisionForced(isForced);
            return null;
        });
    }

    /**
     * Get the enrollment token assigned by the Device Lock backend server.
     *
     * @return A string value of the enrollment token.
     */
    @Nullable
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getEnrollmentToken() {
        return call(() -> IUserPreferencesService.Stub.asInterface(mDlcService)
                        .getEnrollmentToken());
    }

    /**
     * Set the enrollment token assigned by the Device Lock backend server.
     *
     * @param token The string value of the enrollment token.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setEnrollmentToken(String token) {
        return call(() -> {
            IUserPreferencesService.Stub.asInterface(mDlcService)
                    .setEnrollmentToken(token);
            return null;
        });
    }
}
