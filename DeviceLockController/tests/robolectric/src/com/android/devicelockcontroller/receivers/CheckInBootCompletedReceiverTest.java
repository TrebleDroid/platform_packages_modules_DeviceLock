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

package com.android.devicelockcontroller.receivers;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.provision.checkin.DeviceCheckInHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CheckInBootCompletedReceiverTest {

    @Mock
    private DeviceStateController mDeviceStateController;
    @Mock
    private DeviceCheckInHelper mDeviceCheckInHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */ this);
    }

    @Test
    public void onReceive_checkInIsNeeded_shouldEnqueueCheckInWork() {
        when(mDeviceStateController.isCheckInNeeded()).thenReturn(true);

        CheckInBootCompletedReceiver.checkInIfNeeded(mDeviceStateController, mDeviceCheckInHelper);

        verify(mDeviceCheckInHelper).enqueueDeviceCheckInWork(eq(false));
    }

    @Test
    public void onReceive_checkInNotNeeded_shouldNotEnqueueCheckInWork() {
        when(mDeviceStateController.isCheckInNeeded()).thenReturn(false);

        CheckInBootCompletedReceiver.checkInIfNeeded(mDeviceStateController, mDeviceCheckInHelper);

        verify(mDeviceCheckInHelper, never()).enqueueDeviceCheckInWork(anyBoolean());
    }
}
