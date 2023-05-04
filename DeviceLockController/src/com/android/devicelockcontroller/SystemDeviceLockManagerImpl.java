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

package com.android.devicelockcontroller;

import android.annotation.CallbackExecutor;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.devicelock.DeviceLockManager;
import android.devicelock.IDeviceLockService;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteCallback;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.android.devicelockcontroller.util.LogUtil;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation for SystemDeviceLockManager.
 */
public final class SystemDeviceLockManagerImpl implements SystemDeviceLockManager {
    private static final String TAG = "SystemDeviceLockManager";

    private final IDeviceLockService mIDeviceLockService;

    private static final String MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER =
            "com.android.devicelockcontroller.permission."
                    + "MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER";

    private SystemDeviceLockManagerImpl(Context context) {
        final Field[] allFields = DeviceLockManager.class.getDeclaredFields();
        final DeviceLockManager deviceLockManager =
                context.getSystemService(DeviceLockManager.class);
        IDeviceLockService deviceLockService = null;
        for (Field field: allFields) {
            if (field.getType().equals(IDeviceLockService.class)) {
                field.setAccessible(true);
                try {
                    deviceLockService = (IDeviceLockService) field.get(deviceLockManager);
                    break;
                } catch (IllegalAccessException e) {
                    LogUtil.e(TAG, "Cannot get device lock service interface", e);
                }
            }
        }
        if (deviceLockService == null) {
            LogUtil.e(TAG, "Cannot find device lock service interface");
        }

        mIDeviceLockService = deviceLockService;
    }

    private SystemDeviceLockManagerImpl() {
        this(DeviceLockControllerApplication.getAppContext());
    }

    // Initialization-on-demand holder.
    private static final class SystemDeviceLockManagerHolder {
        static final SystemDeviceLockManagerImpl sSystemDeviceLockManager =
                new SystemDeviceLockManagerImpl();
    }

    /**
     * Returns the lazily initialized singleton instance of the SystemDeviceLockManager.
     */
    public static SystemDeviceLockManager getInstance() {
        return SystemDeviceLockManagerHolder.sSystemDeviceLockManager;
    }

    private boolean isDeviceLockServiceAvailable(@CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<?, Exception> callback) {
        if (mIDeviceLockService == null) {
            executor.execute(() -> callback.onError(
                    new IllegalStateException("IDeviceLockService is null.")));

            return false;
        }

        return true;
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void addFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        if (!isDeviceLockServiceAvailable(executor, callback)) {
            return;
        }

        try {
            mIDeviceLockService.addFinancedDeviceKioskRole(packageName,
                    new RemoteCallback(result -> executor.execute(() -> {
                        final boolean roleAdded = result.getBoolean(
                                IDeviceLockService.KEY_REMOTE_CALLBACK_RESULT);
                        if (roleAdded) {
                            callback.onResult(null /* result */);
                        } else {
                            callback.onError(new Exception("Failed to add financed role to: "
                                    + packageName));
                        }
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }
}
