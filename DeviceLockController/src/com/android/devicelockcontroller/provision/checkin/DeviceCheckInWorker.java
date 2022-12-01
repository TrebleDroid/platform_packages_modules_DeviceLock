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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.util.LogUtil;

/**
 * A worker class dedicated to execute the check-in operation for device lock program.
 */
final class DeviceCheckInWorker extends Worker {
    static final String KEY_DEVICE_IDS = "device_ids";
    private final ArraySet<Pair<Integer, String>> mDeviceIds;

    DeviceCheckInWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mDeviceIds =
                (ArraySet<Pair<Integer, String>>)
                        workerParams.getInputData().getKeyValueMap().get(KEY_DEVICE_IDS);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (mDeviceIds == null || mDeviceIds.isEmpty()) {
            LogUtil.e("DeviceCheckInWorker", "CheckIn failed. Device Id is null or empty");
            return Result.failure();
        }
        //TODO(b/258711334): Implement device check-in gRPC request.
        return Result.success();
    }


}
