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

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_FACTORY_RESET;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_RETRY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_SUCCESS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;

import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ReportDeviceProvisionStateWorkerResponseTest {
    @ParameterizedRobolectricTestRunner.Parameter
    public boolean mWithDelay;

    @ParameterizedRobolectricTestRunner.Parameter(1)
    @DeviceProvisionState
    public int mLastState;

    @ParameterizedRobolectricTestRunner.Parameter(2)
    @DeviceProvisionState
    public int mNextState;

    /**
     * Parameters
     */
    @ParameterizedRobolectricTestRunner.Parameters(name =
            "Schedule next step with delay: {0}, when last step is {1} and next step is {2}.")
    public static List<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {false, PROVISION_STATE_UNSPECIFIED, PROVISION_STATE_RETRY},
                {true, PROVISION_STATE_UNSPECIFIED, PROVISION_STATE_DISMISSIBLE_UI},
                {true, PROVISION_STATE_UNSPECIFIED, PROVISION_STATE_PERSISTENT_UI},
                {true, PROVISION_STATE_UNSPECIFIED, PROVISION_STATE_FACTORY_RESET},
                {true, PROVISION_STATE_UNSPECIFIED, PROVISION_STATE_SUCCESS},
                {false, PROVISION_STATE_RETRY, PROVISION_STATE_RETRY},
                {true, PROVISION_STATE_RETRY, PROVISION_STATE_DISMISSIBLE_UI},
                {true, PROVISION_STATE_RETRY, PROVISION_STATE_PERSISTENT_UI},
                {true, PROVISION_STATE_RETRY, PROVISION_STATE_FACTORY_RESET},
                {true, PROVISION_STATE_RETRY, PROVISION_STATE_SUCCESS},
                {false, PROVISION_STATE_DISMISSIBLE_UI, PROVISION_STATE_DISMISSIBLE_UI},
                {false, PROVISION_STATE_DISMISSIBLE_UI, PROVISION_STATE_PERSISTENT_UI},
                {false, PROVISION_STATE_DISMISSIBLE_UI, PROVISION_STATE_FACTORY_RESET},
                {false, PROVISION_STATE_PERSISTENT_UI, PROVISION_STATE_FACTORY_RESET},
        });
    }

    @Test
    public void shouldScheduleWithDelay_returnCorrectValue() {
        Truth.assertThat(ReportDeviceProvisionStateWorker.shouldRunNextStepImmediately(mLastState,
                mNextState)).isEqualTo(mWithDelay);
    }
}
