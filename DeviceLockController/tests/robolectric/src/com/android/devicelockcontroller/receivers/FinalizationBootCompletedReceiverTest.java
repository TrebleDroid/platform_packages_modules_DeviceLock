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

package com.android.devicelockcontroller.receivers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.UserManager;

import com.android.devicelockcontroller.DeviceLockControllerApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class FinalizationBootCompletedReceiverTest {

    public static final Intent INTENT = new Intent(Intent.ACTION_BOOT_COMPLETED);

    @Mock
    private DeviceLockControllerApplication mApp;
    private FinalizationBootCompletedReceiver mReceiver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mApp.getSystemService(UserManager.class)).thenReturn(mock(UserManager.class));
        when(mApp.getApplicationContext()).thenReturn(mApp);
        mReceiver = new FinalizationBootCompletedReceiver();
    }

    @Test
    public void onReceive_initializeFinalizationController() {
        mReceiver.onReceive(mApp, INTENT);

        verify(mApp).getFinalizationController();
    }
}
