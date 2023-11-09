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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON;
import static com.android.devicelockcontroller.policy.ProvisionHelperImpl.INSTALLATION_TASKS_NAME;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_KIOSK;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_PAUSE;
import static com.android.devicelockcontroller.provision.worker.PauseProvisioningWorker.REPORT_PROVISION_PAUSED_BY_USER_WORK;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.KEY_PROVISION_FAILURE_REASON;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle.State;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.activities.ProvisioningProgress;
import com.android.devicelockcontroller.activities.ProvisioningProgressController;
import com.android.devicelockcontroller.provision.worker.IsDeviceInApprovedCountryWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.shadows.ShadowBuild;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowBuild.class})
public final class ProvisionHelperImplTest {

    private static final String TEST_KIOSK_PACKAGE = "test.package.name";

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();

    private ProvisionStateController mMockStateController;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private ProvisioningProgressController mProgressController;
    @Captor
    private ArgumentCaptor<ProvisioningProgress> mProvisioningProgressArgumentCaptor;

    private TestDeviceLockControllerApplication mTestApp;
    private ProvisionHelper mProvisionHelper;

    private TestDriver mTestDriver;
    private TestWorkerFactory mTestWorkerFactory;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        mMockStateController = mTestApp.getProvisionStateController();
        Executor executor = TestingExecutors.sameThreadScheduledExecutor();
        mProvisionHelper = new ProvisionHelperImpl(mTestApp, mMockStateController, executor);
        mTestWorkerFactory = new TestWorkerFactory();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp,
                new Configuration.Builder()
                        .setExecutor(executor)
                        .setWorkerFactory(mTestWorkerFactory)
                        .build());
        mTestDriver = WorkManagerTestInitHelper.getTestDriver(mTestApp);
    }

    @Test
    public void startProvisionFlow_isKioskAppPreinstalled_debuggableBuild()
            throws ExecutionException, InterruptedException {
        // GIVEN build is debuggable build and kiosk app is installed.
        ShadowBuild.setIsDebuggable(true);
        setupSetupParameters();
        installKioskApp();
        setupLifecycle();

        // WHEN installation is executed
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();

        // THEN go through correct ProvisioningProgress and advance to next state.
        verify(mProgressController, times(3)).setProvisioningProgress(
                mProvisioningProgressArgumentCaptor.capture());
        List<ProvisioningProgress> allValues = mProvisioningProgressArgumentCaptor.getAllValues();
        assertThat(allValues).containsExactlyElementsIn(
                Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                        ProvisioningProgress.INSTALLING_KIOSK_APP,
                        ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));
    }

    @Test
    public void startProvisionFlow_nonDebuggableBuild_playInstall()
            throws Exception {
        // GIVEN build is non-debuggable build
        ShadowBuild.setIsDebuggable(false);
        setupSetupParameters();
        setupLifecycle();

        // WHEN installation is scheduled and successfully executed.
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();

        executeInstallationWorks();

        // THEN should go through correct provisioning progress and advance to next state
        verify(mProgressController, times(3)).setProvisioningProgress(
                mProvisioningProgressArgumentCaptor.capture());
        List<ProvisioningProgress> allValues = mProvisioningProgressArgumentCaptor.getAllValues();
        assertThat(allValues).containsExactlyElementsIn(
                Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                        ProvisioningProgress.INSTALLING_KIOSK_APP,
                        ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));
    }

    @Test
    public void startProvisionFlow_debuggableBuild_kioskNotInstalled_playInstall()
            throws Exception {
        // GIVEN build is debuggable build and kiosk app is not installed.
        ShadowBuild.setIsDebuggable(true);
        setupSetupParameters();
        setupLifecycle();

        // WHEN installation is scheduled and successfully executed.
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();

        executeInstallationWorks();

        // THEN go through correct provisioning progress and advance to next state
        verify(mProgressController, times(3)).setProvisioningProgress(
                mProvisioningProgressArgumentCaptor.capture());
        List<ProvisioningProgress> allValues = mProvisioningProgressArgumentCaptor.getAllValues();
        assertThat(allValues).containsExactlyElementsIn(
                Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                        ProvisioningProgress.INSTALLING_KIOSK_APP,
                        ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));
    }

    @Test
    public void pauseProvision_shouldCallExpectedMethods()
            throws Exception {
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateVoidFuture());
        mProvisionHelper.pauseProvision();
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isTrue();
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleResumeProvisionAlarm();
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(REPORT_PROVISION_PAUSED_BY_USER_WORK);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
    }

    @Test
    public void pauseProvision_withException_shouldCallExpectedMethods()
            throws Exception {
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateFailedFuture(new Exception()));
        mProvisionHelper.pauseProvision();
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isTrue();
        verify(mTestApp.getDeviceLockControllerScheduler(), never()).scheduleResumeProvisionAlarm();
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(REPORT_PROVISION_PAUSED_BY_USER_WORK);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isEmpty();
    }

    @Test
    public void playInstallFailed_mandatoryProvision_reportProvisionFailureAndScheduleReset()
            throws Exception {
        // GIVEN build is not debuggable and play installation should fail
        ShadowBuild.setIsDebuggable(false);
        setupSetupParameters();
        setupLifecycle();
        mTestWorkerFactory.setPlayInstallTaskResult(Result.failure());

        // WHEN mandatory provisioning installation fails
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ true);
        shadowOf(Looper.getMainLooper()).idle();

        executeInstallationWorks();

        // THEN report failure immediately and go through correct progresses and scheduled device
        // reset alarm.
        ListenableFuture<List<WorkInfo>> reportWorkFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(
                        ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
        List<WorkInfo> reportWork = Futures.getChecked(reportWorkFuture, Exception.class);
        assertThat(reportWork).isNotEmpty();

        verify(mProgressController, times(3)).setProvisioningProgress(
                mProvisioningProgressArgumentCaptor.capture());
        List<ProvisioningProgress> allValues = mProvisioningProgressArgumentCaptor.getAllValues();
        assertThat(allValues).containsExactlyElementsIn(
                Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                        ProvisioningProgress.INSTALLING_KIOSK_APP,
                        ProvisioningProgress.MANDATORY_FAILED_PROVISION));
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
    }

    @Test
    public void
            playInstallFailed_nonMandatoryProvision_doNotReportProvisionFailureAndScheduleReset()
            throws Exception {
        // GIVEN build is not debuggable and play installation should fail
        ShadowBuild.setIsDebuggable(false);
        setupSetupParameters();
        setupLifecycle();
        mTestWorkerFactory.setPlayInstallTaskResult(Result.failure(
                new Data.Builder().putInt(KEY_PROVISION_FAILURE_REASON,
                        PLAY_INSTALLATION_FAILED).build()));

        // WHEN non-mandatory provisioning installation fails.
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();

        executeInstallationWorks();

        // THEN failure is not reported and reset alarm is not scheduled and go through correct
        // provisioning progress.
        verify(mProgressController, times(3)).setProvisioningProgress(
                mProvisioningProgressArgumentCaptor.capture());
        List<ProvisioningProgress> allValues = mProvisioningProgressArgumentCaptor.getAllValues();
        assertThat(allValues).containsExactlyElementsIn(
                Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                        ProvisioningProgress.INSTALLING_KIOSK_APP,
                        ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                                PLAY_INSTALLATION_FAILED)));
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(
                        ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isEmpty();
        verify(mTestApp.getDeviceLockControllerScheduler(),
                never()).scheduleMandatoryResetDeviceAlarm();
    }

    @Test
    public void
            fetchGeoEligibilityFailed_nonMandatory_doNotReportProvisionFailureAndScheduleReset()
            throws Exception {
        // GIVEN build is not debuggable and country info is unavailable.
        ShadowBuild.setIsDebuggable(false);
        setupSetupParameters();
        setupLifecycle();
        mTestWorkerFactory.setIsDeviceInApprovedCountryResult(Result.failure(
                new Data.Builder().putInt(KEY_PROVISION_FAILURE_REASON,
                        COUNTRY_INFO_UNAVAILABLE).build()));

        // WHEN non-mandatory provisioning installation fails.
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();

        executeInstallationWorks();

        // THEN should go through correct progresses and not report failure and not schedule device
        // reset alarm.
        verify(mProgressController, times(3)).setProvisioningProgress(
                mProvisioningProgressArgumentCaptor.capture());
        List<ProvisioningProgress> allValues = mProvisioningProgressArgumentCaptor.getAllValues();
        assertThat(allValues).containsExactlyElementsIn(
                Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                        ProvisioningProgress.INSTALLING_KIOSK_APP,
                        ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                                /* Intended to use UNKNOWN_REASON instead of
                                COUNTRY_INFO_UNAVAILABLE, because output data does not pass
                                through work chain when fails */
                                UNKNOWN_REASON)));
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(
                        ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isEmpty();
        verify(mTestApp.getDeviceLockControllerScheduler(),
                never()).scheduleMandatoryResetDeviceAlarm();
    }

    private void executeInstallationWorks() throws Exception {
        ListenableFuture<List<WorkInfo>> installationWorksFuture = WorkManager.getInstance(
                mTestApp).getWorkInfosForUniqueWork(INSTALLATION_TASKS_NAME);
        List<WorkInfo> installationWorks = Futures.getChecked(installationWorksFuture,
                Exception.class);
        assertThat(installationWorks.size()).isEqualTo(2);
        mTestDriver.setAllConstraintsMet(installationWorks.get(0).getId());
        mTestDriver.setAllConstraintsMet(installationWorks.get(1).getId());
        ShadowLooper.runUiThreadTasks();
    }

    private void installKioskApp() {
        ShadowPackageManager pm = Shadows.shadowOf(mTestApp.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_KIOSK_PACKAGE;
        pm.installPackage(kioskPackageInfo);
    }

    private void setupLifecycle() {
        LifecycleRegistry mockLifecycle = new LifecycleRegistry(mMockLifecycleOwner);
        mockLifecycle.setCurrentState(State.RESUMED);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mockLifecycle);
    }

    private static void setupSetupParameters() throws InterruptedException, ExecutionException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(preferences).get();
    }

    /**
     * A {@link WorkerFactory} creates {@link ListenableWorker} which returns result based on
     * caller's input.
     */
    private class TestWorkerFactory extends WorkerFactory {
        private Result mPlayInstallTaskResult = Result.success();

        private Result mIsDeviceInApprovedCountryResult = Result.success();

        private void setPlayInstallTaskResult(Result result) {
            mPlayInstallTaskResult = result;
        }

        private void setIsDeviceInApprovedCountryResult(Result result) {
            mIsDeviceInApprovedCountryResult = result;
        }

        @Override
        public ListenableWorker createWorker(
                @NonNull Context appContext,
                @NonNull String workerClassName,
                @NonNull WorkerParameters workerParameters) {
            return new ListenableWorker(appContext,
                    workerParameters) {
                @NonNull
                @Override
                public ListenableFuture<Result> startWork() {
                    if (workerClassName.equals(
                            mTestApp.getPlayInstallPackageTaskClass().getName())) {
                        return Futures.immediateFuture(mPlayInstallTaskResult);
                    } else if (workerClassName.equals(
                            IsDeviceInApprovedCountryWorker.class.getName())) {
                        return Futures.immediateFuture(mIsDeviceInApprovedCountryResult);
                    }
                    return Futures.immediateFuture(Result.success());
                }
            };
        }
    }
}
