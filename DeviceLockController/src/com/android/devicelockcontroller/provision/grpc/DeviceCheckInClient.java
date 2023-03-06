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

package com.android.devicelockcontroller.provision.grpc;

import android.util.ArraySet;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants.PauseDeviceProvisioningReason;

/**
 * An abstract class that's intended for implementation of class that manages communication with
 * DeviceLock backend server.
 */
public abstract class DeviceCheckInClient {
    @Nullable
    protected final String mRegisteredId;
    protected final String mHostName;
    protected final int mPortNumber;

    protected DeviceCheckInClient(String hostName, int portNumber, @Nullable String registeredId) {
        mRegisteredId = registeredId;
        mHostName = hostName;
        mPortNumber = portNumber;
    }

    /**
     * Get a new instance of DeviceCheckInClient object.
     */
    public static DeviceCheckInClient newInstance(String className, String hostName, int portNumber,
            @Nullable String registeredId) {
        try {
            Class<?> clazz = Class.forName(className);
            return (DeviceCheckInClient) clazz.getDeclaredConstructor(
                            String.class, Integer.TYPE, String.class)
                    .newInstance(hostName, portNumber, registeredId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get DeviceCheckInClient instance", e);
        }
    }

    /**
     * Check In with DeviceLock backend server and get the next step for the device
     *
     * @param deviceIds            A set of all device unique identifiers, this could include IMEIs,
     *                             MEIDs, etc.
     * @param carrierInfo          The information of the device's sim operator which is used to
     *                             determine the device's geological location and eventually
     *                             eligibility of the DeviceLock program.
     * @param fcmRegistrationToken The fcm registration token
     * @return A class that encapsulate the response from the backend server.
     */
    @WorkerThread
    public abstract GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(
            ArraySet<DeviceId> deviceIds, String carrierInfo,
            @Nullable String fcmRegistrationToken);

    /**
     * Inform the server that device provisioning has been paused for a certain amount of time.
     *
     * @param reason The reason that provisioning has been paused.
     * @return A class that encapsulate the response from the backend sever.
     */
    @WorkerThread
    public abstract PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(
            @PauseDeviceProvisioningReason int reason);

    /**
     * Inform the server that device provisioning has been completed.
     *
     * @return A class that encapsulate the response from the backend server.
     */
    @WorkerThread
    public abstract ReportDeviceProvisionCompleteGrpcResponse reportDeviceProvisioningComplete();
}
