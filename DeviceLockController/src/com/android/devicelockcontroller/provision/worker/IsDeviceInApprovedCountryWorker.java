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
import androidx.annotation.VisibleForTesting;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.IsDeviceInApprovedCountryGrpcResponse;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * A worker class dedicated to check whether device is in approved country.
 * Note that this worker always returns {@link androidx.work.ListenableWorker.Result.Success}
 * regardless of the success of the underlying rpc.
 *
 * Child workers or observers should check input/output data for a boolean value associated with
 * {@link KEY_IS_IN_APPROVED_COUNTRY}:
 * - If a true boolean value presents, then device is in approved country;
 * - If a false boolean value presents, then device is not in approved country and provision should
 * fail due to {@link ProvisionFailureReason#NOT_IN_ELIGIBLE_COUNTRY};
 * - If no boolean value presents, then device country info is unavailable and provision should fail
 * due to {@link ProvisionFailureReason#COUNTRY_INFO_UNAVAILABLE};
 */
public final class IsDeviceInApprovedCountryWorker extends
        AbstractCheckInWorker {

    public static final String KEY_CARRIER_INFO = "carrier-info";
    public static final String KEY_IS_IN_APPROVED_COUNTRY = "is-in-approved-country";

    public IsDeviceInApprovedCountryWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, ListeningExecutorService executorService) {
        super(context, workerParams, null, executorService);
    }

    @VisibleForTesting
    IsDeviceInApprovedCountryWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParameters,
            DeviceCheckInClient client, ListeningExecutorService executorService) {
        super(context, workerParameters, client, executorService);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return Futures.transform(mClient, client -> {
            String carrierInfo = getInputData().getString(KEY_CARRIER_INFO);
            IsDeviceInApprovedCountryGrpcResponse response =
                    client.isDeviceInApprovedCountry(carrierInfo);
            ((StatsLoggerProvider) mContext.getApplicationContext()).getStatsLogger()
                    .logIsDeviceInApprovedCountry();
            if (response.hasRecoverableError()) {
                return Result.retry();
            }
            Data.Builder builder = new Data.Builder();
            if (response.isSuccessful()) {
                return Result.success(builder.putBoolean(KEY_IS_IN_APPROVED_COUNTRY,
                        response.isDeviceInApprovedCountry()).build());
            }
            return Result.success();
        }, MoreExecutors.directExecutor());
    }
}
