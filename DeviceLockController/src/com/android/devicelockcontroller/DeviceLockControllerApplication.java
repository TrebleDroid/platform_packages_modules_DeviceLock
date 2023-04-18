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

import android.app.Application;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.DelegatingWorkerFactory;
import androidx.work.ListenableWorker;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DevicePolicyControllerImpl;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.DeviceStateControllerImpl;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.SetupController;
import com.android.devicelockcontroller.policy.SetupControllerImpl;
import com.android.devicelockcontroller.policy.TaskWorkerFactory;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * Application class for Device Lock Controller.
 */
public class DeviceLockControllerApplication extends Application implements
        PolicyObjectsInterface, Configuration.Provider {
    private static final String TAG = "DeviceLockControllerApplication";

    private DeviceStateController mStateController;
    private DevicePolicyController mPolicyController;

    private static Context sApplicationContext;
    private SetupController mSetupController;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplicationContext = getApplicationContext();
        LogUtil.i(TAG, "onCreate");
    }

    @Override
    @MainThread
    public DeviceStateController getStateController() {
        if (mStateController == null) {
            mStateController = new DeviceStateControllerImpl(this);
        }

        return mStateController;
    }

    @Override
    @MainThread
    public DevicePolicyController getPolicyController() {
        if (mPolicyController == null) {
            final DeviceStateController stateController = getStateController();
            mPolicyController = new DevicePolicyControllerImpl(this, stateController);
        }

        return mPolicyController;
    }

    @Override
    public SetupController getSetupController() {
        if (mSetupController == null) {
            mSetupController = new SetupControllerImpl(this, getStateController(),
                    getPolicyController());
        }
        return mSetupController;
    }

    public static Context getAppContext() {
        return sApplicationContext;
    }

    //b/267355744: Required to initialize WorkManager on-demand.
    @Override
    public Configuration getWorkManagerConfiguration() {
        final DelegatingWorkerFactory factory = new DelegatingWorkerFactory();
        factory.addFactory(new TaskWorkerFactory());
        return new Configuration.Builder()
                .setWorkerFactory(factory)
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
    }

    @Nullable
    public Class<? extends ListenableWorker> getPlayInstallPackageTaskClass() {
        return null;
    }
}
