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
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusRequest;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusResponseWrapper;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * A worker class dedicated to execute the check-in operation for device lock program.
 */
public final class DeviceCheckInWorker extends Worker {

    private static final String TAG = "DeviceCheckInWorker";

    private final DeviceCheckInHelper mCheckInHelper;
    private final DeviceCheckInClient mClient;

    public DeviceCheckInWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final String hostName = context.getResources().getString(
                R.string.check_in_server_host_name);
        final int portNumber = context.getResources().getInteger(
                R.integer.check_in_server_port_number);
        mClient = new DeviceCheckInClient(hostName, portNumber);
        mCheckInHelper = new DeviceCheckInHelper(context);
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
        final GetDeviceCheckInStatusResponseWrapper response =
                mClient.getDeviceCheckInStatus(
                        createGetDeviceCheckinStatusRequest(deviceIds, carrierInfo));
        if (response.isSuccessful()) {
            return mCheckInHelper.handleGetDeviceCheckInStatusResponse(response)
                    ? Result.success()
                    : Result.retry();
        }
        LogUtil.w(TAG, "CheckIn failed: " + response);
        return Result.failure();
    }

    private GetDeviceCheckinStatusRequest createGetDeviceCheckinStatusRequest(
            ArraySet<Pair<Integer, String>> deviceIds, String carrierInfo) {
        GetDeviceCheckinStatusRequest.Builder builder = GetDeviceCheckinStatusRequest.newBuilder();
        for (Pair<Integer, String> deviceId : deviceIds) {
            builder.addClientDeviceIdentifiers(
                    ClientDeviceIdentifier.newBuilder()
                            .setDeviceIdentifierType(
                                    // TODO: b/270392813
                                    DeviceIdentifierType.forNumber(deviceId.first + 1))
                            .setDeviceIdentifier(deviceId.second));
        }
        builder.setCarrierMccmnc(carrierInfo);
        //TODO: add fcm registration token.
        return builder.build();
    }
}
