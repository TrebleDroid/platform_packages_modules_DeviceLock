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

import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__GET_DEVICE_CHECK_IN_STATUS;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__PAUSE_DEVICE_PROVISIONING;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISIONING_COMPLETE;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISION_STATE;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_KIOSK_APP_REQUEST_REPORTED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import org.junit.Rule;
import org.junit.Test;

import com.android.devicelockcontroller.DevicelockStatsLog;

public final class StatsLoggerImplTest {
    private static final int UID = 123;
    private final StatsLogger mStatsLogger = new StatsLoggerImpl();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(DevicelockStatsLog.class).build();

    @Test
    public void logGetDeviceCheckInStatus_shouldWriteCorrectLog() {
        mStatsLogger.logGetDeviceCheckInStatus();
        verify(() -> DevicelockStatsLog.write(
                DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__GET_DEVICE_CHECK_IN_STATUS));
    }

    @Test
    public void logPauseDeviceProvisioning_shouldWriteCorrectLog() {
        mStatsLogger.logPauseDeviceProvisioning();
        verify(() -> DevicelockStatsLog.write(
                DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__PAUSE_DEVICE_PROVISIONING));
    }

    @Test
    public void logReportDeviceProvisionState_shouldWriteCorrectLog() {
        mStatsLogger.logReportDeviceProvisionState();
        verify(() -> DevicelockStatsLog.write(
                DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISION_STATE));
    }

    @Test
    public void logKioskAppRequest_shouldWriteCorrectLog() {
        mStatsLogger.logKioskAppRequest(UID);
        verify(() -> DevicelockStatsLog.write(DEVICE_LOCK_KIOSK_APP_REQUEST_REPORTED, UID));
    }
}