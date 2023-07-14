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
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.AbstractDeviceLockControllerScheduler;
import com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason;
import com.android.devicelockcontroller.policy.SetupController;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * A worker class dedicated to report state of provision for the device lock program.
 */
public final class ReportDeviceProvisionStateWorker extends AbstractCheckInWorker {

    public static final String KEY_DEVICE_PROVISION_FAILURE_REASON =
            "device-provision-failure-reason";
    public static final String KEY_IS_PROVISION_SUCCESSFUL = "is-provision-successful";
    public static final String REPORT_PROVISION_STATE_WORK_NAME = "report-provision-state";

    /**
     * Get a {@link SetupController.SetupUpdatesCallbacks} which will enqueue this worker to report
     * provision success / failure.
     */
    @NonNull
    public static SetupController.SetupUpdatesCallbacks getSetupUpdatesCallbacks(
            AbstractDeviceLockControllerScheduler scheduler,
            WorkManager workManager) {
        return new SetupController.SetupUpdatesCallbacks() {
            @Override
            public void setupFailed(@SetupFailureReason int reason) {
                reportSetupFailed(reason, workManager);
                scheduler.scheduleNextProvisionFailedStepAlarm();
            }

            @Override
            public void setupCompleted() {
                reportSetupCompleted(workManager);
            }
        };
    }

    private static void reportSetupFailed(@SetupFailureReason int reason, WorkManager workManager) {
        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)
                .putInt(KEY_DEVICE_PROVISION_FAILURE_REASON, reason)
                .build();
        enqueueReportWork(inputData, workManager);
    }

    private static void reportSetupCompleted(WorkManager workManager) {
        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_PROVISION_SUCCESSFUL, true)
                .build();
        enqueueReportWork(inputData, workManager);
    }

    /**
     * Schedule a work to report the current provision failed step to server.
     */
    public static void reportCurrentFailedStep(WorkManager workManager) {
        enqueueReportWork(new Data.Builder().build(), workManager);
    }

    private static void enqueueReportWork(Data inputData, WorkManager workManager) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(ReportDeviceProvisionStateWorker.class)
                        .setConstraints(constraints)
                        .setInputData(inputData)
                        .build();
        workManager.enqueueUniqueWork(
                REPORT_PROVISION_STATE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE, work);
    }

    public ReportDeviceProvisionStateWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, ListeningExecutorService executorService) {
        super(context, workerParams, null, executorService);
    }

    @VisibleForTesting
    ReportDeviceProvisionStateWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams, DeviceCheckInClient client,
            ListeningExecutorService executorService) {
        super(context, workerParams, client, executorService);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        ListenableFuture<Integer> lastState =
                globalParametersClient.getLastReceivedProvisionState();
        return Futures.whenAllSucceed(mClient, lastState).call(() -> {
            boolean isSuccessful = getInputData().getBoolean(
                    KEY_IS_PROVISION_SUCCESSFUL, /* defaultValue= */ false);
            int reason = getInputData().getInt(KEY_DEVICE_PROVISION_FAILURE_REASON,
                    SetupFailureReason.SETUP_FAILED);
            ReportDeviceProvisionStateGrpcResponse response =
                    Futures.getDone(mClient).reportDeviceProvisionState(reason,
                            Futures.getDone(lastState),
                            isSuccessful);
            if (response.hasRecoverableError()) return Result.retry();
            if (response.hasFatalError()) return Result.failure();
            String enrollmentToken = response.getEnrollmentToken();
            if (!TextUtils.isEmpty(enrollmentToken)) {
                Futures.getUnchecked(globalParametersClient.setEnrollmentToken(enrollmentToken));
            }
            int daysLeftUntilReset = response.getDaysLeftUntilReset();
            if (daysLeftUntilReset > 0) {
                Futures.getUnchecked(
                        globalParametersClient.setDaysLeftUntilReset(daysLeftUntilReset));
            }
            Futures.getUnchecked(globalParametersClient.setLastReceivedProvisionState(
                    response.getNextClientProvisionState()));
            return Result.success();
        }, mExecutorService);
    }

}
