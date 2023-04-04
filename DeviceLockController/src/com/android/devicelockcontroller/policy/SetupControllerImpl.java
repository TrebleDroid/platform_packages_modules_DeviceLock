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

import static androidx.work.WorkInfo.State.CANCELLED;
import static androidx.work.WorkInfo.State.FAILED;
import static androidx.work.WorkInfo.State.SUCCEEDED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DOWNLOAD_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SIGNATURE_CHECKSUM;
import static com.android.devicelockcontroller.common.DeviceLockConstants.KEY_KIOSK_APP_INSTALLED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_CREATE_LOCAL_FILE_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_DELETE_APK_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_EMPTY_DOWNLOAD_URL;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_GET_PENDING_INTENT_FAILED;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_PACKAGE_INFO;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_NO_PACKAGE_NAME;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS;
import static com.android.devicelockcontroller.policy.AbstractTask.ERROR_CODE_TOO_MANY_REDIRECTS;
import static com.android.devicelockcontroller.policy.AbstractTask.TASK_RESULT_ERROR_CODE_KEY;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.DELETE_PACKAGE_FAILED;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.DOWNLOAD_FAILED;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.INSTALL_EXISTING_FAILED;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.INSTALL_FAILED;
import static com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType.VERIFICATION_FAILED;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LifecycleOwner;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType;
import com.android.devicelockcontroller.setup.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Controller managing communication between setup tasks and UI layer. */
public final class SetupControllerImpl implements SetupController {

    private static final String SETUP_URL_INSTALL_TASKS_NAME = "devicelock_setup_url_install_tasks";
    private static final String SETUP_PLAY_INSTALL_TASKS_NAME =
            "devicelock_setup_play_install_tasks";
    public static final String SETUP_VERIFY_PRE_INSTALLED_PACKAGE_TASK =
            "devicelock_setup_verify_pre_installed_package_task";
    public static final String TAG = "SetupController";
    public static final String INSTALL_KIOSK_APP_FOR_SECONDARY_USER_TASK =
            "setup_install_kiosk_app_for_secondary_user_task";
    public static final String SETUP_INSTALL_EXISTING_PACKAGE_TASK =
            "devicelock_setup_install_existing_package_task";


    private final List<SetupUpdatesCallbacks> mCallbacks = new ArrayList<>();
    @SetupStatus
    private int mCurrentSetupState;
    private final Context mContext;
    private final DevicePolicyController mPolicyController;
    private final DeviceStateController mStateController;

    public SetupControllerImpl(
            Context context,
            DeviceStateController stateController,
            DevicePolicyController policyController) {
        this.mContext = context;
        this.mStateController = stateController;
        this.mPolicyController = policyController;
        int state = stateController.getState();
        if (state == DeviceState.SETUP_IN_PROGRESS || state == DeviceState.UNPROVISIONED) {
            mCurrentSetupState = SetupStatus.SETUP_NOT_STARTED;
        } else if (state == DeviceState.SETUP_FAILED) {
            mCurrentSetupState = SetupStatus.SETUP_FAILED;
        } else {
            mCurrentSetupState = SetupStatus.SETUP_FINISHED;
        }
        LogUtil.v(TAG,
                String.format(Locale.US, "Setup started with state = %d", mCurrentSetupState));
    }

    @Override
    public void addListener(SetupUpdatesCallbacks cb) {
        synchronized (mCallbacks) {
            mCallbacks.add(cb);
        }
    }

    @Override
    public void removeListener(SetupUpdatesCallbacks cb) {
        synchronized (mCallbacks) {
            mCallbacks.remove(cb);
        }
    }

    @Override
    @SetupStatus
    public int getSetupState() {
        LogUtil.v(TAG, String.format(Locale.US, "Setup state returned = %d", mCurrentSetupState));
        return mCurrentSetupState;
    }

    @Override
    public void startSetupFlow(LifecycleOwner owner) {
        LogUtil.v(TAG, "Trigger setup flow");
        WorkManager workManager = WorkManager.getInstance(mContext);
        if (isKioskAppPreInstalled() && Build.isDebuggable()) {
            verifyPreInstalledPackage(workManager, owner);
        } else if (mContext.getUser().isSystem()) {
            final Class<? extends ListenableWorker> playInstallTaskClass =
                    ((DeviceLockControllerApplication) mContext.getApplicationContext())
                            .getPlayInstallPackageTaskClass();
            if (playInstallTaskClass != null) {
                installKioskAppFromPlay(workManager, owner, playInstallTaskClass);
            } else {
                installKioskAppFromURL(workManager, owner);
            }
        } else {
            // Setup flow for secondary users
            installKioskAppForSecondaryUser(workManager, owner);
        }
    }

