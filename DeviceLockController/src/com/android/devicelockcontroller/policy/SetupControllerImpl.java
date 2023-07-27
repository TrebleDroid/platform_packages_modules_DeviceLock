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

import static com.android.devicelockcontroller.DeviceLockControllerScheduler.RESET_DEVICE_IN_TWO_MINUTES;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason.INSTALL_FAILED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent.PROVISION_PAUSE;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.common.DeviceLockConstants.SetupFailureReason;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.provision.worker.PauseProvisioningWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller managing communication between setup tasks and UI layer.
 */
public final class SetupControllerImpl implements SetupController {

    private static final String SETUP_PLAY_INSTALL_TASKS_NAME =
            "devicelock_setup_play_install_tasks";
    public static final String TAG = "SetupController";

    private final List<SetupUpdatesCallbacks> mCallbacks = new ArrayList<>();
    @SetupStatus
    private int mCurrentSetupState;
    private final Context mContext;
    private final DevicePolicyController mPolicyController;
    private final DeviceStateController mStateController;
    private SetupUpdatesCallbacks mReportStateCallbacks;

    public SetupControllerImpl(
            Context context,
            DeviceStateController stateController,
            DevicePolicyController policyController) {
        mContext = context;
        mStateController = stateController;
        mPolicyController = policyController;
        int state = stateController.getState();
        if (state == DeviceState.PROVISION_IN_PROGRESS
                || state == DeviceState.PROVISION_PAUSED
                || state == DeviceState.UNPROVISIONED
                || state == DeviceState.PSEUDO_UNLOCKED
                || state == DeviceState.PSEUDO_LOCKED) {
            mCurrentSetupState = SetupStatus.SETUP_NOT_STARTED;
        } else if (state == DeviceState.PROVISION_FAILED) {
            mCurrentSetupState = SetupStatus.SETUP_FAILED;
        } else {
            mCurrentSetupState = SetupStatus.SETUP_FINISHED;
        }
        LogUtil.v(TAG,
                String.format(Locale.US, "Setup started with state = %d", mCurrentSetupState));
    }

    @Override
    public void delaySetup() {
        Futures.addCallback(
                Futures.transformAsync(
                        GlobalParametersClient.getInstance().setProvisionForced(true),
                        unused -> mStateController.setNextStateForEvent(PROVISION_PAUSE),
                        MoreExecutors.directExecutor()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Integer newState) {
                        if (newState == DeviceState.PROVISION_PAUSED) {
                            WorkManager workManager = WorkManager.getInstance(mContext);
                            PauseProvisioningWorker.reportProvisionPausedByUser(workManager);
                            new DeviceLockControllerScheduler(
                                    mContext).scheduleResumeProvisionAlarm();
                        } else {
                            onFailure(new IllegalArgumentException(
                                    "New state should not be: " + newState));
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to delay setup", t);
                    }
                }, MoreExecutors.directExecutor());
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
        mReportStateCallbacks =
                ReportDeviceProvisionStateWorker.getSetupUpdatesCallbacks(workManager);
        mCallbacks.add(mReportStateCallbacks);
        return Futures.transformAsync(isKioskAppPreInstalled(),
                isPreinstalled -> {
                    if (isPreinstalled) {
                        setupFlowTaskSuccessCallbackHandler();
                        return Futures.immediateVoidFuture();
                    } else {
                        final Class<? extends ListenableWorker> playInstallTaskClass =
                                ((DeviceLockControllerApplication) mContext.getApplicationContext())
                                        .getPlayInstallPackageTaskClass();
                        if (playInstallTaskClass != null) {
                            return installKioskAppFromPlay(workManager, owner,
                                    playInstallTaskClass);
                        } else {
                            setupFlowTaskFailureCallbackHandler(INSTALL_FAILED);
                            return Futures.immediateFailedFuture(
                                    new IllegalStateException("Kiosk app installation failed"));
                        }
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
                                LogUtil.i(TAG, "Kiosk app is pre-installed");
                                return true;
                            } catch (NameNotFoundException e) {
                                LogUtil.i(TAG, "Kiosk app is not pre-installed");
                                return false;
                            }
                        }, MoreExecutors.directExecutor());
    }

