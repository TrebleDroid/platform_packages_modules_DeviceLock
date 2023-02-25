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

import com.android.devicelockcontroller.R;
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

    // TODO: Temporary address for testing purpose. Replace with the real server address when it is
    //  available.
    private final String mHostName;
    private final int mPortNumber;
    private final DeviceCheckInHelper mCheckInHelper;

    public DeviceCheckInWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mCheckInHelper = new DeviceCheckInHelper(context);
        mHostName = context.getResources().getString(R.string.check_in_server_host_name);
        mPortNumber = context.getResources().getInteger(R.integer.check_in_server_port_number);
    }

    @NonNull
    @Override
    public Result doWork() {
        LogUtil.i(TAG, "perform check-in request");
        final ArraySet<Pair<Integer, String>> deviceIds = mCheckInHelper.getDeviceUniqueIds();
        final String carrierInfo = mCheckInHelper.getCarrierInfo();
        if (deviceIds.isEmpty() || carrierInfo.isEmpty()) {
            LogUtil.w(TAG, "CheckIn failed. Required device information not available");
            return Result.failure();
        }
        final DeviceCheckInClient client =
                new DeviceCheckInClient(
                        DeviceLockCheckinServiceGrpc.newBlockingStub(
                                OkHttpChannelBuilder
                                        .forAddress(mHostName, mPortNumber)
                                        .build()));
        GetDeviceCheckInStatusResponseWrapper response =
                client.getDeviceCheckInStatus(
                        createGetDeviceCheckinStatusRequest(deviceIds, carrierInfo));
        if (response.isSuccessful()) {
            return mCheckInHelper.handleGetDeviceCheckInStatusResponse(response)
                    ? Result.success()
                    : Result.retry();
        }
        return Result.failure();
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
