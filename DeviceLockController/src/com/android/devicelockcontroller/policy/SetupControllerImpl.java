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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LifecycleOwner;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.SetupController.SetupUpdatesCallbacks.FailureType;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Controller managing communication between setup tasks and UI layer. */
public final class SetupControllerImpl implements SetupController {

    private static final String SETUP_URL_INSTALL_TASKS_NAME = "devicelock_setup_url_install_tasks";
    private static final String SETUP_PLAY_INSTALL_TASKS_NAME =
            "devicelock_setup_play_install_tasks";
    public static final String SETUP_PRE_INSTALLED_PACKAGE_TASK =
            "devicelock_setup_pre_installed_package_task";
    public static final String TAG = "SetupController";
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
    public ListenableFuture<Void> startSetupFlow(LifecycleOwner owner) {
        LogUtil.v(TAG, "Trigger setup flow");
        WorkManager workManager = WorkManager.getInstance(mContext);
        return Futures.transformAsync(isKioskAppPreInstalled(),
                isPreinstalled -> {
                    if (isPreinstalled) {
                        return assignRoleToPreinstalledPackage(workManager, owner);
                    } else if (mContext.getUser().isSystem()) {
                        final Class<? extends ListenableWorker> playInstallTaskClass =
                                ((DeviceLockControllerApplication) mContext.getApplicationContext())
                                        .getPlayInstallPackageTaskClass();
                        if (playInstallTaskClass != null) {
                            return installKioskAppFromPlay(workManager, owner,
                                    playInstallTaskClass);
                        } else {
                            return installKioskAppFromURL(workManager, owner);
                        }
                    } else {
                        return installKioskAppForSecondaryUser(workManager, owner);
                    }
                }, MoreExecutors.directExecutor());
    }

    @VisibleForTesting
    ListenableFuture<Boolean> isKioskAppPreInstalled() {
        return !Build.isDebuggable() ? Futures.immediateFuture(false)
                : Futures.transform(SetupParametersClient.getInstance().getKioskPackage(),
                        packageName -> {
                            try {
                                mContext.getPackageManager().getPackageInfo(
                                        packageName,
                                        ApplicationInfo.FLAG_INSTALLED);
                                LogUtil.i(TAG, "Creditor app is pre-installed");
                                return true;
                            } catch (NameNotFoundException e) {
                                LogUtil.i(TAG, "Creditor app is not pre-installed");
                                return false;
                            }
                        }, MoreExecutors.directExecutor());
    }

    @VisibleForTesting
    ListenableFuture<Void> installKioskAppFromPlay(WorkManager workManager, LifecycleOwner owner,
            Class<? extends ListenableWorker> playInstallTaskClass) {

        final SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        final ListenableFuture<String> getPackageNameTask = setupParametersClient.getKioskPackage();
        final ListenableFuture<String> getKioskSignatureChecksumTask =
                setupParametersClient.getKioskSignatureChecksum();

        return Futures.whenAllSucceed(getPackageNameTask, getKioskSignatureChecksumTask)
                .call(() -> {
                    LogUtil.v(TAG, "Installing kiosk app from play");

                    final String kioskPackageName = Futures.getDone(getPackageNameTask);

                    final OneTimeWorkRequest playInstallPackageTask =
                            getPlayInstallPackageTask(playInstallTaskClass, kioskPackageName);
                    final OneTimeWorkRequest verifyInstallPackageTask =
                            getVerifyInstalledPackageTask(kioskPackageName,
                                    Futures.getDone(getKioskSignatureChecksumTask));
                    final OneTimeWorkRequest addFinancedDeviceKioskRoleTask =
                            getAddFinancedDeviceKioskRoleTask(kioskPackageName);
                    createAndRunTasks(workManager, owner, SETUP_PLAY_INSTALL_TASKS_NAME,
                            playInstallPackageTask, verifyInstallPackageTask,
                            addFinancedDeviceKioskRoleTask);
                    return null;
                }, mContext.getMainExecutor());
    }

