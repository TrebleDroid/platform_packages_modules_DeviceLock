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

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.google.common.util.concurrent.testing.TestingExecutors;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public final class ResumeProvisioningWorkerTest {
    private TestDeviceLockControllerApplication mTestApp;
    private ResumeProvisioningWorker mWorker;
    private DeviceStateController mDeviceStateController;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        mDeviceStateController = mTestApp.getStateController();
        mWorker = TestListenableWorkerBuilder.from(mTestApp, ResumeProvisioningWorker.class)
                .setWorkerFactory(new WorkerFactory() {
                    @Override
                    public ListenableWorker createWorker(@NotNull Context appContext,
                            @NotNull String workerClassName,
                            @NotNull WorkerParameters workerParameters) {
                        if (workerClassName.equals(ResumeProvisioningWorker.class.getName())) {
                            return new ResumeProvisioningWorker(appContext, workerParameters,
                                    TestingExecutors.sameThreadScheduledExecutor());
                        }
                        return null;
                    }
                }).build();
    }

    @Test
    public void scheduleResumeProvisioningWorker_shouldScheduleTheWorker() {
        WorkManager workManager = mock(WorkManager.class);
        Duration delay = Duration.ofHours(PauseProvisioningWorker.PROVISION_PAUSED_HOUR);
        ResumeProvisioningWorker.scheduleResumeProvisioningWorker(workManager, delay);

        ArgumentCaptor<OneTimeWorkRequest> workRequestArgumentCaptor = ArgumentCaptor.forClass(
                OneTimeWorkRequest.class);
        verify(workManager).enqueueUniqueWork(eq(ResumeProvisioningWorker.RESUME_PROVISION_WORK),
                eq(ExistingWorkPolicy.KEEP), workRequestArgumentCaptor.capture());
        assertThat(workRequestArgumentCaptor.getValue().getWorkSpec().initialDelay)
                .isEqualTo(delay.toMillis());
        assertThat(workRequestArgumentCaptor.getValue().getWorkSpec().workerClassName)
                .isEqualTo(ResumeProvisioningWorker.class.getName());
    }

    @Test
    public void startWork_shouldDispatchResumeProvisionEvent() {
        when(mDeviceStateController.setNextStateForEvent(eq(DeviceEvent.SETUP_RESUME)))
                .thenReturn(Futures.immediateFuture(DeviceState.SETUP_IN_PROGRESS));

        ListenableFuture<Result> result = mWorker.startWork();

        assertThat(Futures.getUnchecked(result)).isEqualTo(Result.success());
        verify(mDeviceStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_RESUME));
    }

    @Test
    public void startWork_shouldFail_whenDeviceStateDidNotTransitionToInProgress() {
        when(mDeviceStateController.setNextStateForEvent(eq(DeviceEvent.SETUP_RESUME)))
                .thenReturn(Futures.immediateFuture(DeviceState.SETUP_FAILED));

        ListenableFuture<Result> result = mWorker.startWork();

        assertThat(Futures.getUnchecked(result)).isEqualTo(Result.failure());
        verify(mDeviceStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_RESUME));
    }
}
