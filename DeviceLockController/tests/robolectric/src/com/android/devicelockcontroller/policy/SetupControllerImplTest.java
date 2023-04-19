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
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_CREATE_SESSION_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_EMPTY_DOWNLOAD_URL;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_PACKAGE_INFO;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.DOWNLOAD_FAILED;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.INSTALL_FAILED;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.SETUP_FAILED;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.VERIFICATION_FAILED;
import static com.android.devicelockcontroller.policy.SetupControllerImpl.transformErrorCodeToFailureType;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Bundle;
import android.os.Looper;
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

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.setup.SetupParametersClient;
import com.android.devicelockcontroller.setup.SetupParametersService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.After;
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
import org.robolectric.annotation.LooperMode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
public final class SetupControllerImplTest {

    private static final String TEST_SETUP_ACTIVITY = "packagename/.activity";
    private static final String TEST_DOWNLOAD_URL = "https://www.example.com";
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String TEST_SIGNATURE_CHECKSUM =
            "n2SnR-G5fxMfq7a0Rylsm28CAeefs8U1bmx36JtqgGo=";
    public static final String DOWNLOAD_SUFFIX = "Download";
    public static final String INSTALL_SUFFIX = "Install";

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();

    @Mock
    private DeviceStateController mMockStateController;
    @Mock
    private DevicePolicyController mMockPolicyController;
    @Mock
    private SetupController.SetupUpdatesCallbacks mMockCbs;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;

    private Context mContext;
    private String mFileLocation;
    private SetupParametersClient mSetupParametersClient;
    private TestWorkFactory mTestWorkFactory;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf((Application) mContext).setComponentNameAndServiceForBindService(
                new ComponentName(mContext, SetupParametersService.class),
                Robolectric.setupService(SetupParametersService.class).onBind(null));
        mSetupParametersClient = SetupParametersClient.getInstance(
                mContext, TestingExecutors.sameThreadScheduledExecutor());
        mFileLocation = mContext.getFilesDir() + "/TEST_FILE_NAME";
        createTestFile(mFileLocation);

