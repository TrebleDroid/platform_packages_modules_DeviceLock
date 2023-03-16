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

package com.android.devicelockcontroller.provision.grpc.impl;

import android.util.ArraySet;

import androidx.annotation.Keep;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants;
import com.android.devicelockcontroller.proto.ClientDeviceIdentifier;
import com.android.devicelockcontroller.proto.DeviceIdentifierType;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceBlockingStub;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusRequest;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningReason;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningRequest;
import com.android.devicelockcontroller.proto.ReportDeviceProvisionCompleteRequest;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionCompleteGrpcResponse;

import javax.annotation.Nullable;

import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;

/**
 * A client for the {@link  com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc}
 * service.
 */
@Keep
public final class DeviceCheckInClientImpl extends DeviceCheckInClient {
    private final DeviceLockCheckinServiceBlockingStub mBlockingStub;

    public DeviceCheckInClientImpl(String hostName, int portNumber, @Nullable String registeredId) {
        super(registeredId);
        mBlockingStub = DeviceLockCheckinServiceGrpc.newBlockingStub(
                OkHttpChannelBuilder
                        .forAddress(hostName, portNumber)
                        .build());
    }

    @Override
    public GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(
            ArraySet<DeviceId> deviceIds, String carrierInfo,
            @Nullable String fcmRegistrationToken) {
        try {
            final GetDeviceCheckInStatusGrpcResponse response =
                    new GetDeviceCheckInStatusGrpcResponseWrapper(
                            mBlockingStub.getDeviceCheckinStatus(
                                    createGetDeviceCheckinStatusRequest(deviceIds, carrierInfo)));
            return response;
        } catch (StatusRuntimeException e) {
            return new GetDeviceCheckInStatusGrpcResponseWrapper(e.getStatus());
        }
    }

    @Override
    public PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(int reason) {
        try {
            return new PauseDeviceProvisioningGrpcResponseWrapper(
                    mBlockingStub.pauseDeviceProvisioning(
                            createPauseDeviceProvisioningRequest(mRegisteredId, reason)));

        } catch (StatusRuntimeException e) {
            return new PauseDeviceProvisioningGrpcResponseWrapper(e.getStatus());
        }
    }

    @Override
    public ReportDeviceProvisionCompleteGrpcResponse reportDeviceProvisioningComplete() {
        try {
            return new ReportDeviceProvisionCompleteGrpcResponseWrapper(
                    mBlockingStub.reportDeviceProvisionComplete(
                            createReportDeviceProvisioningCompleteRequest(mRegisteredId)));

        } catch (StatusRuntimeException e) {
            return new ReportDeviceProvisionCompleteGrpcResponseWrapper(e.getStatus());
        }
    }

    private GetDeviceCheckinStatusRequest createGetDeviceCheckinStatusRequest(
            ArraySet<DeviceId> deviceIds, String carrierInfo) {
        GetDeviceCheckinStatusRequest.Builder builder = GetDeviceCheckinStatusRequest.newBuilder();
        for (DeviceId deviceId : deviceIds) {
            builder.addClientDeviceIdentifiers(
                    ClientDeviceIdentifier.newBuilder()
                            .setDeviceIdentifierType(
                                    // TODO: b/270392813
                                    DeviceIdentifierType.forNumber(deviceId.getType() + 1))
                            .setDeviceIdentifier(deviceId.getId()));
        }
        builder.setCarrierMccmnc(carrierInfo);
        return builder.build();
    }

    private PauseDeviceProvisioningRequest createPauseDeviceProvisioningRequest(String registeredId,
            @DeviceLockConstants.PauseDeviceProvisioningReason int reason) {
        return PauseDeviceProvisioningRequest.newBuilder()
                .setRegisteredDeviceIdentifier(registeredId)
                .setPauseDeviceProvisioningReason(
                        PauseDeviceProvisioningReason.forNumber(reason))
                .build();
    }

    private ReportDeviceProvisionCompleteRequest createReportDeviceProvisioningCompleteRequest(
            String registeredId) {
        return ReportDeviceProvisionCompleteRequest.newBuilder()
                .setRegisteredDeviceIdentifier(registeredId)
                .build();
    }
}
