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

package com.android.devicelockcontroller;

import android.annotation.CallbackExecutor;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Manager used to interact with the system device lock service from the Device Lock Controller.
 * Stopgap: these should have been SystemApis on DeviceLockManager.
 */
public interface SystemDeviceLockManager {
    /**
     * Add the FINANCED_DEVICE_KIOSK role to the specified package.
     *
     * @param packageName package for the financed device kiosk app.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback this returns either success or an exception.
     */
    void addFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Remove the FINANCED_DEVICE_KIOSK role from the specified package.
     *
     * @param packageName package for the financed device kiosk app.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback this returns either success or an exception.
     */
    void removeFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set the Device Lock Controller exempt from restrictions about starting activities
     * from the background (for the calling user).
     *
     * @param exempt true if the Controller should be exempt from the restriction.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    void setExemptFromActivityBackgroundStartRestriction(boolean exempt,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set the kiosk app exempt from hibernation (for the calling user).
     *
     * @param packageName kiosk app package name.
     * @param exempt true if the Controller should be exempt from hibernation.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    void setExemptFromHibernation(String packageName, boolean exempt,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Enable kiosk keepalive, making sure the kiosk app is restarted on crash.
     *
     * @param packageName kiosk app package name.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    void enableKioskKeepalive(String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Disable kiosk keepalive.
     *
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    void disableKioskKeepalive(@CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);

    /**
     * Set whether device is finalized so that system service knows when to keep the Device Lock
     * Controller enabled.
     *
     * @param finalized true if device is finalized and DLC should not be enabled.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param callback callback this returns either success or an exception.
     */
    void setDeviceFinalized(boolean finalized, @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback);
}