    @VisibleForTesting
    ListenableFuture<Void> installKioskAppFromURL(WorkManager workManager, LifecycleOwner owner) {
        LogUtil.v(TAG, "Installing kiosk app from URL");
        final ListenableFuture<String> kioskPackageTask =
                SetupParametersClient.getInstance().getKioskPackage();
        final ListenableFuture<String> kioskSignatureChecksumTask =
                SetupParametersClient.getInstance().getKioskSignatureChecksum();
        final ListenableFuture<String> kioskDownloadUrlTask =
                SetupParametersClient.getInstance().getKioskDownloadUrl();
        return Futures.whenAllSucceed(
                        kioskPackageTask,
                        kioskSignatureChecksumTask,
                        kioskDownloadUrlTask)
                .call(() -> {
                    final String kioskPackageName = Futures.getDone(kioskPackageTask);
                    final OneTimeWorkRequest verifyDownloadPackageTask =
                            getVerifyDownloadPackageTask(kioskPackageName,
                                    Futures.getDone(kioskSignatureChecksumTask));
                    final OneTimeWorkRequest downloadPackageTask =
                            getDownloadPackageTask(Futures.getDone(kioskDownloadUrlTask));
                    final OneTimeWorkRequest verifyInstallPackageTask =
                            getVerifyInstalledPackageTask(kioskPackageName,
                                    Futures.getDone(kioskSignatureChecksumTask));
                    final OneTimeWorkRequest addFinancedDeviceKioskRoleTask =
                            getAddFinancedDeviceKioskRoleTask(kioskPackageName);
                    createAndRunTasks(workManager, owner, SETUP_URL_INSTALL_TASKS_NAME,
                            verifyDownloadPackageTask,
                            downloadPackageTask,
                            verifyInstallPackageTask,
                            addFinancedDeviceKioskRoleTask);
                    return null;
                }, mContext.getMainExecutor());
    }

    ListenableFuture<Void> installKioskAppForSecondaryUser(WorkManager workManager,
            LifecycleOwner owner) {
        LogUtil.v(TAG, "Installing existing package");
        final SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        final ListenableFuture<String> kioskPackageTask = setupParametersClient.getKioskPackage();
        ListenableFuture<String> kioskSignatureChecksumTask =
                setupParametersClient.getKioskSignatureChecksum();
        return Futures.whenAllSucceed(kioskPackageTask, kioskSignatureChecksumTask)
                .call(() -> {
                    final String kioskPackageName = Futures.getDone(kioskPackageTask);

                    createAndRunTasks(workManager, owner, SETUP_INSTALL_EXISTING_PACKAGE_TASK,
                            getInstallExistingPackageTask(Futures.getDone(kioskPackageTask)),
                            getVerifyInstalledPackageTask(
                                    kioskPackageName,
                                    Futures.getDone(kioskSignatureChecksumTask)),
                            getAddFinancedDeviceKioskRoleTask(kioskPackageName));
                    return null;
                }, mContext.getMainExecutor());
    }

    @VisibleForTesting
    ListenableFuture<Void> assignRoleToPreinstalledPackage(WorkManager workManager,
            LifecycleOwner owner) {

        final SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        final ListenableFuture<String> getKioskPackageTask =
                setupParametersClient.getKioskPackage();
        return Futures.transform(getKioskPackageTask,
                kioskPackage -> {
                    LogUtil.v(TAG, "assigning role to pre-installed package");
                    OneTimeWorkRequest addFinancedDeviceKioskRoleTask =
                            getAddFinancedDeviceKioskRoleTask(kioskPackage);
                    createAndRunTasks(workManager, owner,
                            SETUP_PRE_INSTALLED_PACKAGE_TASK,
                            addFinancedDeviceKioskRoleTask);
                    return null;
                }, mContext.getMainExecutor());
    }

    @NonNull
    private static OneTimeWorkRequest getDownloadPackageTask(String kioskDownloadUrl) {
        return new OneTimeWorkRequest.Builder(
                DownloadPackageTask.class).setInputData(
                new Data.Builder()
                        .putString(EXTRA_KIOSK_DOWNLOAD_URL,
                                kioskDownloadUrl)
                        .build()).build();
    }

