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

import static org.mockito.Mockito.mock;

import android.app.Application;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.SetupController;

/** Application class that provides mock objects for tests. */
public final class TestDeviceLockControllerApplication extends Application implements
        PolicyObjectsInterface {

    private DevicePolicyController mPolicyController;
    private DeviceStateController mStateController;
    private SetupController mSetupController;

    @Override
    public void onCreate() {
        super.onCreate();
        mPolicyController = mock(DevicePolicyController.class);
        mStateController = mock(DeviceStateController.class);
    }

    public DeviceStateController getMockStateController() {
        if (mStateController == null) {
            mStateController = mock(DeviceStateController.class);
        }
        return mStateController;
    }

    @Override
    public DeviceStateController getStateController() {
        return getMockStateController();
    }

    public DevicePolicyController getMockPolicyController() {
        if (mPolicyController == null) {
            mPolicyController = mock(DevicePolicyController.class);
        }
        return mPolicyController;
    }

    @Override
    public DevicePolicyController getPolicyController() {
        return getMockPolicyController();
    }

    @Override
    public SetupController getSetupController() {
        if (mSetupController == null) {
            mSetupController = mock(SetupController.class);
        }
        return mSetupController;
    }
}