    @VisibleForTesting
    ListenableFuture<Void> installKioskAppFromPlay(WorkManager workManager, LifecycleOwner owner,
            Class<? extends ListenableWorker> playInstallTaskClass) {

        final SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        final ListenableFuture<String> getPackageNameTask = setupParametersClient.getKioskPackage();

        return Futures.whenAllSucceed(getPackageNameTask)
                .call(() -> {
                    LogUtil.v(TAG, "Installing kiosk app from play");

                    final String kioskPackageName = Futures.getDone(getPackageNameTask);

                    final OneTimeWorkRequest playInstallPackageTask =
                            getPlayInstallPackageTask(playInstallTaskClass, kioskPackageName);
                    workManager.enqueueUniqueWork(SETUP_PLAY_INSTALL_TASKS_NAME,
                            ExistingWorkPolicy.KEEP, playInstallPackageTask);
                    final LiveData status =
                            workManager.getWorkInfoByIdLiveData(playInstallPackageTask.getId());
                    status.observe(owner, new Observer<WorkInfo>() {
                        @Override
                        public void onChanged(@Nullable WorkInfo workInfo) {
                            if (workInfo != null) {
                                final WorkInfo.State state = workInfo.getState();
                                if (state == SUCCEEDED) {
                                    setupFlowTaskSuccessCallbackHandler();
                                } else if (state == FAILED || state == CANCELLED) {
                                    setupFlowTaskFailureCallbackHandler(
                                            SetupFailureReason.INSTALL_FAILED);
                                }
                            }
                        }
                    });

                    return null;
                }, mContext.getMainExecutor());
    }

    @NonNull
    private static OneTimeWorkRequest getPlayInstallPackageTask(
            Class<? extends ListenableWorker> playInstallTaskClass, String kioskPackageName) {
        return new OneTimeWorkRequest.Builder(
                playInstallTaskClass).setInputData(
                new Data.Builder().putString(
                        EXTRA_KIOSK_PACKAGE, kioskPackageName).build()).build();
    }

    @VisibleForTesting
    ListenableFuture<Void> finishSetup() {
        mCallbacks.remove(mReportStateCallbacks);
        if (mCurrentSetupState == SetupStatus.SETUP_FINISHED) {
            return Futures.transform(
                    mStateController.setNextStateForEvent(DeviceEvent.PROVISION_KIOSK),
                    (Integer unused) -> null,
                    MoreExecutors.directExecutor());
        } else if (mCurrentSetupState == SetupStatus.SETUP_FAILED) {
            return Futures.transform(
                    SetupParametersClient.getInstance().isProvisionMandatory(),
                    isMandatory -> {
                        if (isMandatory) {
                            new DeviceLockControllerScheduler(mContext).scheduleResetDeviceAlarm(
                                    Duration.ofMinutes(RESET_DEVICE_IN_TWO_MINUTES));
                        }
                        return null;
                    }, MoreExecutors.directExecutor());
        } else {
            return Futures.immediateFailedFuture(new IllegalStateException(
                    "Can not finish setup when setup state is NOT_STARTED/IN_PROGRESS!"));
        }
    }

    @VisibleForTesting
    void setupFlowTaskSuccessCallbackHandler() {
        setupFlowTaskCallbackHandler(true, /* Ignored parameter */ SetupFailureReason.SETUP_FAILED);
    }

    @VisibleForTesting
    void setupFlowTaskFailureCallbackHandler(@SetupFailureReason int failReason) {
        setupFlowTaskCallbackHandler(false, failReason);
    }

    /**
     * Handles the setup result and invokes registered {@link SetupUpdatesCallbacks}.
     *
     * @param result     true if the setup succeed, otherwise false
     * @param failReason why the setup failed, the value will be ignored if {@code result} is true
     */
    private void setupFlowTaskCallbackHandler(
            boolean result, @SetupFailureReason int failReason) {
        Futures.addCallback(
                Futures.transformAsync(mStateController.setNextStateForEvent(result
                                ? DeviceEvent.PROVISION_SUCCESS : DeviceEvent.PROVISION_FAILURE),
                        input -> {
                            if (result) {
                                LogUtil.i(TAG, "Handling successful setup");
                                mCurrentSetupState = SetupStatus.SETUP_FINISHED;
                                synchronized (mCallbacks) {
                                    ImmutableList<SetupUpdatesCallbacks> callbacks =
                                            ImmutableList.copyOf(mCallbacks);
                                    for (int i = 0, cbSize = callbacks.size(); i < cbSize; i++) {
                                        callbacks.get(i).setupCompleted();
                                    }
                                }
                            } else {
                                LogUtil.i(TAG, "Handling failed setup");
                                mCurrentSetupState = SetupStatus.SETUP_FAILED;
                                synchronized (mCallbacks) {
                                    ImmutableList<SetupUpdatesCallbacks> callbacks =
                                            ImmutableList.copyOf(mCallbacks);
                                    for (int i = 0, cbSize = callbacks.size(); i < cbSize; i++) {
                                        callbacks.get(i).setupFailed(failReason);
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
}
