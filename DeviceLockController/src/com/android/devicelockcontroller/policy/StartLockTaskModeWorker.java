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
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Duration;
import java.util.Objects;

/**
 * A worker class dedicated to start lock task mode when device is locked.
 */
public final class StartLockTaskModeWorker extends ListenableWorker {

    private static final String TAG = "StartLockTaskModeWorker";
    static final String START_LOCK_TASK_MODE_WORK_NAME = "start-lock-task-mode";
    private final Context mContext;
    private final ListeningExecutorService mExecutorService;

    static final Duration START_LOCK_TASK_MODE_WORKER_RETRY_INTERVAL_SECONDS =
            Duration.ofSeconds(30);
    private final DevicePolicyManager mDpm;

    /** Enqueue this worker to start lock task mode */
    public static void startLockTaskMode(WorkManager workManager) {
        OneTimeWorkRequest startLockTask = new OneTimeWorkRequest.Builder(
                StartLockTaskModeWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR,
                        START_LOCK_TASK_MODE_WORKER_RETRY_INTERVAL_SECONDS)
                .build();
        workManager.enqueueUniqueWork(
                START_LOCK_TASK_MODE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                startLockTask);
    }

    public StartLockTaskModeWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams,
            ListeningExecutorService executorService) {
        super(context, workerParams);
        mContext = context;
        mExecutorService = executorService;
        mDpm = Objects.requireNonNull(mContext.getSystemService(DevicePolicyManager.class));
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        ActivityManager am =
                Objects.requireNonNull(mContext.getSystemService(ActivityManager.class));
        DevicePolicyController devicePolicyController =
                ((PolicyObjectsInterface) mContext.getApplicationContext())
                        .getProvisionStateController().getDevicePolicyController();
        ListenableFuture<Boolean> isInLockTaskModeFuture =
                Futures.submit(
                        () -> am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED,
                        mExecutorService);
        return Futures.transformAsync(isInLockTaskModeFuture, isInLockTaskMode -> {
            if (isInLockTaskMode) {
                LogUtil.i(TAG, "Lock task mode is active now");
                return Futures.immediateFuture(Result.success());
            }

            return Futures.transform(
                    devicePolicyController.getLaunchIntentForCurrentState(),
                    launchIntent -> {
                        if (launchIntent == null) {
                            LogUtil.e(TAG, "Failed to enter lock task mode: no intent to launch");
                            return Result.failure();
                        }
                        ComponentName launchIntentComponent = launchIntent.getComponent();
                        String packageName = launchIntentComponent.getPackageName();
                        if (!Objects.requireNonNull(mDpm).isLockTaskPermitted(packageName)) {
                            LogUtil.e(TAG, packageName + " is not permitted in lock task mode");
                            return Result.failure();
                        }
                        setPreferredActivityForHome(launchIntentComponent);
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
                    }, mExecutorService);
        }, mExecutorService);
    }

    private void setPreferredActivityForHome(ComponentName activity) {

        final String currentPackage = UserParameters.getPackageOverridingHome(mContext);
        if (currentPackage != null && !currentPackage.equals(activity.getPackageName())) {
            mDpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
        } else {
            mDpm.addPersistentPreferredActivity(null /* admin */, getHomeIntentFilter(), activity);
            UserParameters.setPackageOverridingHome(mContext, activity.getPackageName());
        }
    }

    private static IntentFilter getHomeIntentFilter() {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        return filter;
    }
}
