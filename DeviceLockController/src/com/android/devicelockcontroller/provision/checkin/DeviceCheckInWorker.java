/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.devicelockcontroller.provision.checkin;

import android.content.Context;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.proto.ClientDeviceIdentifier;
import com.android.devicelockcontroller.proto.DeviceIdentifierType;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusRequest;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusResponseWrapper;
import com.android.devicelockcontroller.util.LogUtil;

import io.grpc.okhttp.OkHttpChannelBuilder;

/**
 * A worker class dedicated to execute the check-in operation for device lock program.
 */
public final class DeviceCheckInWorker extends Worker {

    private static final String TAG = "DeviceCheckInWorker";

    // TODO: Feed server address when it is available.
    private static final String CHECK_IN_SERVER_HOST = "";
    private static final int CHECK_IN_SERVER_PORT = -1;

    public DeviceCheckInWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        LogUtil.i(TAG, "perform check-in request");
        final DeviceCheckInHelperImpl checkInHelper = new DeviceCheckInHelperImpl();
        final ArraySet<Pair<Integer, String>> deviceIds = checkInHelper.getDeviceUniqueIds();
        final String carrierInfo = checkInHelper.getCarrierInfo();
        if (deviceIds.isEmpty() || carrierInfo.isEmpty()) {
            LogUtil.w(TAG, "CheckIn failed. Required device information not available");
            return Result.failure();
        }
        if (CHECK_IN_SERVER_HOST.isEmpty() || CHECK_IN_SERVER_PORT < 0) return Result.failure();
        final DeviceCheckInClient client =
                new DeviceCheckInClient(
                        DeviceLockCheckinServiceGrpc.newBlockingStub(
                                OkHttpChannelBuilder
                                        .forAddress(CHECK_IN_SERVER_HOST, CHECK_IN_SERVER_PORT)
                                        .build()));
        GetDeviceCheckInStatusResponseWrapper response =
                client.getDeviceCheckInStatus(
                        createGetDeviceCheckinStatusRequest(deviceIds, carrierInfo));
        LogUtil.d(TAG, "checkin succeed: " + response);
        return Result.success();
    }

    private GetDeviceCheckinStatusRequest createGetDeviceCheckinStatusRequest(
            ArraySet<Pair<Integer, String>> deviceIds, String carrierInfo) {
        GetDeviceCheckinStatusRequest.Builder builder = GetDeviceCheckinStatusRequest.newBuilder();
        for (Pair<Integer, String> deviceId : deviceIds) {
            builder.addClientDeviceIdentifiers(
                    ClientDeviceIdentifier.newBuilder()
                            .setDeviceIdentifierType(DeviceIdentifierType.forNumber(deviceId.first))
                            .setDeviceIdentifier(deviceId.second));
        }
        builder.setCarrierMccmnc(carrierInfo);
        //TODO: add fcm registration token.
        return builder.build();
    }
}
