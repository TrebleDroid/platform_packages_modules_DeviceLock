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

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_RETRY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_SUCCESS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.AbstractDeviceLockControllerScheduler;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.util.List;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
public class NextProvisionFailedStepReceiverTest {

    private GlobalParametersClient mGlobalParameters;
    private AbstractDeviceLockControllerScheduler mScheduler;
    private NextProvisionFailedStepReceiver mReceiver;
    private TestDeviceLockControllerApplication mTestApp;
    private Intent mIntent;

    @Before
    public void setUp() throws Exception {
        mGlobalParameters = GlobalParametersClient.getInstance();
        mScheduler = mock(AbstractDeviceLockControllerScheduler.class);
        mReceiver = new NextProvisionFailedStepReceiver(mScheduler,
                TestingExecutors.sameThreadScheduledExecutor());
        mTestApp = ApplicationProvider.getApplicationContext();
        mIntent = new Intent(mTestApp, NextProvisionFailedStepReceiver.class);

        Configuration config = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp, config);
    }


    @Test
    public void onReceive_nextProvisionStateDismissibleUI_shouldReportState() throws Exception {
        // GIVEN last received provision state is DISMISSIBLE_UI
        mGlobalParameters.setLastReceivedProvisionState(PROVISION_STATE_DISMISSIBLE_UI).get();

        mReceiver.onReceive(mTestApp, mIntent);

        verifyReportProvisionStateWorkScheduled(WorkManager.getInstance(mTestApp));
        verify(mScheduler).scheduleNextProvisionFailedStepAlarm();
    }

    @Test
    public void onReceive_nextProvisionStatePersistentUI_shouldReportState() throws Exception {
        // GIVEN last received provision state is PROVISION_STATE_PERSISTENT_UI
        mGlobalParameters.setLastReceivedProvisionState(PROVISION_STATE_PERSISTENT_UI).get();

        mReceiver.onReceive(mTestApp, mIntent);

        verifyReportProvisionStateWorkScheduled(WorkManager.getInstance(mTestApp));
        verify(mScheduler).scheduleNextProvisionFailedStepAlarm();
    }

    @Ignore //TODO(b/288937639): Figure out how to verify the expectation
    @Test
    public void onReceive_nextProvisionStateFactoryReset_shouldResetDevice() {
    }

    @Test
    public void onReceive_nextProvisionStateUnspecified_shouldNotReportState() throws Exception {
        // GIVEN last received provision state is PROVISION_STATE_UNSPECIFIED
        mGlobalParameters.setLastReceivedProvisionState(PROVISION_STATE_UNSPECIFIED).get();

        mReceiver.onReceive(mTestApp, mIntent);

        verifyReportProvisionStateWorkNotScheduled(WorkManager.getInstance(mTestApp));
        verify(mScheduler, never()).scheduleNextProvisionFailedStepAlarm();
    }

    @Test
    public void doWork_nextProvisionStateSuccess_shouldNotReportState() throws Exception {
        // GIVEN last received provision state is PROVISION_STATE_SUCCESS
        mGlobalParameters.setLastReceivedProvisionState(PROVISION_STATE_SUCCESS).get();

        mReceiver.onReceive(mTestApp, mIntent);

        verifyReportProvisionStateWorkNotScheduled(WorkManager.getInstance(mTestApp));
        verify(mScheduler, never()).scheduleNextProvisionFailedStepAlarm();
    }

    @Test
    public void doWork_nextProvisionStateRetry_shouldNotReportState() throws Exception {
        // GIVEN last received provision state is PROVISION_STATE_RETRY
        mGlobalParameters.setLastReceivedProvisionState(PROVISION_STATE_RETRY).get();

        mReceiver.onReceive(mTestApp, mIntent);

        verifyReportProvisionStateWorkNotScheduled(WorkManager.getInstance(mTestApp));
        verify(mScheduler, never()).scheduleNextProvisionFailedStepAlarm();
    }

    private static void verifyReportProvisionStateWorkScheduled(WorkManager workManager)
            throws Exception {
        // THEN report provision work should be scheduled
        List<WorkInfo> actualWorks = workManager.getWorkInfosForUniqueWork(
                REPORT_PROVISION_STATE_WORK_NAME).get();
        assertThat(actualWorks.size()).isEqualTo(1);
        WorkInfo actualWorkInfo = actualWorks.get(0);
        assertThat(actualWorkInfo.getConstraints().getRequiredNetworkType()).isEqualTo(
                NetworkType.CONNECTED);
    }

    private static void verifyReportProvisionStateWorkNotScheduled(WorkManager workManager)
            throws Exception {
        // THEN report provision work should not be scheduled
        List<WorkInfo> actualWorks = workManager.getWorkInfosForUniqueWork(
                REPORT_PROVISION_STATE_WORK_NAME).get();
        assertThat(actualWorks.size()).isEqualTo(0);
    }
}