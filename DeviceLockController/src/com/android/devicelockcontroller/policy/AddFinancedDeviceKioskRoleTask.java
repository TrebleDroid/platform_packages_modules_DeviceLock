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

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;

import android.content.Context;
import android.os.OutcomeReceiver;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.SystemDeviceLockManagerImpl;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Task to add the ROLE_FINANCED_DEVICE_KIOSK role to the kiosk app.
 */
public final class AddFinancedDeviceKioskRoleTask extends AbstractTask {
    private static final String TAG = "AddFinancedDeviceKioskRoleTask";

    private final ListeningExecutorService mExecutorService;

    final SystemDeviceLockManager mSystemDeviceLockManager;

    @VisibleForTesting
    AddFinancedDeviceKioskRoleTask(Context context,
            WorkerParameters workerParameters,
            ListeningExecutorService executorService,
            SystemDeviceLockManager systemDeviceLockManager) {
        super(context, workerParameters);

        mExecutorService = executorService;
        mSystemDeviceLockManager = systemDeviceLockManager;
    }

    public AddFinancedDeviceKioskRoleTask(Context context,
            WorkerParameters workerParameters,
            ListeningExecutorService executorService) {
        this(context, workerParameters, executorService, SystemDeviceLockManagerImpl.getInstance());
    }

    private ListenableFuture<Result> getAddFinancedDeviceLockKioskRoleFuture(String packageName) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.addFinancedDeviceKioskRole(packageName,
                            mExecutorService,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(Result.success());
                                }

                                @Override
                                public void onError(Exception ex) {
                                    LogUtil.e(TAG, "Failed to add financed device kiosk role", ex);
                                    completer
                                            .set(failure(
                                                    ERROR_CODE_ADD_FINANCED_DEVICE_KIOSK_FAILED));
                                }
                            });
                    // Used only for debugging.
                    return "getAddFinancedDeviceLockKioskRole operation";
                });
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        LogUtil.i(TAG, "Starts to run");

        final String packageName = getInputData().getString(EXTRA_KIOSK_PACKAGE);
        if (TextUtils.isEmpty(packageName)) {
            LogUtil.e(TAG, "No package name provided");
            return Futures.immediateFuture(failure(ERROR_CODE_NO_PACKAGE_NAME));
        }

        return getAddFinancedDeviceLockKioskRoleFuture(packageName);
    }
}
