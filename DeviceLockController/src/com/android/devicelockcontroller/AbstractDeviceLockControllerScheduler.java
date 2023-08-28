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

import java.time.Duration;

/** The abstraction of {@link DeviceLockControllerScheduler} */
public abstract class AbstractDeviceLockControllerScheduler {

    /**
     * Notify the scheduler that system time has changed.
     */
    public abstract void notifyTimeChanged();

    /**
     * Notify the scheduler that reschedule might be required for check-in work
     */
    public abstract void notifyNeedRescheduleCheckIn();

    /**
     * Schedule an alarm to resume the provision flow.
     */
    public abstract void scheduleResumeProvisionAlarm();

    /**
     * Notify the scheduler that device reboot when provision is paused.
     */
    public abstract void notifyRebootWhenProvisionPaused();

    /**
     * Schedule the initial check-in work when device first boot.
     */
    public abstract void scheduleInitialCheckInWork();

    /**
     * Schedule the retry check-in work with a delay.
     *
     * @param delay The delayed duration to wait for performing retry check-in work.
     */
    public abstract void scheduleRetryCheckInWork(Duration delay);

    /**
     * Schedule an alarm to perform next provision failed step with the default delay.
     */
    public abstract void scheduleNextProvisionFailedStepAlarm();

    /**
     * Notify the scheduler that device reboot when provision has failed.
     */
    public abstract void notifyRebootWhenProvisionFailed();

    /**
     * Schedule an alarm to factory reset the device in case of provision is failed.
     */
    public abstract void scheduleResetDeviceAlarm();

    /**
     * Schedule an alarm to factory reset the device in case of mandatory provision is failed.
     */
    public abstract void scheduleMandatoryResetDeviceAlarm();
}
