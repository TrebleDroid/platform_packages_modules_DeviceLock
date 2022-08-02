/*
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.devicelock;

import android.devicelock.IGetKioskAppsCallback;
import android.devicelock.IGetDeviceIdCallback;
import android.devicelock.IIsDeviceLockedCallback;
import android.devicelock.ILockUnlockDeviceCallback;

/**
 * Binder interface to communicate with DeviceLockService.
 * {@hide}
 */
oneway interface IDeviceLockService {
    /**
     * Asynchronously lock the device.
     */
    void lockDevice(in ILockUnlockDeviceCallback callback);

    /**
     * Asynchronously unlock the device.
     */
    void unlockDevice(in ILockUnlockDeviceCallback callback);

    /**
     * Asynchronously retrieve the device lock status.
     */
    void isDeviceLocked(in IIsDeviceLockedCallback callback);

    /**
     * Asynchronously retrieve the device identifier.
     */
    void getDeviceId(in IGetDeviceIdCallback callback);

    /**
     * Constant corresponding to a financed device role.
     * Returned by {@link #getKioskApps}.
     */
    const int DEVICE_LOCK_ROLE_FINANCING = 0;

    /**
     * Asynchronously retrieve the Kiosk apps roles and package names.
     */
    void getKioskApps(in IGetKioskAppsCallback callback);
}
