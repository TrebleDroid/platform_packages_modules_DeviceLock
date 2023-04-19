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

package com.android.devicelockcontroller.setup;

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
public class UserPreferencesServiceTest extends AbstractUserPreferencesTestBase {
    private IUserPreferencesService mIUserPreferencesService;

    @Before
    public void setUp() {
        final ServiceController<UserPreferencesService> userPreferencesServiceController =
                Robolectric.buildService(UserPreferencesService.class);
        final UserPreferencesService userPreferencesService =
                userPreferencesServiceController.create().get();
        mIUserPreferencesService =
                (IUserPreferencesService) userPreferencesService.onBind(new Intent());
    }

    @Test
    public void getDeviceState_shouldReturnExpectedCurrentDeviceState() throws RemoteException {
        assertThat(mIUserPreferencesService.getDeviceState()).isEqualTo(
                DeviceStateController.DeviceState.UNPROVISIONED);
        mIUserPreferencesService.setDeviceState(DeviceStateController.DeviceState.SETUP_SUCCEEDED);
        assertThat(mIUserPreferencesService.getDeviceState()).isEqualTo(
                DeviceStateController.DeviceState.SETUP_SUCCEEDED);
    }

    @Test
    public void getPackageOverridingHome_shouldReturnExpectedOverridingHomePackage()
            throws RemoteException {
        assertThat(mIUserPreferencesService.getPackageOverridingHome()).isNull();
        mIUserPreferencesService.setPackageOverridingHome(PACKAGE_OVERRIDING_HOME);
        assertThat(mIUserPreferencesService.getPackageOverridingHome())
                .isEqualTo(PACKAGE_OVERRIDING_HOME);
    }

    @Test
    public void getLockTaskAllowlist_shouldReturnExpectedAllowlist() throws RemoteException {
        assertThat(mIUserPreferencesService.getLockTaskAllowlist()).isEmpty();
        final ArrayList<String> expectedAllowlist = new ArrayList<>();
        expectedAllowlist.add(ALLOWLIST_PACKAGE_0);
        expectedAllowlist.add(ALLOWLIST_PACKAGE_1);
        mIUserPreferencesService.setLockTaskAllowlist(expectedAllowlist);
        final List<String> actualAllowlist = mIUserPreferencesService.getLockTaskAllowlist();
        assertThat(actualAllowlist).containsExactlyElementsIn(expectedAllowlist);
    }
}
