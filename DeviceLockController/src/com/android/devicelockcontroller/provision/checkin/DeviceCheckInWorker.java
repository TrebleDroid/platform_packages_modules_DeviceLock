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

import com.android.devicelockcontroller.util.LogUtil;

/**
 * A worker class dedicated to execute the check-in operation for device lock program.
 */
public final class DeviceCheckInWorker extends Worker {

    private static final String TAG = "DeviceCheckInWorker";

    public DeviceCheckInWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        LogUtil.i(TAG, "perform check-in request");
        final ArraySet<Pair<Integer, String>> deviceIds =
                new DeviceCheckInHelperImpl().getDeviceUniqueIds();
        if (deviceIds.isEmpty()) {
            LogUtil.e("DeviceCheckInWorker#doWork", "CheckIn failed. Device Id not available");
            return Result.failure();
        }
        //TODO(b/258711334): Implement device check-in gRPC request.
        LogUtil.d(TAG, "checkin succeed");
        return Result.success();
    }
}
