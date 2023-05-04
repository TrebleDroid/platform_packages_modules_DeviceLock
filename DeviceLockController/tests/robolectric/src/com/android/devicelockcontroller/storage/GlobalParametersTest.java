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

package com.android.devicelockcontroller.storage;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class GlobalParametersTest extends AbstractGlobalParametersTestBase {
    private Context mContext;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void getDeviceState_shouldReturnExpectedCurrentDeviceState() {
        assertThat(GlobalParameters.getDeviceState(mContext)).isEqualTo(DeviceState.UNPROVISIONED);
        GlobalParameters.setDeviceState(mContext, DeviceState.SETUP_SUCCEEDED);
        assertThat(GlobalParameters.getDeviceState(mContext)).isEqualTo(
                DeviceState.SETUP_SUCCEEDED);
    }

    @Test
    public void getPackageOverridingHome_shouldReturnExpectedOverridingHomePackage() {
        assertThat(GlobalParameters.getPackageOverridingHome(mContext)).isNull();
        GlobalParameters.setPackageOverridingHome(mContext, PACKAGE_OVERRIDING_HOME);
        assertThat(GlobalParameters.getPackageOverridingHome(mContext))
                .isEqualTo(PACKAGE_OVERRIDING_HOME);
    }

    @Test
    public void getLockTaskAllowlist_shouldReturnExpectedAllowlist() {
        assertThat(GlobalParameters.getLockTaskAllowlist(mContext)).isEmpty();
        final ArrayList<String> expectedAllowlist = new ArrayList<>();
        expectedAllowlist.add(ALLOWLIST_PACKAGE_0);
        expectedAllowlist.add(ALLOWLIST_PACKAGE_1);
        GlobalParameters.setLockTaskAllowlist(mContext, expectedAllowlist);
        final List<String> actualAllowlist = GlobalParameters.getLockTaskAllowlist(mContext);
        assertThat(actualAllowlist).containsExactlyElementsIn(expectedAllowlist);
    }
}
