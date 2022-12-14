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

package com.android.devicelockcontroller.provision.checkin;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DEVICE_ID_TYPE_MEID;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import androidx.core.util.Pair;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowTelephonyManager;

import java.util.Objects;

@RunWith(RobolectricTestRunner.class)
public final class DeviceCheckInHelperImplTest {
    static final int TOTAL_SLOT_COUNT = 2;
    static final int TOTAL_ID_COUNT = 4;
    static final String IMEI_1 = "IMEI1";
    static final String IMEI_2 = "IMEI2";
    static final String MEID_1 = "MEID1";
    static final String MEID_2 = "MEID2";
    static final ArraySet<Pair<Integer, String>> ACTUAL_DEVICE_IDs =
            new ArraySet<Pair<Integer, String>>(new Pair[]{
                    new Pair<>(DEVICE_ID_TYPE_IMEI, IMEI_1),
                    new Pair<>(DEVICE_ID_TYPE_IMEI, IMEI_2),
                    new Pair<>(DEVICE_ID_TYPE_MEID, MEID_1),
                    new Pair<>(DEVICE_ID_TYPE_MEID, MEID_2),
            });
    static final int DEVICE_ID_TYPE_BITMAP =
            (1 << DEVICE_ID_TYPE_IMEI) | (1 << DEVICE_ID_TYPE_MEID);
    private DeviceCheckInHelperImpl mHelper;

    private ShadowTelephonyManager mTelephonyManager;

    @Before
    public void setUp() {
        final Context context = ApplicationProvider.getApplicationContext();
        mTelephonyManager = Shadows.shadowOf(context.getSystemService(TelephonyManager.class));
        mHelper = new DeviceCheckInHelperImpl(context);
    }

    @Test
    public void getDeviceAvailableUniqueIds_shouldReturnAllAvailableUniqueIds() {
        mTelephonyManager.setActiveModemCount(TOTAL_SLOT_COUNT);
        mTelephonyManager.setImei(/* slotIndex= */ 0, IMEI_1);
        mTelephonyManager.setImei(/* slotIndex= */ 1, IMEI_2);
        mTelephonyManager.setMeid(/* slotIndex= */ 0, MEID_1);
        mTelephonyManager.setMeid(/* slotIndex= */ 1, MEID_2);
        final ArraySet<Pair<Integer, String>> deviceIds = mHelper.getDeviceAvailableUniqueIds(
                DEVICE_ID_TYPE_BITMAP);
        assertThat(Objects.requireNonNull(deviceIds).size()).isEqualTo(TOTAL_ID_COUNT);
        assertThat(deviceIds).containsAnyIn(ACTUAL_DEVICE_IDs);
    }
}
