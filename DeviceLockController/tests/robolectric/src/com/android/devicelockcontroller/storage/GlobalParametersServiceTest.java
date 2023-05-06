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

package com.android.devicelockcontroller.storage;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.RemoteException;

import com.android.devicelockcontroller.policy.DeviceStateController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class GlobalParametersServiceTest extends AbstractGlobalParametersTestBase {
    private IGlobalParametersService mIGlobalParametersService;

    @Before
    public void setUp() {
        final ServiceController<GlobalParametersService> globalParametersServiceController =
                Robolectric.buildService(GlobalParametersService.class);
        final GlobalParametersService globalParametersService =
                globalParametersServiceController.create().get();
        mIGlobalParametersService =
                (IGlobalParametersService) globalParametersService.onBind(new Intent());
    }

    @Test
    public void getDeviceState_shouldReturnExpectedCurrentDeviceState() throws RemoteException {
        assertThat(mIGlobalParametersService.getDeviceState()).isEqualTo(
                DeviceStateController.DeviceState.UNPROVISIONED);
        mIGlobalParametersService.setDeviceState(DeviceStateController.DeviceState.SETUP_SUCCEEDED);
        assertThat(mIGlobalParametersService.getDeviceState()).isEqualTo(
                DeviceStateController.DeviceState.SETUP_SUCCEEDED);
    }

    @Test
    public void getPackageOverridingHome_shouldReturnExpectedOverridingHomePackage()
            throws RemoteException {
        assertThat(mIGlobalParametersService.getPackageOverridingHome()).isNull();
        mIGlobalParametersService.setPackageOverridingHome(PACKAGE_OVERRIDING_HOME);
        assertThat(mIGlobalParametersService.getPackageOverridingHome())
                .isEqualTo(PACKAGE_OVERRIDING_HOME);
    }

    @Test
    public void getLockTaskAllowlist_shouldReturnExpectedAllowlist() throws RemoteException {
        assertThat(mIGlobalParametersService.getLockTaskAllowlist()).isEmpty();
        final ArrayList<String> expectedAllowlist = new ArrayList<>();
        expectedAllowlist.add(ALLOWLIST_PACKAGE_0);
        expectedAllowlist.add(ALLOWLIST_PACKAGE_1);
        mIGlobalParametersService.setLockTaskAllowlist(expectedAllowlist);
        final List<String> actualAllowlist = mIGlobalParametersService.getLockTaskAllowlist();
        assertThat(actualAllowlist).containsExactlyElementsIn(expectedAllowlist);
    }
}
