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
import static com.android.devicelockcontroller.proto.ClientCheckinStatus.CLIENT_CHECKIN_STATUS_RETRY_CHECKIN;
import static com.android.devicelockcontroller.proto.ClientCheckinStatus.CLIENT_CHECKIN_STATUS_STOP_CHECKIN;

import static com.google.common.truth.Truth.assertThat;

import android.telephony.TelephonyManager;
import android.util.ArraySet;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.proto.ClientCheckinStatus;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusResponse;
import com.android.devicelockcontroller.proto.NextCheckinInformation;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusResponseWrapper;
import com.android.devicelockcontroller.setup.UserPreferences;

import com.google.protobuf.Timestamp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowTelephonyManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(RobolectricTestRunner.class)
public final class DeviceCheckInHelperTest {
    static final Duration TEST_CHECK_RETRY_DURATION = Duration.ofDays(30);
    private TestDeviceLockControllerApplication mTestApplication;
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
    private DeviceCheckInHelper mHelper;

    private ShadowTelephonyManager mTelephonyManager;

    @Before
    public void setUp() {
        mTestApplication = ApplicationProvider.getApplicationContext();
        mTelephonyManager = Shadows.shadowOf(
                mTestApplication.getSystemService(TelephonyManager.class));
        mHelper = new DeviceCheckInHelper(mTestApplication);
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApplication,
                new Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .setExecutor(new SynchronousExecutor())
                        .build());
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

    @Test
    public void testHandleGetDeviceCheckInStatusResponse_stopCheckIn_shouldSetNeedCheckInFalse() {
        final GetDeviceCheckInStatusResponseWrapper response = createMockResponse(
                CLIENT_CHECKIN_STATUS_STOP_CHECKIN);

        assertThat(mHelper.handleGetDeviceCheckInStatusResponse(response)).isTrue();

        assertThat(UserPreferences.needCheckIn(mTestApplication)).isFalse();
    }

    @Test
    public void testHandleGetDeviceCheckInStatusResponse_retryCheckIn_shouldEnqueueNewCheckInWork()
            throws ExecutionException, InterruptedException, TimeoutException {
        final GetDeviceCheckInStatusResponseWrapper response = createMockResponse(
                CLIENT_CHECKIN_STATUS_RETRY_CHECKIN,
                Instant.now().plus(TEST_CHECK_RETRY_DURATION));

        assertThat(mHelper.handleGetDeviceCheckInStatusResponse(response)).isTrue();

        WorkManager workManager = WorkManager.getInstance(mTestApplication);

        List<WorkInfo> workInfo = workManager.getWorkInfosForUniqueWork(
                DeviceCheckInHelper.CHECK_IN_WORK_NAME).get(500, TimeUnit.MILLISECONDS);
        assertThat(workInfo.size()).isEqualTo(1);
    }

    private static GetDeviceCheckInStatusResponseWrapper createMockResponse(
            ClientCheckinStatus checkInStatus) {
        return createMockResponse(checkInStatus, /* nextCheckInDate= */ null);
    }

    private static GetDeviceCheckInStatusResponseWrapper createMockResponse(
            ClientCheckinStatus checkInStatus,
            @Nullable Instant nextCheckInTime) {
        GetDeviceCheckinStatusResponse.Builder builder =
                GetDeviceCheckinStatusResponse.newBuilder()
                        .setClientCheckinStatus(checkInStatus);

        if (nextCheckInTime != null) {
            builder.setNextCheckinInformation(
                    NextCheckinInformation.newBuilder().setNextCheckinTimestamp(
                            Timestamp.newBuilder()
                                    .setSeconds(nextCheckInTime.getEpochSecond())
                                    .setNanos(nextCheckInTime.getNano())));
        }

        return new GetDeviceCheckInStatusResponseWrapper(builder.build());
    }
}