        mTestWorkFactory = new TestWorkFactory();
        Configuration config =
                new Configuration.Builder().setWorkerFactory(mTestWorkFactory).build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, config);
        Shadows.shadowOf(Looper.getMainLooper()).idleConstantly(true);
    }

    @After
    public void tearDown() {
        SetupParametersClient.reset();
    }

    @Test
    public void testInitialState_SetupFinished() throws Exception {
        Bundle b = new Bundle();
        b.putString(EXTRA_KIOSK_SETUP_ACTIVITY, TEST_SETUP_ACTIVITY);
        createParameters(b);
        when(mMockStateController.getState()).thenReturn(DeviceState.KIOSK_SETUP);
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_COMPLETE)).thenReturn(
                Futures.immediateVoidFuture());
        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mContext, mMockStateController, mMockPolicyController);
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FINISHED);
        setupController.finishSetup();
        verify(mMockStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_COMPLETE));
        verify(mMockPolicyController).launchActivityInLockedMode();
        verify(mMockPolicyController, never()).wipeData();
    }

    @Test
    public void testInitialState_SetupFinishedException() throws Exception {
        Bundle b = new Bundle();
        b.putString(EXTRA_KIOSK_SETUP_ACTIVITY, TEST_SETUP_ACTIVITY);
        createParameters(b);
        when(mMockStateController.getState()).thenReturn(DeviceState.KIOSK_SETUP);
        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mContext, mMockStateController, mMockPolicyController);
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FINISHED);

        doThrow(
                new StateTransitionException(
                        DeviceEvent.PROVISIONING_SUCCESS, DeviceState.UNPROVISIONED))
                .when(mMockStateController)
                .setNextStateForEvent(anyInt());
        setupController.finishSetup();
        verify(mMockPolicyController, never()).launchActivityInLockedMode();
    }

    @Test
    public void testInitialState_mandatoryProvisioning_SetupFailed()
            throws StateTransitionException {
        Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_MANDATORY_PROVISION, true);
        createParameters(bundle);
        when(mMockStateController.getState()).thenReturn(DeviceState.SETUP_FAILED);
        when(mMockStateController.setNextStateForEvent(DeviceEvent.SETUP_FAILURE)).thenReturn(
                Futures.immediateVoidFuture());
        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mContext, mMockStateController, mMockPolicyController);
        setupController.finishSetup();
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FAILED);
        verify(mMockStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_FAILURE));
        verify(mMockPolicyController, never()).launchActivityInLockedMode();
        verify(mMockPolicyController).wipeData();
    }

    @Test
    public void installKioskAppFromURL_kioskAppInstalled_allTasksSucceed()
            throws StateTransitionException {
        // GIVEN all parameters are valid
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_DOWNLOAD_URL, TEST_DOWNLOAD_URL);
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE_NAME);
        bundle.putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, TEST_SIGNATURE_CHECKSUM);
        createParameters(bundle);

        setupLifecycle();

        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN finish kiosk app setup
        setupController.installKioskAppFromURL(WorkManager.getInstance(mContext),
                mMockLifecycleOwner);

        // THEN setup succeeds
        verify(mMockStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_SUCCESS));
        verify(mMockCbs).setupCompleted();
    }

    @Test
    public void installKioskAppFromURL_kioskAppNotInstalled_oneTaskFails()
            throws StateTransitionException {
        // GIVEN verify install task is failed due to no installed package info
        whenVerifyInstallTaskFailed(ERROR_CODE_NO_PACKAGE_INFO);
        setupLifecycle();

        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN finish kiosk app setup
        setupController.installKioskAppFromURL(WorkManager.getInstance(mContext),
                mMockLifecycleOwner);

        // THEN verify task will fail
        verify(mMockStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_FAILURE));
        verify(mMockCbs).setupFailed(eq(VERIFICATION_FAILED));
    }

    @Test
    public void installKioskAppForSecondaryUser_kioskAppInstalled_allTaskSucceed()
            throws StateTransitionException {
        // GIVEN all tasks succeed
        setupLifecycle();
        final SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN install kiosk app for secondary user
        setupController.installKioskAppForSecondaryUser(WorkManager.getInstance(mContext),
                mMockLifecycleOwner);

        verify(mMockStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_SUCCESS));
        verify(mMockCbs).setupCompleted();
    }

    @Test
    public void installKioskAppForSecondaryUser_kioskAppNotInstalled_oneTaskFaied()
            throws StateTransitionException {
        // GIVEN verify install task is failed due to no installed package info
        whenVerifyInstallTaskFailed(ERROR_CODE_NO_PACKAGE_INFO);
        setupLifecycle();

        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);

        // WHEN install kiosk app for secondary user
        setupController.installKioskAppForSecondaryUser(WorkManager.getInstance(mContext),
                mMockLifecycleOwner);

        // THEN verify task will fail
        verify(mMockStateController).setNextStateForEvent(eq(DeviceEvent.SETUP_FAILURE));
        verify(mMockCbs).setupFailed(eq(VERIFICATION_FAILED));
    }

    @Test
    public void testInitialState_SetupNotStarted() {
        when(mMockStateController.getState()).thenReturn(DeviceState.SETUP_IN_PROGRESS);
        SetupControllerImpl setupController =
                new SetupControllerImpl(
                        mContext, mMockStateController, mMockPolicyController);
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_NOT_STARTED);
    }

    @Test
    public void setupFlowTaskCallbackHandler_stateTransitionFailed()
            throws StateTransitionException {
        doThrow(
                new StateTransitionException(
                        DeviceEvent.PROVISIONING_SUCCESS, DeviceState.UNPROVISIONED))
                .when(mMockStateController)
                .setNextStateForEvent(anyInt());

        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);
        setupController.setupFlowTaskCallbackHandler(false, SETUP_FAILED);

        verify(mMockCbs).setupFailed(eq(SETUP_FAILED));
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

        SetupControllerImpl setupController = createSetupControllerImpl(callbacks);
        setupController.setupFlowTaskCallbackHandler(
                false, SetupController.SetupUpdatesCallbacks.FailureType.DOWNLOAD_FAILED);
        assertThat(result.get()).isFalse();
        assertThat(reason.get()).isEqualTo(
                SetupController.SetupUpdatesCallbacks.FailureType.DOWNLOAD_FAILED);
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

        SetupControllerImpl setupController = createSetupControllerImpl(callbacks);
        setupController.setupFlowTaskCallbackHandler(true, SETUP_FAILED);
        assertThat(result.get()).isTrue();
        assertThat(setupController.getSetupState()).isEqualTo(
                SetupController.SetupStatus.SETUP_FINISHED);
    }

    @Test
    public void testSetupUpdatesCallbacks_removeListener() {
        SetupControllerImpl setupController = createSetupControllerImpl(mMockCbs);
        setupController.removeListener(mMockCbs);

        setupController.setupFlowTaskCallbackHandler(true, SETUP_FAILED);
        verify(mMockCbs, never()).setupCompleted();
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
                new SetupControllerImpl(mContext, mMockStateController, mMockPolicyController);
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
