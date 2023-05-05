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

package com.android.devicelockcontroller.provision.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionCompleteGrpcResponse;
import com.android.devicelockcontroller.storage.GlobalParameters;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * A worker class dedicated to report completion of provisioning for the device lock program.
 */
public final class ReportDeviceProvisioningCompleteWorker extends AbstractCheckInWorker {


    public ReportDeviceProvisioningCompleteWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        final ReportDeviceProvisionCompleteGrpcResponse response =
                mClient.reportDeviceProvisioningComplete();
        if (response.isSuccessful()) {
            GlobalParameters.setEnrollmentToken(mContext, response.getEnrollmentToken());
            Result.success();
        }
        LogUtil.w(TAG,
                "Report device provisioning complete failed: " + response);
        return Result.failure();
    }
}
