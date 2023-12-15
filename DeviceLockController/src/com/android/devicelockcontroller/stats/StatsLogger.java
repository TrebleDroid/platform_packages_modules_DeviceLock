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

package com.android.devicelockcontroller.stats;

import com.android.devicelockcontroller.DevicelockStatsLog;

/**
 * Utility class wrapping operations related to Statistics.
 *
 * Please refer to {@link DevicelockStatsLog} class and
 * stats/atoms/devicelock/devicelock_extension_atoms.proto for more information.
 */
public interface StatsLogger {
    /**
     * Logs the analytics event of successfully getting device check in status from the server.
     */
    void logGetDeviceCheckInStatus();

    /**
     * Logs the analytics event of successfully pausing the device provisioning.
     */
    void logPauseDeviceProvisioning();

    /**
     * Logs the analytics event of successfully reporting the device provisioning state to the
     * server.
     */
    void logReportDeviceProvisionState();

    /**
     * Logs the analytics event of receiving a result from the server of the
     * IsDeviceInApprovedCountry gRPC call.
     */
    void logIsDeviceInApprovedCountry();

    /**
     * Logs the analytics event of receiving a request from the Kisok app.
     *
     * @param uid The UID of the Kiosk app, which can be acquired from the PackageManager.
     */
    void logKioskAppRequest(int uid);

    /**
     * Logs the analytics event of starting the provisioning process, starting the Kiosk app, and
     * the time elapsed in between.
     */
    void logProvisioningComplete(long timeSpentInProvisioningMillis);

    /**
     * Logs the analytics event of resetting the device due to a failed provisioning.
     * @param isProvisioningMandatory True if the provision is mandatory, false otherwise.
     */
    void logDeviceReset(boolean isProvisioningMandatory);
}