    private boolean isKioskAppPreInstalled() {
        try {
            mContext.getPackageManager().getPackageInfo(
                    Futures.getUnchecked(SetupParametersClient.getInstance().getKioskPackage()),
                    0 /* flags */);
            return true;
        } catch (NameNotFoundException e) {
            LogUtil.i(TAG, "Creditor app is not pre-installed");
            return false;
        }
    }

    @VisibleForTesting
    void installKioskAppFromPlay(WorkManager workManager, LifecycleOwner owner,
            Class<? extends ListenableWorker> playInstallTaskClass) {
        LogUtil.v(TAG, "Installing kiosk app from play");

        final SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        final String packageName = Futures.getUnchecked(
                setupParametersClient.getKioskPackage());
        final OneTimeWorkRequest playInstallPackageTask =
                new OneTimeWorkRequest.Builder(playInstallTaskClass).setInputData(
                        new Data.Builder()
                                .putString(EXTRA_KIOSK_PACKAGE, packageName)
                                .build()).build();
        final OneTimeWorkRequest verifyInstallPackageTask =
                new OneTimeWorkRequest.Builder(VerifyPackageTask.class).setInputData(
                        new Data.Builder()
                                .putBoolean(KEY_KIOSK_APP_INSTALLED, /* value= */ true)
                                .putString(EXTRA_KIOSK_PACKAGE, packageName)
                                .putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, Futures.getUnchecked(
                                        setupParametersClient.getKioskSignatureChecksum()))
                                .build()).build();
        workManager.beginUniqueWork(
                        SETUP_PLAY_INSTALL_TASKS_NAME,
                        ExistingWorkPolicy.KEEP,
                        playInstallPackageTask)
                .then(verifyInstallPackageTask)
                .enqueue();

