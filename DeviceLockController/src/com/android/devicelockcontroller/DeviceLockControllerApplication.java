/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.devicelockcontroller.util.ThreadUtils.assertMainThread;

import android.app.Application;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.DelegatingWorkerFactory;
import androidx.work.ListenableWorker;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.policy.FinalizationControllerImpl;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.policy.ProvisionStateControllerImpl;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerImpl;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * Application class for Device Lock Controller.
 */
public class DeviceLockControllerApplication extends Application implements
        PolicyObjectsInterface, Configuration.Provider, DeviceLockControllerSchedulerProvider {
    private static final String TAG = "DeviceLockControllerApplication";

    private static Context sApplicationContext;
    private ProvisionStateController mProvisionStateController;
    private FinalizationController mFinalizationController;
    private DeviceLockControllerScheduler mDeviceLockControllerScheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplicationContext = getApplicationContext();
        LogUtil.i(TAG, "onCreate");
    }

    @Override
    @MainThread
    public DeviceStateController getDeviceStateController() {
        assertMainThread("getDeviceStateController");
        return getProvisionStateController().getDeviceStateController();
    }

    @Override
    @MainThread
    public ProvisionStateController getProvisionStateController() {
        assertMainThread("getProvisionStateController");
        if (mProvisionStateController == null) {
            mProvisionStateController = new ProvisionStateControllerImpl(this);
        }
        return mProvisionStateController;
    }

    @Override
    @MainThread
    public DevicePolicyController getPolicyController() {
        assertMainThread("getPolicyController");
        return getProvisionStateController().getDevicePolicyController();
    }

    @Override
    @MainThread
    public FinalizationController getFinalizationController() {
        assertMainThread("getFinalizationController");
        if (mFinalizationController == null) {
            mFinalizationController = new FinalizationControllerImpl(this);
        }
        return mFinalizationController;
    }

    @Override
    @MainThread
    public void destroyObjects() {
        assertMainThread("destroyObjects");
        mProvisionStateController = null;
        mFinalizationController = null;
    }

    public static Context getAppContext() {
        return sApplicationContext;
    }

    //b/267355744: Required to initialize WorkManager on-demand.
    @Override
    public Configuration getWorkManagerConfiguration() {
        final DelegatingWorkerFactory factory = new DelegatingWorkerFactory();
        factory.addFactory(new DeviceLockControllerWorkerFactory());
        return new Configuration.Builder()
                .setWorkerFactory(factory)
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
    }

    @Nullable
    public Class<? extends ListenableWorker> getPlayInstallPackageTaskClass() {
        return null;
    }

    @Override
    @MainThread
    public DeviceLockControllerScheduler getDeviceLockControllerScheduler() {
        assertMainThread("getDeviceLockControllerScheduler");
        if (mDeviceLockControllerScheduler == null) {
            mDeviceLockControllerScheduler = new DeviceLockControllerSchedulerImpl(this,
                    getProvisionStateController());
        }
        return mDeviceLockControllerScheduler;
    }
}
