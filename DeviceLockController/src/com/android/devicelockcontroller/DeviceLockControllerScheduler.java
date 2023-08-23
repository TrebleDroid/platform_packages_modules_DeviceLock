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

package com.android.devicelockcontroller;

import static com.android.devicelockcontroller.common.DeviceLockConstants.MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.os.SystemProperties;

import androidx.annotation.VisibleForTesting;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.activities.DeviceLockNotificationManager;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.receivers.NextProvisionFailedStepReceiver;
import com.android.devicelockcontroller.receivers.ResetDeviceReceiver;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A class responsible for scheduling delayed work / alarm */
public final class DeviceLockControllerScheduler extends AbstractDeviceLockControllerScheduler {
    private static final String TAG = "DeviceLockControllerScheduler";
    public static final String DEVICE_CHECK_IN_WORK_NAME = "device-check-in";
    private static final String PROVISION_PAUSED_MINUTES_SYS_PROPERTY_KEY =
            "debug.devicelock.paused-minutes";
    // The default minute value of the duration that provision UI can be paused.
    @VisibleForTesting
    static final int PROVISION_PAUSED_MINUTES_DEFAULT = 60;
    // The default minute value of the interval between steps of provision failed flow.
    @VisibleForTesting
    static final long PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES = TimeUnit.DAYS.toMinutes(1);
    private static final String KEY_PROVISION_REPORT_INTERVAL_MINUTES =
            "devicelock.provision.report-interval-minutes";
    private final Context mContext;
    private static final int CHECK_IN_INTERVAL_MINUTE = 60;
    private final Clock mClock;
    private final Executor mExecutor;
    private ProvisionStateController mUserStateController;


    public DeviceLockControllerScheduler(Context context) {
        this(context, Clock.systemUTC(), Executors.newSingleThreadExecutor());
    }

    @VisibleForTesting
    DeviceLockControllerScheduler(Context context, Clock clock, Executor executor) {
        mContext = context;
        PolicyObjectsInterface policyObjectsInterface =
                (PolicyObjectsInterface) mContext.getApplicationContext();
        mUserStateController = policyObjectsInterface.getProvisionStateController();
        mClock = clock;
        mExecutor = executor;
    }

