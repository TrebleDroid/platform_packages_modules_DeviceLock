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
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.setup.UserPreferences;
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
        final String className = context.getResources().getString(
                R.string.device_check_in_client_class_name);
        mClient = DeviceCheckInClient.newInstance(className, hostName, portNumber,
                UserPreferences.getRegisteredDeviceId(context));
        mCheckInHelper = new DeviceCheckInHelper(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        LogUtil.i(TAG, "perform check-in request");
        final ArraySet<DeviceId> deviceIds = mCheckInHelper.getDeviceUniqueIds();
        final String carrierInfo = mCheckInHelper.getCarrierInfo();
        if (deviceIds.isEmpty() || carrierInfo.isEmpty()) {
            LogUtil.w(TAG, "CheckIn failed. Required device information not available");
            return Result.failure();
        }
        final GetDeviceCheckInStatusGrpcResponse response =
                mClient.getDeviceCheckInStatus(deviceIds, carrierInfo,
                        /* fcmRegistrationToken= */ null);
        if (response.isSuccessful()) {
            return mCheckInHelper.handleGetDeviceCheckInStatusResponse(response)
                    ? Result.success()
                    : Result.retry();
        }
        LogUtil.w(TAG, "CheckIn failed: " + response);
        return Result.failure();
    }
}
