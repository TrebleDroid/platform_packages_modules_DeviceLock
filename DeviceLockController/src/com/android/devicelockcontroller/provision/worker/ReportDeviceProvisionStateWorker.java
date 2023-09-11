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
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * A worker class dedicated to report state of provision for the device lock program.
 */
public final class ReportDeviceProvisionStateWorker extends AbstractCheckInWorker {
    public static final String KEY_IS_PROVISION_SUCCESSFUL = "is-provision-successful";
    public static final String KEY_PROVISION_FAILURE_REASON = "provision-failure-reason";
    public static final String REPORT_PROVISION_STATE_WORK_NAME = "report-provision-state";

    /** Report provision failure and get next failed step */
    public static void reportSetupFailed(WorkManager workManager) {
        Data inputData = new Data.Builder()
                .putBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)
                .build();
        enqueueReportWork(inputData, workManager);
    }

    /** Report provision success */
    public static void reportSetupCompleted(WorkManager workManager) {
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
        this(context, workerParams, /* client= */ null,
                executorService);
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
        DeviceLockControllerSchedulerProvider schedulerProvider =
                (DeviceLockControllerSchedulerProvider) mContext;
        DeviceLockControllerScheduler scheduler =
                schedulerProvider.getDeviceLockControllerScheduler();
        return Futures.whenAllSucceed(mClient, lastState).call(() -> {
            boolean isSuccessful = getInputData().getBoolean(
                    KEY_IS_PROVISION_SUCCESSFUL, /* defaultValue= */ false);
            int failureReason = getInputData().getInt(KEY_PROVISION_FAILURE_REASON,
                    ProvisionFailureReason.UNKNOWN_REASON);
            ReportDeviceProvisionStateGrpcResponse response =
                    Futures.getDone(mClient).reportDeviceProvisionState(
                            Futures.getDone(lastState),
                            isSuccessful,
                            failureReason);
            if (response.hasRecoverableError()) return Result.retry();
            if (response.hasFatalError()) return Result.failure();
            int daysLeftUntilReset = response.getDaysLeftUntilReset();
            if (daysLeftUntilReset > 0) {
                UserParameters.setDaysLeftUntilReset(mContext, daysLeftUntilReset);
            }
            Futures.getUnchecked(globalParametersClient.setLastReceivedProvisionState(
                    response.getNextClientProvisionState()));
            scheduler.scheduleNextProvisionFailedStepAlarm();
            return Result.success();
        }, mExecutorService);
    }

}
