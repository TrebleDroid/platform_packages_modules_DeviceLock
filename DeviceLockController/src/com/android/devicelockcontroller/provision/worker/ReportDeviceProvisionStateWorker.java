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

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_FACTORY_RESET;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_RETRY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_SUCCESS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason.SETUP_FAILED;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.Futures;

/**
 * A worker class dedicated to report state of provision for the device lock program.
 */
public final class ReportDeviceProvisionStateWorker extends AbstractCheckInWorker {

    public static final String KEY_DEVICE_PROVISION_FAILURE_REASON =
            "device-provision-failure-reason";
    public static final String KEY_LAST_RECEIVED_STATE = "last-received-state";
    public static final String KEY_IS_PROVISION_SUCCESSFUL = "is-provision-successful";
    @VisibleForTesting
    static final String UNEXPECTED_PROVISION_STATE_ERROR_MESSAGE = "Unexpected provision state!";

    public ReportDeviceProvisionStateWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @VisibleForTesting
    ReportDeviceProvisionStateWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, DeviceCheckInClient client) {
        super(context, workerParams, client);
    }

    @NonNull
    @Override
    public Result doWork() {
        int reason = getInputData().getInt(KEY_DEVICE_PROVISION_FAILURE_REASON, SETUP_FAILED);
        int lastState = getInputData().getInt(KEY_LAST_RECEIVED_STATE, PROVISION_STATE_UNSPECIFIED);
        boolean isSuccessful = getInputData().getBoolean(
                KEY_IS_PROVISION_SUCCESSFUL, /* defaultValue= */ false);
        final ReportDeviceProvisionStateGrpcResponse response =
                Futures.getUnchecked(mClient).reportDeviceProvisionState(reason, lastState,
                        isSuccessful);
        if (!response.isSuccessful()) return Result.failure();
        String enrollmentToken = response.getEnrollmentToken();
        if (!TextUtils.isEmpty(enrollmentToken)) {
            Futures.getUnchecked(
                    GlobalParametersClient.getInstance().setEnrollmentToken(enrollmentToken));
        }
        // TODO(b/276392181): Handle next state properly
        int provisionState = response.getNextClientProvisionState();
        switch (provisionState) {
            case PROVISION_STATE_RETRY:
            case PROVISION_STATE_DISMISSIBLE_UI:
            case PROVISION_STATE_PERSISTENT_UI:
            case PROVISION_STATE_FACTORY_RESET:
            case PROVISION_STATE_SUCCESS:
            case PROVISION_STATE_UNSPECIFIED:
                return Result.success(
                        new Data.Builder().putInt(KEY_LAST_RECEIVED_STATE, provisionState).build());
            default:
                throw new IllegalStateException(UNEXPECTED_PROVISION_STATE_ERROR_MESSAGE);
        }
    }
}