        workManager
                .getWorkInfosForUniqueWorkLiveData(SETUP_PLAY_INSTALL_TASKS_NAME)
                .observe(
                        owner,
                        workInfo -> {
                            if (areAllTasksSucceeded(workInfo)) {
                                setupFlowTaskSuccessCallbackHandler();
                            } else if (isAtLeastOneTaskFailedOrCancelled(workInfo)) {
                                installKioskAppFromURL(workManager, owner);
                            }
                        });
    }

    @VisibleForTesting
    void installKioskAppFromURL(WorkManager workManager, LifecycleOwner owner) {
        LogUtil.v(TAG, "Installing kiosk app from URL");
        final String kioskPackage = Futures.getUnchecked(
                SetupParametersClient.getInstance().getKioskPackage());
        final String kioskSignatureChecksum = Futures.getUnchecked(
                SetupParametersClient.getInstance().getKioskSignatureChecksum());
        final String kioskDownloadUrl = Futures.getUnchecked(
                SetupParametersClient.getInstance().getKioskDownloadUrl());
        OneTimeWorkRequest verifyDownloadPackageTask = new OneTimeWorkRequest.Builder(
                VerifyPackageTask.class).setInputData(
                new Data.Builder()
                        .putBoolean(KEY_KIOSK_APP_INSTALLED, /* value= */ false)
                        .putString(EXTRA_KIOSK_PACKAGE, kioskPackage)
                        .putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, kioskSignatureChecksum)
                        .build()).build();
        OneTimeWorkRequest downloadPackageTask = new OneTimeWorkRequest.Builder(
                DownloadPackageTask.class).setInputData(
                new Data.Builder()
                        .putString(EXTRA_KIOSK_DOWNLOAD_URL, kioskDownloadUrl)
                        .build()).build();
        final OneTimeWorkRequest verifyInstallPackageTask =
                new OneTimeWorkRequest.Builder(VerifyPackageTask.class).setInputData(
                        new Data.Builder()
                                .putBoolean(KEY_KIOSK_APP_INSTALLED, /* value= */ true)
                                .putString(EXTRA_KIOSK_PACKAGE, kioskPackage)
                                .putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, kioskSignatureChecksum)
                                .build()).build();

        workManager
                .beginUniqueWork(SETUP_URL_INSTALL_TASKS_NAME, ExistingWorkPolicy.KEEP,
                        downloadPackageTask)
                .then(verifyDownloadPackageTask)
                .then(new OneTimeWorkRequest.Builder(InstallPackageTask.class).build())
                .then(verifyInstallPackageTask)
                .then(new OneTimeWorkRequest.Builder(CleanupTask.class).build())
                .enqueue();

        workManager
                .getWorkInfosForUniqueWorkLiveData(SETUP_URL_INSTALL_TASKS_NAME)
                .observe(
                        owner,
                        workInfo -> {
                            if (areAllTasksSucceeded(workInfo)) {
                                setupFlowTaskSuccessCallbackHandler();
                            } else if (isAtLeastOneTaskFailedOrCancelled(workInfo)) {
                                setupFlowTaskFailureCallbackHandler(getTaskFailureType(workInfo));
                            }
                        });
    }

    void installKioskAppForSecondaryUser(WorkManager workManager, LifecycleOwner owner) {
        LogUtil.v(TAG, "Installing existing package");
        final SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        final String kioskPackage = Futures.getUnchecked(setupParametersClient.getKioskPackage());
        final OneTimeWorkRequest installExistingPackage = new OneTimeWorkRequest.Builder(
                InstallExistingPackageTask.class).setInputData(
                new Data.Builder().putString(EXTRA_KIOSK_PACKAGE,
                        kioskPackage).build()).build();
        final OneTimeWorkRequest verifyInstalledPackageTask =
                new OneTimeWorkRequest.Builder(VerifyPackageTask.class).setInputData(
                        new Data.Builder()
                                .putBoolean(KEY_KIOSK_APP_INSTALLED, /* value= */ true)
                                .putString(EXTRA_KIOSK_PACKAGE, kioskPackage)
                                .putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, Futures.getUnchecked(
                                        setupParametersClient.getKioskSignatureChecksum()))
                                .build()).build();
        workManager.beginUniqueWork(
                        SETUP_INSTALL_EXISTING_PACKAGE_TASK,
                        ExistingWorkPolicy.KEEP,
                        installExistingPackage)
                .then(verifyInstalledPackageTask)
                .enqueue();
        workManager.getWorkInfosForUniqueWorkLiveData(SETUP_INSTALL_EXISTING_PACKAGE_TASK)
                .observe(owner, workInfo -> {
                    if (areAllTasksSucceeded(workInfo)) {
                        setupFlowTaskSuccessCallbackHandler();
                    } else if (isAtLeastOneTaskFailedOrCancelled(workInfo)) {
                        setupFlowTaskFailureCallbackHandler(getTaskFailureType(workInfo));
                    }
                });
    }

    @VisibleForTesting
    void verifyPreInstalledPackage(WorkManager workManager, LifecycleOwner owner) {
        LogUtil.v(TAG, "Verifying pre-installed package");

        final SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        final OneTimeWorkRequest verifyInstallPackageTask =
                new OneTimeWorkRequest.Builder(VerifyPackageTask.class).setInputData(
                        new Data.Builder()
                                .putBoolean(KEY_KIOSK_APP_INSTALLED, /* value= */ true)
                                .putString(EXTRA_KIOSK_PACKAGE, Futures.getUnchecked(
                                        setupParametersClient.getKioskPackage()))
                                .putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, Futures.getUnchecked(
                                        setupParametersClient.getKioskSignatureChecksum()))
                                .build()).build();
        workManager.enqueueUniqueWork(
                SETUP_VERIFY_PRE_INSTALLED_PACKAGE_TASK,
                ExistingWorkPolicy.KEEP,
                verifyInstallPackageTask);

        workManager
                .getWorkInfoByIdLiveData(verifyInstallPackageTask.getId())
                .observe(
                        owner,
                        workInfo -> {
                            State state = workInfo.getState();
                            if (state == SUCCEEDED) {
                                setupFlowTaskSuccessCallbackHandler();
                            } else if (state == FAILED || state == CANCELLED) {
                                setupFlowTaskFailureCallbackHandler(
                                        FailureType.PACKAGE_DOES_NOT_EXIST);
                            }
                        });
    }

    @Override
    public void finishSetup() {
        try {
            if (mCurrentSetupState == SetupStatus.SETUP_FINISHED) {
                mStateController.setNextStateForEvent(DeviceEvent.SETUP_COMPLETE);
                if (!mPolicyController.launchActivityInLockedMode()) {
                    LogUtil.w(TAG, "Failed to launch kiosk activity after setup");
                }
                return;
            }
            mStateController.setNextStateForEvent(DeviceEvent.SETUP_FAILURE);
            LogUtil.e(TAG, "Setup failed");
        } catch (StateTransitionException e) {
            LogUtil.e(TAG, "State transition failed!", e);
        }

        if (Futures.getUnchecked(SetupParametersClient.getInstance().isProvisionMandatory())) {
            mPolicyController.wipeData();
        }
    }

    private void setupFlowTaskSuccessCallbackHandler() {
        setupFlowTaskCallbackHandler(true, /* Ignored parameter */ FailureType.SETUP_FAILED);
    }

    private void setupFlowTaskFailureCallbackHandler(
            @FailureType int failReason) {
        setupFlowTaskCallbackHandler(false, failReason);
    }

    /**
     * Handles the setup result and invokes registered {@link SetupUpdatesCallbacks}.
     *
     * @param result     true if the setup succeed, otherwise false
     * @param failReason why the setup failed, the value will be ignored if {@code result} is true
     */
    @VisibleForTesting
    void setupFlowTaskCallbackHandler(
            boolean result, @FailureType int failReason) {

        try {
            mStateController.setNextStateForEvent(
                    result ? DeviceEvent.SETUP_SUCCESS : DeviceEvent.SETUP_FAILURE);
        } catch (StateTransitionException e) {
            LogUtil.e(TAG, "Device state inconsistent, aborting setup", e);
            result = false;
            failReason = FailureType.SETUP_FAILED;
        }


        if (result) {
            LogUtil.i(TAG, "Handling successful setup");
            mCurrentSetupState = SetupStatus.SETUP_FINISHED;
            synchronized (mCallbacks) {
                for (int i = 0, cbSize = mCallbacks.size(); i < cbSize; i++) {
                    mCallbacks.get(i).setupCompleted();
                }
            }
        } else {
            LogUtil.i(TAG, "Handling failed setup");
            mCurrentSetupState = SetupStatus.SETUP_FAILED;
            synchronized (mCallbacks) {
                for (int i = 0, cbSize = mCallbacks.size(); i < cbSize; i++) {
                    mCallbacks.get(i).setupFailed(failReason);
                }
            }
        }
    }

    @VisibleForTesting
    @FailureType
    static int transformErrorCodeToFailureType(@AbstractTask.ErrorCode int errorCode) {
        int failReason = FailureType.SETUP_FAILED;
        if (errorCode <= ERROR_CODE_TOO_MANY_REDIRECTS
                && errorCode >= ERROR_CODE_EMPTY_DOWNLOAD_URL) {
            failReason = DOWNLOAD_FAILED;
        } else if (errorCode <= ERROR_CODE_PACKAGE_HAS_MULTIPLE_SIGNERS
                && errorCode >= ERROR_CODE_NO_PACKAGE_INFO) {
            failReason = VERIFICATION_FAILED;
        } else if (errorCode <= ERROR_CODE_GET_PENDING_INTENT_FAILED
                && errorCode >= ERROR_CODE_CREATE_LOCAL_FILE_FAILED) {
            failReason = INSTALL_FAILED;
        } else if (errorCode == ERROR_CODE_DELETE_APK_FAILED) {
            failReason = DELETE_PACKAGE_FAILED;
        } else if (errorCode == ERROR_CODE_NO_PACKAGE_NAME) {
            failReason = INSTALL_EXISTING_FAILED;
        }
        return failReason;
    }

    private static boolean areAllTasksSucceeded(List<WorkInfo> workInfoList) {
        for (WorkInfo workInfo : workInfoList) {
            if (workInfo.getState() != SUCCEEDED) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAtLeastOneTaskFailedOrCancelled(List<WorkInfo> workInfoList) {
        for (WorkInfo workInfo : workInfoList) {
            State state = workInfo.getState();
            if (state == FAILED || state == CANCELLED) {
                return true;
            }
        }
        return false;
    }

    @FailureType
    private static int getTaskFailureType(List<WorkInfo> workInfoList) {
        for (WorkInfo workInfo : workInfoList) {
            int errorCode = workInfo.getOutputData().getInt(TASK_RESULT_ERROR_CODE_KEY, -1);
            if (errorCode != -1) {
                return transformErrorCodeToFailureType(errorCode);
            }
        }
        return FailureType.SETUP_FAILED;
    }
}
