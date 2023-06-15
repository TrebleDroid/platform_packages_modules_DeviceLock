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

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;

/**
 * A worker class dedicated to start lock task mode when device is locked.
 */
public final class StartLockTaskModeWorker extends ListenableWorker {

    private static final String TAG = "StartLockTaskModeWorker";
    static final String START_LOCK_TASK_MODE_WORK_NAME = "start-lock-task-mode";
    private final Context mContext;
    private final ListeningExecutorService mExecutorService;

    public StartLockTaskModeWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams,
            ListeningExecutorService executorService) {
        super(context, workerParams);
        mContext = context;
        mExecutorService = executorService;
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        ActivityManager am =
                Objects.requireNonNull(mContext.getSystemService(ActivityManager.class));
        ListenableFuture<Boolean> isInLockTaskModeFuture =
                Futures.submit(
                        () -> am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED,
                        mExecutorService);
        return Futures.transformAsync(isInLockTaskModeFuture, isInLockTaskMode -> {
            if (isInLockTaskMode) {
                LogUtil.i(TAG, "Lock task mode is active now");
                return Futures.immediateFuture(Result.success());
            }
            DevicePolicyController policyController =
                    ((PolicyObjectsInterface) mContext.getApplicationContext())
                            .getPolicyController();
            return Futures.transform(policyController.getLaunchIntentForCurrentLockedActivity(),
                    launchIntent -> {
                        if (launchIntent == null) {
                            LogUtil.e(TAG, "Failed to enter lock task mode: no intent to launch");
                            return Result.failure();
                        }

                        launchIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        LogUtil.i(TAG, "Launching activity for intent: " + launchIntent);
                        mContext.startActivity(launchIntent,
                                ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle());

                        if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                            LogUtil.i(TAG, "Successfully entered lock task mode");
                            return Result.success();
                        } else {
                            LogUtil.i(TAG, "Retry entering lock task mode");
                            return Result.retry();
                        }
                    }, MoreExecutors.directExecutor());
        }, MoreExecutors.directExecutor());
    }
}
