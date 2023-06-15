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
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public final class ResumeProvisioningWorker extends ListenableWorker {
    @VisibleForTesting
    public static final String RESUME_PROVISION_WORK =
            "resume-provision-work";

    static final String TAG = "ResumeProvisioningWorker";

    public ResumeProvisioningWorker(@NotNull Context context,
            @NotNull WorkerParameters workerParams,
            ListeningExecutorService listeningExecutorService) {
        super(context, workerParams);
    }


    public static void scheduleResumeProvisioningWorker(WorkManager workManager, Duration delay) {
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(ResumeProvisioningWorker.class)
                        .setInitialDelay(delay)
                        .build();
        LogUtil.i(TAG, String.format("Scheduling work with delay: %s", delay));
        workManager.enqueueUniqueWork(RESUME_PROVISION_WORK, ExistingWorkPolicy.KEEP, work);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        LogUtil.i(TAG, "StartWork");
        PolicyObjectsInterface policyObjects =
                (PolicyObjectsInterface) getApplicationContext();
        return Futures.transform(policyObjects.getStateController().setNextStateForEvent(
                DeviceStateController.DeviceEvent.SETUP_RESUME), (Void v) -> {
            int deviceState = policyObjects.getStateController().getState();
            LogUtil.i(TAG, String.format("DeviceState is: %s", deviceState));
            if (DeviceStateController.DeviceState.SETUP_IN_PROGRESS == deviceState) {
                return Result.success();
            }
            return Result.failure();
        }, MoreExecutors.directExecutor());
    }
}
