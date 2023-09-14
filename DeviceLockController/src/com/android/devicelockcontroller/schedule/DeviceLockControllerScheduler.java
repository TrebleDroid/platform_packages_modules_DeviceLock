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

package com.android.devicelockcontroller.schedule;

import java.time.Duration;

/**
 * An interface which provides APIs to notify the scheduler to schedule works/alarms based on event
 * happened.
 */
public interface DeviceLockControllerScheduler {

    /**
     * Notify the scheduler that system time has changed.
     */
    void notifyTimeChanged();

    /**
     * Notify the scheduler that reschedule might be required for check-in work
     */
    void notifyNeedRescheduleCheckIn();

    /**
     * Schedule an alarm to resume the provision flow.
     */
    void scheduleResumeProvisionAlarm();

    /**
     * Notify the scheduler that device reboot when provision is paused.
     */
    void notifyRebootWhenProvisionPaused();

    /**
     * Schedule the initial check-in work when device first boot.
     */
    void scheduleInitialCheckInWork();

    /**
     * Schedule the retry check-in work with a delay.
     *
     * @param delay The delayed duration to wait for performing retry check-in work.
     */
    void scheduleRetryCheckInWork(Duration delay);

    /**
     * Schedule an alarm to perform next provision failed step.
     *
     * @param shouldRunImmediately true, if the alarm should go off immediately; false, if the
     *                             alarm should go off after a default delay.
     */
    void scheduleNextProvisionFailedStepAlarm(boolean shouldRunImmediately);

    /**
     * Notify the scheduler that device reboot when provision has failed.
     */
    void notifyRebootWhenProvisionFailed();

    /**
     * Schedule an alarm to factory reset the device in case of provision is failed.
     */
    void scheduleResetDeviceAlarm();

    /**
     * Schedule an alarm to factory reset the device in case of mandatory provision is failed.
     */
    void scheduleMandatoryResetDeviceAlarm();
}
