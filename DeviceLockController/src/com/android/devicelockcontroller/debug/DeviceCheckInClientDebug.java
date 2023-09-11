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

package com.android.devicelockcontroller.debug;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.READY_FOR_PROVISION;

import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.ArraySet;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceCheckInStatus;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.IsDeviceInApprovedCountryGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ProvisioningConfiguration;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * An implementation of the {@link DeviceCheckInClient} which simulate server responses by
 * reading it from {@link  SystemProperties}.
 */
@Keep
public final class DeviceCheckInClientDebug extends DeviceCheckInClient {

    public static final String TAG = "DeviceCheckInClientDebug";

    /**
     * Check In with DeviceLock backend server and get the next step for the device.
     */
    @Override
    public GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(ArraySet<DeviceId> deviceIds,
            String carrierInfo, @Nullable String fcmRegistrationToken) {
        return new GetDeviceCheckInStatusGrpcResponse() {
            @Override
            @DeviceCheckInStatus
            public int getDeviceCheckInStatus() {
                return DebugLogUtil.logAndReturn(TAG,
                        SystemProperties.getInt("debug.devicelock.checkin.status",
                                READY_FOR_PROVISION));
            }

            @Nullable
            @Override
            public String getRegisteredDeviceIdentifier() {
                return DebugLogUtil.logAndReturn(TAG,
                        deviceIds.size() > 0 ? deviceIds.valueAt(0).getId() : null);
            }

            @Nullable
            @Override
            public Instant getNextCheckInTime() {
                Duration delay = Duration.ofMinutes(
                        SystemProperties.getInt(
                                "debug.devicelock.checkin.retry-delay", /* def= */ 1));
                return DebugLogUtil.logAndReturn(TAG,
                        SystemClock.currentNetworkTimeClock().instant().plus(delay));
            }

            @Nullable
            @Override
            public ProvisioningConfiguration getProvisioningConfig() {
                // This should be overridden using SetupParametersOverrider.
                return new ProvisioningConfiguration(
                        /* kioskAppProviderName= */ "",
                        /* kioskAppPackageName= */ "",
                        /* kioskAppAllowlistPackages= */ List.of(""),
                        /* kioskAppEnableOutgoingCalls= */ false,
                        /* kioskAppEnableEnableNotifications= */ false,
                        /* disallowInstallingFromUnknownSources= */ false,
                        /* termsAndConditionsUrl= */ "",
                        /* supportUrl= */ "");
            }

            @Override
            public @ProvisioningType int getProvisioningType() {
                // This should be overridden using SetupParametersOverrider.
                return ProvisioningType.TYPE_UNDEFINED;
            }

            @Override
            public boolean isProvisioningMandatory() {
                // This should be overridden using SetupParametersOverrider.
                return false;
            }

            @Override
            public boolean isProvisionForced() {
                return DebugLogUtil.logAndReturn(TAG, SystemProperties.getBoolean(
                        "debug.devicelock.checkin.force-provisioning",
                        false));
            }

            @Override
            public boolean isDeviceInApprovedCountry() {
                return DebugLogUtil.logAndReturn(TAG, SystemProperties.getBoolean(
                        "debug.devicelock.checkin.approved-country",
                        true));
            }
        };
    }

    /**
     * Check if the device is in an approved country for the device lock program.
     */
    @Override
    public IsDeviceInApprovedCountryGrpcResponse isDeviceInApprovedCountry(
            @Nullable String carrierInfo) {
        return new IsDeviceInApprovedCountryGrpcResponse() {
            @Override
            public boolean isDeviceInApprovedCountry() {
                return DebugLogUtil.logAndReturn(TAG, SystemProperties.getBoolean(
                        "debug.devicelock.checkin.approved-country",
                        true));
            }
        };
    }

    /**
     * Inform the server that device provisioning has been paused for a certain amount of time.
     */
    @Override
    public PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(int reason) {
        return new PauseDeviceProvisioningGrpcResponse();
    }

    /**
     * Reports the current provision state of the device.
     */
    @Override
    public ReportDeviceProvisionStateGrpcResponse reportDeviceProvisionState(
            int lastReceivedProvisionState, boolean isSuccessful,
            @ProvisionFailureReason int reason) {
        return new ReportDeviceProvisionStateGrpcResponse() {
            @Override
            @DeviceProvisionState
            public int getNextClientProvisionState() {
                return DebugLogUtil.logAndReturn(TAG, SystemProperties.getInt(
                        "debug.devicelock.checkin.next-provision-state",
                        PROVISION_STATE_UNSPECIFIED));
            }

            @Override
            public int getDaysLeftUntilReset() {
                return DebugLogUtil.logAndReturn(TAG,
                        SystemProperties.getInt(
                                "debug.devicelock.checkin.days-left", /* def= */ 1));
            }
        };
    }
}
