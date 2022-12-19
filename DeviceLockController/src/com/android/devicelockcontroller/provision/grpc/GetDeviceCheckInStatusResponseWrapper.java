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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.proto.ClientCheckinStatus;
import com.android.devicelockcontroller.proto.DeviceProvisioningInformation;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusResponse;
import com.android.devicelockcontroller.proto.NextCheckinInformation;

import io.grpc.Status;

/**
 * Wrapper for response and status objects for a GetDeviceCheckinStatusResponse.
 */
public final class GetDeviceCheckInStatusResponseWrapper extends DeviceCheckInResponse {
    @Nullable
    private final GetDeviceCheckinStatusResponse mResponse;

    public GetDeviceCheckInStatusResponseWrapper(@NonNull Status status) {
        super(status);
        mResponse = null;
    }

    public GetDeviceCheckInStatusResponseWrapper(
            @NonNull GetDeviceCheckinStatusResponse response) {
        mResponse = response;
    }

    @Nullable
    public ClientCheckinStatus getDeviceCheckInStatus() {
        return mResponse != null ? mResponse.getClientCheckinStatus() : null;
    }

    @Nullable
    public NextStepInformation getNextStepInformation() {
        if (mResponse == null) return null;
        switch (mResponse.getNextStepsCase()) {
            case NEXT_CHECKIN_INFORMATION:
                return new NextStepInformation(mResponse.getNextCheckinInformation());
            case DEVICE_PROVISIONING_INFORMATION:
                return new NextStepInformation(mResponse.getDeviceProvisioningInformation());
            case NEXTSTEPS_NOT_SET:
                // fall through
            default:
                return null;
        }
    }

    private static final class NextStepInformation {

        //TODO: Consider to use a builder pattern to ensure at least one of the below fields is
        // not null value so that we can eliminate the need of two separate constructors.
        @Nullable
        private final NextCheckinInformation mNextCheckInInformation;
        @Nullable
        private final DeviceProvisioningInformation mDeviceProvisioningInformation;

        private NextStepInformation(@NonNull NextCheckinInformation information) {
            mNextCheckInInformation = information;
            mDeviceProvisioningInformation = null;
        }

        private NextStepInformation(@NonNull DeviceProvisioningInformation information) {
            mNextCheckInInformation = null;
            mDeviceProvisioningInformation = information;
        }

        private boolean isNextCheckInInformationAvailable() {
            return mNextCheckInInformation != null;
        }

        @Nullable
        private NextCheckinInformation getNextCheckInInformation() {
            return mNextCheckInInformation;
        }

        private boolean isDeviceProvisioningInformationAvailable() {
            return mDeviceProvisioningInformation != null;
        }

        @Nullable
        private DeviceProvisioningInformation getDeviceProvisioningInformation() {
            return mDeviceProvisioningInformation;
        }
    }
}