    @NonNull
    private static OneTimeWorkRequest getVerifyDownloadPackageTask(
            String kioskPackageName, String kioskSignatureChecksum) {
        return new OneTimeWorkRequest.Builder(
                VerifyPackageTask.class).setInputData(
                new Data.Builder()
                        .putBoolean(KEY_KIOSK_APP_INSTALLED, /* value= */ false)
                        .putString(EXTRA_KIOSK_PACKAGE,
                                kioskPackageName)
                        .putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM,
                                kioskSignatureChecksum)
                        .build()).build();
    }

    @NonNull
    private static OneTimeWorkRequest getVerifyInstalledPackageTask(
            String kioskPackageName, String kioskSignatureChecksum) {
        return new OneTimeWorkRequest.Builder(VerifyPackageTask.class).setInputData(
                new Data.Builder()
                        .putBoolean(KEY_KIOSK_APP_INSTALLED, /* value= */ true)
                        .putString(EXTRA_KIOSK_PACKAGE,
                                kioskPackageName)
                        .putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM,
                                kioskSignatureChecksum)
                        .build()).build();
    }

    @NonNull
    private static OneTimeWorkRequest getInstallExistingPackageTask(String kioskPackageName) {
        return new OneTimeWorkRequest.Builder(
                InstallExistingPackageTask.class).setInputData(
                new Data.Builder().putString(EXTRA_KIOSK_PACKAGE,
                        kioskPackageName).build()).build();
    }

    @NonNull
    private static OneTimeWorkRequest getPlayInstallPackageTask(
            Class<? extends ListenableWorker> playInstallTaskClass, String kioskPackageName) {
        return new OneTimeWorkRequest.Builder(
                playInstallTaskClass).setInputData(
                new Data.Builder().putString(
                        EXTRA_KIOSK_PACKAGE, kioskPackageName).build()).build();
    }

    @NonNull
    private static OneTimeWorkRequest getAddFinancedDeviceKioskRoleTask(String kioskPackageName) {
        return new OneTimeWorkRequest.Builder(AddFinancedDeviceKioskRoleTask.class)
                .setInputData(new Data.Builder().putString(EXTRA_KIOSK_PACKAGE,
                        kioskPackageName).build()).build();
    }

    @MainThread
    private void createAndRunTasks(WorkManager workManager, LifecycleOwner owner,
            String uniqueWorkName, OneTimeWorkRequest... works) {

        WorkContinuation workChain = workManager.beginUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                works[0]);
        for (int i = 1, len = works.length; i < len; i++) {
            workChain = workChain.then(works[i]);
        }
        workChain.enqueue();
        workManager.getWorkInfosForUniqueWorkLiveData(
                        uniqueWorkName)
                .observe(owner, workInfo -> {
                    if (areAllTasksSucceeded(workInfo)) {
                        setupFlowTaskSuccessCallbackHandler();
                    } else if (isAtLeastOneTaskFailedOrCancelled(workInfo)) {
                        setupFlowTaskFailureCallbackHandler(getTaskFailureType(workInfo));
                    }
                });
    }

    @VisibleForTesting
    ListenableFuture<Void> finishSetup() {
        if (mCurrentSetupState == SetupStatus.SETUP_FINISHED) {
            return Futures.transformAsync(mStateController.setNextStateForEvent(
                            DeviceEvent.SETUP_COMPLETE),
                    empty -> Futures.transform(mPolicyController.launchActivityInLockedMode(),
                            isLaunched -> {
                                if (!isLaunched) {
                                    throw new IllegalStateException(
                                            "Launching kiosk setup activity failed!");
                                }
                                return null;
                            }, MoreExecutors.directExecutor()), MoreExecutors.directExecutor());
        } else {
            return Futures.transform(
                    SetupParametersClient.getInstance().isProvisionMandatory(),
                    isMandatory -> {
                        if (isMandatory) mPolicyController.wipeData();
                        return null;
                    }, MoreExecutors.directExecutor());
        }
    }

    @VisibleForTesting
    void setupFlowTaskSuccessCallbackHandler() {
        setupFlowTaskCallbackHandler(true, /* Ignored parameter */ FailureType.SETUP_FAILED);
    }

    @VisibleForTesting
    void setupFlowTaskFailureCallbackHandler(@FailureType int failReason) {
        setupFlowTaskCallbackHandler(false, failReason);
    }

    /**
     * Handles the setup result and invokes registered {@link SetupUpdatesCallbacks}.
     *
     * @param result     true if the setup succeed, otherwise false
     * @param failReason why the setup failed, the value will be ignored if {@code result} is true
     */
    private void setupFlowTaskCallbackHandler(
            boolean result, @FailureType int failReason) {
        Futures.addCallback(
                Futures.transformAsync(mStateController.setNextStateForEvent(
                                result ? DeviceEvent.SETUP_SUCCESS : DeviceEvent.SETUP_FAILURE),
                        input -> {
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
                            return finishSetup();
                        }, MoreExecutors.directExecutor()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.v(TAG, "Successfully handled setup callbacks");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to handle setup callbacks!", t);
                    }
                }, MoreExecutors.directExecutor());
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
