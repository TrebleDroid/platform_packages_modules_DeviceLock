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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.AbstractDeviceLockControllerScheduler;
import com.android.devicelockcontroller.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Boot completed broadcast receiver to enqueue the check-in work for provision when device boots
 * for the first time.
 */
public final class CheckInBootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "CheckInBootCompletedReceiver";
    private AbstractDeviceLockControllerScheduler mScheduler;
    private final Executor mExecutor;

    public CheckInBootCompletedReceiver() {
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @VisibleForTesting
    CheckInBootCompletedReceiver(AbstractDeviceLockControllerScheduler scheduler,
            Executor executor) {
        mScheduler = scheduler;
        mExecutor = executor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) return;

        LogUtil.i(TAG, "Received boot completed intent");

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();

        if (isUserProfile) {
            return;
        }
        ProvisionStateController provisionStateController =
                ((PolicyObjectsInterface) context.getApplicationContext())
                        .getProvisionStateController();
        if (mScheduler == null) {
            mScheduler = new DeviceLockControllerScheduler(context, provisionStateController);
        }
        ListenableFuture<Boolean> needReschedule = Futures.transformAsync(
                Futures.submit(() -> UserParameters.needInitialCheckIn(context), mExecutor),
                needCheckIn -> {
                    if (needCheckIn) {
                        mScheduler.scheduleInitialCheckInWork();
                        return Futures.immediateFuture(false);
                    } else {
                        return Futures.transform(
                                provisionStateController.getState(),
                                state -> state == UNPROVISIONED, mExecutor);
                    }
                }, mExecutor);
        Futures.addCallback(needReschedule,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (result) {
                            mScheduler.notifyNeedRescheduleCheckIn();
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, mExecutor);
    }
}
