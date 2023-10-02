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

package com.android.devicelockcontroller.debug;

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.StringDef;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.FcmRegistrationTokenProvider;
import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.provision.worker.PauseProvisioningWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.receivers.NextProvisionFailedStepReceiver;
import com.android.devicelockcontroller.receivers.ResetDeviceReceiver;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.annotation.Retention;
import java.util.Objects;

/**
 * A {@link BroadcastReceiver} that can handle reset, lock, unlock command.
 * <p>
 * Note:
 * Reboot device are {@link DeviceLockCommandReceiver#onReceive(Context, Intent)} has been called to
 * take effect.
 */
public final class DeviceLockCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "DeviceLockCommandReceiver";
    private static final String EXTRA_COMMAND = "command";

    @Retention(SOURCE)
    @StringDef({
            Commands.RESET,
            Commands.LOCK,
            Commands.UNLOCK,
            Commands.CHECK_IN,
            Commands.CLEAR,
            Commands.DUMP,
            Commands.FCM,
    })
    private @interface Commands {
        String RESET = "reset";
        String LOCK = "lock";
        String UNLOCK = "unlock";
        String CHECK_IN = "check-in";
        String CLEAR = "clear";
        String DUMP = "dump";
        String FCM = "fcm";
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.isDebuggable()) {
            throw new SecurityException("This should never be run in production build!");
        }

        if (!TextUtils.equals(intent.getComponent().getClassName(), getClass().getName())) {
            throw new IllegalArgumentException("Intent does not match this class!");
        }

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();
        if (isUserProfile) {
            LogUtil.w(TAG, "Broadcast should not target user profiles");
            return;
        }

        Context appContext = context.getApplicationContext();

        ProvisionStateController provisionStateController =
                ((PolicyObjectsInterface) appContext).getProvisionStateController();
        DeviceStateController deviceStateController =
                provisionStateController.getDeviceStateController();

        @Commands
        String command = String.valueOf(intent.getStringExtra(EXTRA_COMMAND));
        switch (command) {
            case Commands.RESET:
                forceReset(appContext);
                break;
            case Commands.LOCK:
                Futures.addCallback(deviceStateController.lockDevice(),
                        getSetStateCallBack(LOCKED), MoreExecutors.directExecutor());
                break;
            case Commands.UNLOCK:
                Futures.addCallback(deviceStateController.unlockDevice(),
                        getSetStateCallBack(UNLOCKED), MoreExecutors.directExecutor());
                break;
            case Commands.CLEAR:
                Futures.addCallback(deviceStateController.clearDevice(),
                        getSetStateCallBack(CLEARED), MoreExecutors.directExecutor());
                break;
            case Commands.CHECK_IN:
                tryCheckIn(appContext);
                break;
            case Commands.DUMP:
                dumpStorage(context);
                break;
            case Commands.FCM:
                logFcmToken(appContext);
                break;
            default:
                throw new IllegalArgumentException("Unsupported command: " + command);
        }
    }

    private static void dumpStorage(Context context) {
        Futures.addCallback(
                Futures.transformAsync(SetupParametersClient.getInstance().dump(),
                        unused -> GlobalParametersClient.getInstance().dump(),
                        MoreExecutors.directExecutor()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        UserParameters.dump(context);
                        LogUtil.i(TAG, "Successfully dumped storage");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Error encountered when dumping storage", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    private static void tryCheckIn(Context appContext) {
        if (!appContext.getSystemService(UserManager.class).isSystemUser()) {
            LogUtil.e(TAG, "Only system user can perform a check-in");
            return;
        }
        DeviceLockControllerSchedulerProvider schedulerProvider =
                (DeviceLockControllerSchedulerProvider) appContext;
        DeviceLockControllerScheduler scheduler =
                schedulerProvider.getDeviceLockControllerScheduler();

        Futures.addCallback(GlobalParametersClient.getInstance().isProvisionReady(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Boolean provisioningInfoReady) {
                        if (!provisioningInfoReady) {
                            scheduler.scheduleInitialCheckInWork();
                        } else {
                            LogUtil.e(TAG,
                                    "Can not check in when provisioning info has already been "
                                            + "received. Use the \"reset\" command to reset "
                                            + "DLC first.");
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to determine if provisioning info is ready", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    private static void forceReset(Context context) {
        // Cancel provision works
        LogUtil.d(TAG, "cancelling works");
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelAllWorkByTag(DeviceCheckInWorker.class.getName());
        workManager.cancelAllWorkByTag(PauseProvisioningWorker.class.getName());
        workManager.cancelAllWorkByTag(
                ReportDeviceProvisionStateWorker.class.getName());
        workManager.cancelAllWorkByTag(
                ReportDeviceLockProgramCompleteWorker.class.getName());

        // Cancel All alarms
        AlarmManager alarmManager = Objects.requireNonNull(
                context.getSystemService(AlarmManager.class));
        alarmManager.cancel(
                PendingIntent.getBroadcast(
                        context, /* ignored */ 0,
                        new Intent(context, ResetDeviceReceiver.class),
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        alarmManager.cancel(PendingIntent.getBroadcast(
                context, /* ignored */ 0,
                new Intent(context, NextProvisionFailedStepReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        alarmManager.cancel(PendingIntent.getBroadcast(
                context, /* ignored */ 0,
                new Intent(context, ResumeProvisionReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));

        PolicyObjectsInterface policyObjectsInterface =
                (PolicyObjectsInterface) context.getApplicationContext();
        policyObjectsInterface.destroyObjects();
        UserParameters.setProvisionState(context, PROVISION_SUCCEEDED);
        GlobalParametersClient.getInstance().setDeviceState(CLEARED);
        DevicePolicyController policyController = policyObjectsInterface.getPolicyController();
        ListenableFuture<Void> clearPolicies = Futures.catching(
                policyController.enforceCurrentPolicies(),
                RuntimeException.class, unused -> null,
                MoreExecutors.directExecutor());
        Futures.addCallback(Futures.transformAsync(clearPolicies, unused -> clearStorage(context),
                        MoreExecutors.directExecutor()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.i(TAG, "Reset device state.");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());
    }

    private static void logFcmToken(Context appContext) {
        final ListenableFuture<String> fcmRegistrationToken =
                ((FcmRegistrationTokenProvider) appContext).getFcmRegistrationToken();
        Futures.addCallback(fcmRegistrationToken, new FutureCallback<>() {
            @Override
            public void onSuccess(String token) {
                LogUtil.i(TAG, "FCM Registration Token: " + (token == null ? "Not set" : token));
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Unable to get FCM registration token", t);
            }
        }, MoreExecutors.directExecutor());
    }

    private static FutureCallback<Void> getSetStateCallBack(@DeviceState int state) {

        return new FutureCallback<>() {

            @Override
            public void onSuccess(Void unused) {
                LogUtil.i(TAG, "Successfully set state to: " + state);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Unsuccessfully set state to: " + state, t);
            }
        };
    }

    private static ListenableFuture<Void> clearStorage(Context context) {
        UserParameters.clear(context);
        return Futures.whenAllSucceed(
                        SetupParametersClient.getInstance().clear(),
                        GlobalParametersClient.getInstance().clear())
                .call(() -> {
                    ((PolicyObjectsInterface) context.getApplicationContext()).destroyObjects();
                    return null;
                }, context.getMainExecutor());
    }
}