    @Override
    public void correctExpectedToRunTime(Duration delta) {
        Futures.addCallback(mUserStateController.getState(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(@ProvisionState Integer currentState) {
                        UserParameters.setBootTimeMillis(mContext,
                                UserParameters.getBootTimeMillis(mContext) + delta.toMillis());
                        if (currentState == UNPROVISIONED) {
                            long before = UserParameters.getNextCheckInTimeMillis(mContext);
                            if (before > 0) {
                                UserParameters.setNextCheckInTimeMillis(mContext,
                                        before + delta.toMillis());
                            }
                        } else if (currentState == PROVISION_PAUSED) {
                            long before = UserParameters.getResumeProvisionTimeMillis(mContext);
                            if (before > 0) {
                                UserParameters.setResumeProvisionTimeMillis(mContext,
                                        before + delta.toMillis());
                            }
                        } else if (currentState == PROVISION_FAILED) {
                            long before = UserParameters.getNextProvisionFailedStepTimeMills(
                                    mContext);
                            if (before > 0) {
                                UserParameters.setNextProvisionFailedStepTimeMills(mContext,
                                        before + delta.toMillis());
                            }
                            before = UserParameters.getResetDeviceTimeMillis(mContext);
                            if (before > 0) {
                                UserParameters.setResetDeviceTImeMillis(mContext,
                                        before + delta.toMillis());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, mExecutor);
    }

    @Override
    public void scheduleResumeProvisionAlarm() {
        Duration delay = Duration.ofMinutes(PROVISION_PAUSED_MINUTES_DEFAULT);
        if (Build.isDebuggable()) {
            delay = Duration.ofMinutes(
                    SystemProperties.getInt(PROVISION_PAUSED_MINUTES_SYS_PROPERTY_KEY,
                            PROVISION_PAUSED_MINUTES_DEFAULT));
        }
        LogUtil.i(TAG, "Scheduling resume provision work with delay: " + delay);
        scheduleResumeProvisionAlarm(delay);
        Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
        UserParameters.setResumeProvisionTimeMillis(mContext, whenExpectedToRun.toEpochMilli());
    }

    @Override
    public void rescheduleResumeProvisionAlarmIfNeeded() {
        long resumeProvisionTimeMillis = UserParameters.getResumeProvisionTimeMillis(mContext);
        if (resumeProvisionTimeMillis > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(resumeProvisionTimeMillis));
            scheduleResumeProvisionAlarm(delay);
        }
    }

    @Override
    public void scheduleInitialCheckInWork() {
        LogUtil.i(TAG, "Scheduling initial check-in work");
        enqueueCheckInWorkRequest(/* isExpedited= */ true, Duration.ZERO);
        UserParameters.initialCheckInScheduled(mContext);
    }

    @Override
    public void scheduleRetryCheckInWork(Duration delay) {
        LogUtil.i(TAG, "Scheduling retry check-in work with delay: " + delay);
        enqueueCheckInWorkRequest(/* isExpedited= */ false, delay);
        Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
        UserParameters.setNextCheckInTimeMillis(mContext, whenExpectedToRun.toEpochMilli());
    }

    @Override
    public void rescheduleRetryCheckInWorkIfNeeded() {
        long nextCheckInTimeMillis = UserParameters.getNextCheckInTimeMillis(mContext);
        if (nextCheckInTimeMillis > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(nextCheckInTimeMillis));
            LogUtil.i(TAG, "Rescheduling retry check-in work with delay: " + delay);
            enqueueCheckInWorkRequest(/* isExpedited= */ false, delay);
        }
    }

    @Override
    public void scheduleNextProvisionFailedStepAlarm() {
        long lastTimestamp = UserParameters.getNextProvisionFailedStepTimeMills(mContext);
        long nextTimestamp;
        if (lastTimestamp == 0) {
            // This is the first provision failed step, trigger the alarm
            // immediately.
            nextTimestamp = Instant.now(mClock).toEpochMilli();
            scheduleNextProvisionFailedStepAlarm(Duration.ZERO);
        } else {
            // This is not the first provision failed step, schedule with
            // default delay.
            Duration delay = Duration.ofMinutes(
                    PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES);
            if (Build.isDebuggable()) {
                long minutes = SystemProperties.getLong(
                        KEY_PROVISION_REPORT_INTERVAL_MINUTES,
                        PROVISION_STATE_REPORT_INTERVAL_DEFAULT_MINUTES);
                delay = Duration.ofMinutes(minutes);
            }
            nextTimestamp = lastTimestamp + delay.toMillis();
            scheduleNextProvisionFailedStepAlarm(
                    Duration.between(Instant.now(mClock),
                            Instant.ofEpochMilli(nextTimestamp)));
        }
        UserParameters.setNextProvisionFailedStepTimeMills(mContext, nextTimestamp);
    }

    @Override
    public void rescheduleNextProvisionFailedStepAlarmIfNeeded() {
        long timestamp = UserParameters.getNextProvisionFailedStepTimeMills(mContext);
        if (timestamp > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(timestamp));
            scheduleNextProvisionFailedStepAlarm(delay);
        }
    }

    @Override
    public void scheduleResetDeviceAlarm() {
        Duration delay = Duration.ofMinutes(NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE);
        if (Build.isDebuggable()) {
            delay = Duration.ofMinutes(
                    SystemProperties.getInt("devicelock.provision.reset-device-minutes",
                            NON_MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
        }
        scheduleResetDeviceAlarm(delay);
    }

    @Override
    public void scheduleMandatoryResetDeviceAlarm() {
        Duration delay = Duration.ofMinutes(MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE);
        if (Build.isDebuggable()) {
            delay = Duration.ofMinutes(
                    SystemProperties.getInt("devicelock.provision.mandatory-reset-device-minutes",
                            MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
        }
        scheduleResetDeviceAlarm(delay);
    }

    private void scheduleResetDeviceAlarm(Duration delay) {
        scheduleResetDeviceAlarmInternal(delay);
        Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
        DeviceLockNotificationManager.sendDeviceResetTimerNotification(mContext,
                SystemClock.elapsedRealtime() + delay.toMillis());
        UserParameters.setResetDeviceTImeMillis(mContext, whenExpectedToRun.toEpochMilli());
    }

    @Override
    public void rescheduleResetDeviceAlarmIfNeeded() {
        long timestamp = UserParameters.getResetDeviceTimeMillis(mContext);
        if (timestamp > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(timestamp));
            scheduleResetDeviceAlarmInternal(delay);
        }
    }

    private void enqueueCheckInWorkRequest(boolean isExpedited, Duration delay) {
        OneTimeWorkRequest.Builder builder =
                new OneTimeWorkRequest.Builder(DeviceCheckInWorker.class)
                        .setConstraints(
                                new Constraints.Builder().setRequiredNetworkType(
                                        NetworkType.CONNECTED).build())
                        .setInitialDelay(delay)
                        .setBackoffCriteria(BackoffPolicy.LINEAR,
                                Duration.ofHours(CHECK_IN_INTERVAL_MINUTE));
        if (isExpedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        WorkManager.getInstance(mContext).enqueueUniqueWork(DEVICE_CHECK_IN_WORK_NAME,
                ExistingWorkPolicy.REPLACE, builder.build());
    }

    private void scheduleResumeProvisionAlarm(Duration delay) {
        scheduleAlarmWithPendingIntentAndDelay(ResumeProvisionReceiver.class, delay);
    }

    private void scheduleNextProvisionFailedStepAlarm(Duration delay) {
        scheduleAlarmWithPendingIntentAndDelay(NextProvisionFailedStepReceiver.class, delay);
    }

    private void scheduleResetDeviceAlarmInternal(Duration delay) {
        scheduleAlarmWithPendingIntentAndDelay(ResetDeviceReceiver.class, delay);
    }

    private void scheduleAlarmWithPendingIntentAndDelay(
            Class<? extends BroadcastReceiver> receiverClass, Duration delay) {
        long countDownBase = SystemClock.elapsedRealtime() + delay.toMillis();
        AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, /* ignored */ 0,
                new Intent(mContext, receiverClass),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        Objects.requireNonNull(alarmManager).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                countDownBase,
                pendingIntent);
    }
}
