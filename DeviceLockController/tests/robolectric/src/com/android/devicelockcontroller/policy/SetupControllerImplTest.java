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

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DOWNLOAD_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SETUP_ACTIVITY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SIGNATURE_CHECKSUM;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_MANDATORY_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.KEY_KIOSK_APP_INSTALLED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason.DOWNLOAD_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason.INSTALL_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason.SETUP_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason.VERIFICATION_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_CREATE_SESSION_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_EMPTY_DOWNLOAD_URL;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_PACKAGE_INFO;
import static com.android.devicelockcontroller.policy.SetupControllerImpl.transformErrorCodeToFailureType;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Bundle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle.State;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.shadows.ShadowBuild;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowBuild.class})
public final class SetupControllerImplTest {

    private static final String TEST_SETUP_ACTIVITY = "packagename/.activity";
    private static final String TEST_DOWNLOAD_URL = "https://www.example.com";
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String TEST_SIGNATURE_CHECKSUM =
            "n2SnR-G5fxMfq7a0Rylsm28CAeefs8U1bmx36JtqgGo=";
    public static final String DOWNLOAD_SUFFIX = "Download";
    public static final String INSTALL_SUFFIX = "Install";
    public static final int ASYNC_TIMEOUT_MILLIS = 500;

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();

    private DeviceStateController mMockStateController;
    private DevicePolicyController mMockPolicyController;
    @Mock
    private SetupController.SetupUpdatesCallbacks mMockCbs;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    private TestDeviceLockControllerApplication mTestApplication;
    private String mFileLocation;
    private SetupParametersClient mSetupParametersClient;
    private TestWorkFactory mTestWorkFactory;

