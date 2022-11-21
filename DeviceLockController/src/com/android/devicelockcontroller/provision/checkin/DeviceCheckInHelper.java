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

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

/**
 * Helper class to perform the device check in process with device lock backend server
 */
public final class DeviceCheckInHelper {
    private final Context mContext;

    public DeviceCheckInHelper(Context context) {
        mContext = context;
    }

    /**
     * Get the check-in status of this device for device lock program.
     *
     * @param isExpedited if true, the work request should be expedited;
     * @param deviceIds   A list of device unique identifiers.
     */
    public void enqueueDeviceCheckInWork(boolean isExpedited,
            ArraySet<Pair<Integer, String>> deviceIds) {
        final WorkManager workManager = WorkManager.getInstance(mContext);
        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();

        final OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(
                DeviceCheckInWorker.class)
                .setInputData(new Data.Builder().put(DeviceCheckInWorker.KEY_DEVICE_IDS,
                        deviceIds).build())
                .setConstraints(constraints);
        if (!isExpedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        workManager.enqueue(builder.build());
    }
}
