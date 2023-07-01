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

package com.android.devicelockcontroller.policy;


import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent.PROVISION_RESUME;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PROVISION_IN_PROGRESS;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Duration;

/**
 * A worker dedicated to resume the provisioning flow after it has been paused.
 */
public final class ResumeProvisioningWorker extends ListenableWorker {
    @VisibleForTesting
    static final String RESUME_PROVISION_WORK = "resume-provision-work";

    private static final String TAG = "ResumeProvisioningWorker";
    private static final String PROVISION_PAUSED_MINUTES_SYS_PROPERTY_KEY =
            "debug.devicelock.paused-minutes";
    private static final int PROVISION_PAUSED_MINUTES_DEFAULT = 60;
    @VisibleForTesting
    static final int PROVISION_PAUSED_HOUR = 1;
    private final ListeningExecutorService mExecutorService;

    public ResumeProvisioningWorker(Context context, WorkerParameters workerParams,
            ListeningExecutorService listeningExecutorService) {
        super(context, workerParams);
        mExecutorService = listeningExecutorService;
    }


    static void scheduleResumeProvisioningWorker(WorkManager workManager) {
        Duration delay = Build.isDebuggable()
                ? Duration.ofMinutes(SystemProperties.getInt(
                PROVISION_PAUSED_MINUTES_SYS_PROPERTY_KEY,
                PROVISION_PAUSED_MINUTES_DEFAULT))
                : Duration.ofHours(PROVISION_PAUSED_HOUR);
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
        LogUtil.v(TAG, "StartWork");
        DeviceStateController stateController =
                ((PolicyObjectsInterface) getApplicationContext()).getStateController();
        return Futures.transform(stateController.setNextStateForEvent(PROVISION_RESUME),
                deviceState -> {
                    LogUtil.v(TAG, String.format("DeviceState is: %s", deviceState));
                    if (PROVISION_IN_PROGRESS == deviceState) {
                        return Result.success();
                    }
                    return Result.failure();
                }, mExecutorService);
    }
}