    @Before
    public void setUp() {
        mTestApplication = ApplicationProvider.getApplicationContext();
        mMockStateController = mTestApplication.getStateController();
        mMockPolicyController = mTestApplication.getPolicyController();
        when(mMockPolicyController.launchActivityInLockedMode()).thenReturn(
                Futures.immediateFuture(true));
        Shadows.shadowOf(mTestApplication).setComponentNameAndServiceForBindService(
                new ComponentName(mTestApplication, SetupParametersService.class),
                Robolectric.setupService(SetupParametersService.class).onBind(null));
        mSetupParametersClient = SetupParametersClient.getInstance(
                mTestApplication, TestingExecutors.sameThreadScheduledExecutor());
        mFileLocation = mTestApplication.getFilesDir() + "/TEST_FILE_NAME";
        createTestFile(mFileLocation);

        mTestWorkFactory = new TestWorkFactory();
        Configuration config =
                new Configuration.Builder().setWorkerFactory(mTestWorkFactory).build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApplication, config);
    }

    @Test
    public void testInitialState_SetupFinished() {
        Bundle b = new Bundle();
        b.putString(EXTRA_KIOSK_SETUP_ACTIVITY, TEST_SETUP_ACTIVITY);
        createParameters(b);
        when(mMockStateController.getState()).thenReturn(DeviceState.KIOSK_SETUP);
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_COMPLETE)).thenReturn(
                Futures.immediateVoidFuture());
        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mTestApplication, mMockStateController, mMockPolicyController);
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FINISHED);
        Futures.getUnchecked(setupController.finishSetup());
        verify(mMockStateController).setNextStateForEvent(DeviceEvent.SETUP_COMPLETE);
        verify(mMockPolicyController).launchActivityInLockedMode();
        verify(mMockPolicyController, never()).wipeData();
    }

    @Test
    public void testInitialState_mandatoryProvisioning_SetupFailed() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_MANDATORY_PROVISION, true);
        createParameters(bundle);
        when(mMockStateController.getState()).thenReturn(DeviceState.SETUP_FAILED);
        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mTestApplication, mMockStateController, mMockPolicyController);
        Futures.getUnchecked(setupController.finishSetup());
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FAILED);
        verify(mMockPolicyController, never()).launchActivityInLockedMode();
        verify(mMockPolicyController).wipeData();
    }

    @Test
    public void isKioskAppPreinstalled_nonDebuggableBuild_returnFalse() {
        // GIVEN build is non-debuggable build and kiosk app is installed.
        ShadowBuild.setIsDebuggable(false);
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE_NAME);
        createParameters(bundle);
        ShadowPackageManager pm = Shadows.shadowOf(mTestApplication.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_PACKAGE_NAME;
        pm.installPackage(kioskPackageInfo);

        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mTestApplication, mMockStateController, mMockPolicyController);

        assertThat(Futures.getUnchecked(setupController.isKioskAppPreInstalled())).isFalse();
    }

    @Test
    public void isKioskAppPreinstalled_debuggableBuild_kioskPreinstalled_returnTrue() {
        // GIVEN build is debuggable build and kiosk app is installed
        ShadowBuild.setIsDebuggable(true);
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE_NAME);
        createParameters(bundle);
        ShadowPackageManager pm = Shadows.shadowOf(mTestApplication.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_PACKAGE_NAME;
        pm.installPackage(kioskPackageInfo);

        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mTestApplication, mMockStateController, mMockPolicyController);


        assertThat(Futures.getUnchecked(setupController.isKioskAppPreInstalled())).isTrue();
    }

    @Test
    public void isKioskAppPreinstalled_debuggableBuild_kioskNotInstalled_returnFalse() {
        // GIVEN build is debuggable build but kiosk app is not installed
        ShadowBuild.setIsDebuggable(true);
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE_NAME);
        createParameters(bundle);

        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mTestApplication, mMockStateController, mMockPolicyController);

        assertThat(Futures.getUnchecked(setupController.isKioskAppPreInstalled())).isFalse();
    }

    @Test
    public void installKioskAppFromURL_kioskAppInstalled_allTasksSucceed() {
        // GIVEN all parameters are valid
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_DOWNLOAD_URL, TEST_DOWNLOAD_URL);
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE_NAME);
        bundle.putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, TEST_SIGNATURE_CHECKSUM);
        createParameters(bundle);
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_SUCCESS)).thenReturn(
                Futures.immediateVoidFuture());

        setupLifecycle();

        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN finish kiosk app setup
        Futures.getUnchecked(
                setupController.installKioskAppFromURL(WorkManager.getInstance(mTestApplication),
                        mMockLifecycleOwner));

        // THEN setup succeeds
        verify(mMockStateController, timeout(ASYNC_TIMEOUT_MILLIS)).setNextStateForEvent(
                eq(DeviceEvent.SETUP_SUCCESS));
        verify(mMockCbs).setupCompleted();
    }

    @Test
    public void installKioskAppFromURL_kioskAppNotInstalled_oneTaskFails() {
        // GIVEN verify install task is failed due to no installed package info
        whenVerifyInstallTaskFailed(ERROR_CODE_NO_PACKAGE_INFO);
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_FAILURE)).thenReturn(
                Futures.immediateVoidFuture());
        setupLifecycle();

        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN finish kiosk app setup
        Futures.getUnchecked(
                setupController.installKioskAppFromURL(WorkManager.getInstance(mTestApplication),
                        mMockLifecycleOwner));

        // THEN verify task will fail
        verify(mMockStateController, timeout(ASYNC_TIMEOUT_MILLIS)).setNextStateForEvent(
                eq(DeviceEvent.SETUP_FAILURE));
        verify(mMockCbs).setupFailed(eq(VERIFICATION_FAILED));
    }

    @Test
    public void installKioskAppForSecondaryUser_kioskAppInstalled_allTaskSucceed() {
        // GIVEN all tasks succeed
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_SUCCESS)).thenReturn(
                Futures.immediateVoidFuture());

        setupLifecycle();
        final SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN install kiosk app for secondary user
        Futures.getUnchecked(setupController.installKioskAppForSecondaryUser(
                WorkManager.getInstance(mTestApplication),
                mMockLifecycleOwner));

        verify(mMockStateController, timeout(ASYNC_TIMEOUT_MILLIS)).setNextStateForEvent(
                eq(DeviceEvent.SETUP_SUCCESS));
        verify(mMockCbs).setupCompleted();
    }

    @Test
    public void installKioskAppForSecondaryUser_kioskAppNotInstalled_oneTaskFails() {
        // GIVEN verify install task is failed due to no installed package info
        whenVerifyInstallTaskFailed(ERROR_CODE_NO_PACKAGE_INFO);
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_FAILURE)).thenReturn(
                Futures.immediateVoidFuture());
        setupLifecycle();

        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN install kiosk app for secondary user
        Futures.getUnchecked(setupController.installKioskAppForSecondaryUser(
                WorkManager.getInstance(mTestApplication),
                mMockLifecycleOwner));

        // THEN verify task will fail
        verify(mMockStateController, timeout(ASYNC_TIMEOUT_MILLIS)).setNextStateForEvent(
                eq(DeviceEvent.SETUP_FAILURE));
        verify(mMockCbs).setupFailed(eq(VERIFICATION_FAILED));
    }

    @Test
    public void testInitialState_SetupNotStarted() {
        when(mMockStateController.getState()).thenReturn(DeviceState.SETUP_IN_PROGRESS);
        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mTestApplication, mMockStateController, mMockPolicyController);
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_NOT_STARTED);
    }

    @Test
    public void testSetupUpdatesCallbacks_failureCallback() {
        AtomicBoolean result = new AtomicBoolean(true);
        AtomicInteger reason = new AtomicInteger(-1);
        SetupController.SetupUpdatesCallbacks callbacks =
                new SetupController.SetupUpdatesCallbacks() {
                    @Override
                    public void setupCompleted() {
                    }

                    @Override
                    public void setupFailed(int failReason) {
                        result.set(false);
                        reason.set(failReason);
                    }
                };
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_FAILURE)).thenReturn(
                Futures.immediateVoidFuture());
        SetupControllerImpl setupController = createSetupControllerImpl(callbacks);
        setupController.setupFlowTaskFailureCallbackHandler(SetupFailureReason.DOWNLOAD_FAILED);
        assertThat(result.get()).isFalse();
        assertThat(reason.get()).isEqualTo(SetupFailureReason.DOWNLOAD_FAILED);
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FAILED);
    }

    @Test
    public void testSetupUpdatesCallbacks_successCallback() {
        AtomicBoolean result = new AtomicBoolean(false);
        SetupController.SetupUpdatesCallbacks callbacks =
                new SetupController.SetupUpdatesCallbacks() {
                    @Override
                    public void setupCompleted() {
                        result.set(true);
                    }

                    @Override
                    public void setupFailed(int reason) {
                    }
                };

        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_SUCCESS)).thenReturn(
                Futures.immediateVoidFuture());

        SetupControllerImpl setupController = createSetupControllerImpl(callbacks);
        setupController.setupFlowTaskSuccessCallbackHandler();
        assertThat(result.get()).isTrue();
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FINISHED);
    }

    @Test
    public void testSetupUpdatesCallbacks_removeListener() {
        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);
        setupController.removeListener(mMockCbs);
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_SUCCESS)).thenReturn(
                Futures.immediateVoidFuture());

        setupController.setupFlowTaskSuccessCallbackHandler();
        verify(mMockCbs, after(ASYNC_TIMEOUT_MILLIS).never()).setupCompleted();
    }

    @Test
    public void testTransformErrorCodeToFailureType() {
        assertThat(transformErrorCodeToFailureType(ERROR_CODE_EMPTY_DOWNLOAD_URL))
                .isEqualTo(DOWNLOAD_FAILED);
        assertThat(transformErrorCodeToFailureType(ERROR_CODE_NO_PACKAGE_INFO))
                .isEqualTo(VERIFICATION_FAILED);
        assertThat(transformErrorCodeToFailureType(ERROR_CODE_CREATE_SESSION_FAILED))
                .isEqualTo(INSTALL_FAILED);
        int invalidErrorCode = 100;
        assertThat(transformErrorCodeToFailureType(invalidErrorCode)).isEqualTo(SETUP_FAILED);
    }

    private static PackageInfo createKioskPackageInfo(Signature[] signatures) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = TEST_PACKAGE_NAME;
        packageInfo.signingInfo = new SigningInfo();
        Shadows.shadowOf(packageInfo.signingInfo).setSignatures(signatures);
        return packageInfo;
    }

    private void whenDownloadTaskFailed(int errorCode) {
        mTestWorkFactory.mResultMap.put(DownloadPackageTask.class.getName(), errorCode);
    }

    private void whenVerifyDownloadTaskFailed(int errorCode) {
        mTestWorkFactory.mResultMap.put(VerifyPackageTask.class.getName() + DOWNLOAD_SUFFIX,
                errorCode);
    }

    private void whenInstallExistingPackageTaskFailed(int errorCode) {
        mTestWorkFactory.mResultMap.put(InstallExistingPackageTask.class.getName(), errorCode);
    }

    private void whenInstallTaskFailed(int errorCode) {
        mTestWorkFactory.mResultMap.put(InstallPackageTask.class.getName(), errorCode);

    }

    private void whenVerifyInstallTaskFailed(int errorCode) {
        mTestWorkFactory.mResultMap.put(VerifyPackageTask.class.getName() + INSTALL_SUFFIX,
                errorCode);

    }

    private static void createTestFile(String fileLocation) {
        try (FileOutputStream outputStream = new FileOutputStream(fileLocation)) {
            outputStream.write(new byte[]{1, 2, 3, 4, 5});
        } catch (IOException e) {
            throw new AssertionError("Exception", e);
        }
    }

    private void createParameters(Bundle b) {
        try {
            Futures.getChecked(mSetupParametersClient.createPrefs(b), ExecutionException.class);
        } catch (ExecutionException e) {
            throw new AssertionError("Failed to create setup parameters!", e);
        }
    }

    private void setupLifecycle() {
        LifecycleRegistry mockLifecycle = new LifecycleRegistry(mMockLifecycleOwner);
        mockLifecycle.setCurrentState(State.RESUMED);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mockLifecycle);
    }

    private SetupControllerImpl createSetupControllerImpl(
            SetupController.SetupUpdatesCallbacks callbacks) {
        SetupControllerImpl setupController =
                new SetupControllerImpl(mTestApplication, mMockStateController,
                        mMockPolicyController);
        setupController.addListener(callbacks);
        return setupController;
    }

    private final class TestWorkFactory extends WorkerFactory {

        private final ListeningExecutorService mExecutorService;

        private final ArrayMap<String, Integer> mResultMap =
                new ArrayMap<>();

        TestWorkFactory() {
            mExecutorService = TestingExecutors.sameThreadScheduledExecutor();
        }

        @Nullable
        @Override
        public ListenableWorker createWorker(
                @NonNull Context context,
                @NonNull String workerClassName,
                @NonNull WorkerParameters workerParameters) {
            return new AbstractTask(context, workerParameters) {
                @NonNull
                @Override
                public ListenableFuture<Result> startWork() {
                    return mExecutorService.submit(() -> {
                        final Integer resultCode = mResultMap.get(workerClassName + getSuffix());
                        return resultCode == null || resultCode < 0
                                ? Result.success(new Data.Builder().putString(
                                TASK_RESULT_DOWNLOADED_FILE_LOCATION_KEY, mFileLocation).build())
                                : failure(resultCode);
                    });
                }

                private String getSuffix() {
                    if (workerClassName.equals(VerifyPackageTask.class.getName())) {
                        return getInputData().getBoolean(KEY_KIOSK_APP_INSTALLED, false)
                                ? INSTALL_SUFFIX : DOWNLOAD_SUFFIX;
                    }
                    return "";
                }

            };
        }
    }
}
