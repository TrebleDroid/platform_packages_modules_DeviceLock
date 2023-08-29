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

package com.android.devicelockcontroller.schedule;

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

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.activities.DeviceLockNotificationManager;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.receivers.NextProvisionFailedStepReceiver;
import com.android.devicelockcontroller.receivers.ResetDeviceReceiver;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;
import com.android.devicelockcontroller.util.ThreadUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link DeviceLockControllerScheduler}.
 * WARNING: Do not create an instance directly, instead you should retrieve it using the
 * {@link DeviceLockControllerApplication#getDeviceLockControllerScheduler()} API.
 */
public final class DeviceLockControllerSchedulerImpl implements DeviceLockControllerScheduler {
    private static final String TAG = "DeviceLockControllerSchedulerImpl";
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
    private final Executor mSequentialExecutor;
    private final ProvisionStateController mProvisionStateController;

    public DeviceLockControllerSchedulerImpl(Context context,
            ProvisionStateController provisionStateController) {
        this(context, Clock.systemUTC(), provisionStateController);
    }

    @VisibleForTesting
    DeviceLockControllerSchedulerImpl(Context context, Clock clock,
            ProvisionStateController provisionStateController) {
        mContext = context;
        mProvisionStateController = provisionStateController;
        mClock = clock;
        mSequentialExecutor = ThreadUtils.getSequentialSchedulerExecutor();
    }

    @Override
    public void notifyTimeChanged() {
        Futures.addCallback(mProvisionStateController.getState(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(@ProvisionState Integer currentState) {
                        correctStoredTime(currentState);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, mSequentialExecutor);
    }

    /**
     * Correct the stored time for when a scheduled work/alarm should execute based on the
     * difference between current time and stored time.
     *
     * @param currentState The current {@link ProvisionState} used to determine which work/alarm may
     *                     be possibly scheduled.
     */
    @VisibleForTesting
    void correctStoredTime(@ProvisionState Integer currentState) {
        long bootTimestamp = UserParameters.getBootTimeMillis(mContext);
        long delta =
                mClock.millis() - (bootTimestamp + SystemClock.elapsedRealtime());
        UserParameters.setBootTimeMillis(mContext,
                UserParameters.getBootTimeMillis(mContext) + delta);
        if (currentState == UNPROVISIONED) {
            long before = UserParameters.getNextCheckInTimeMillis(mContext);
            if (before > 0) {
                UserParameters.setNextCheckInTimeMillis(mContext,
                        before + delta);
            }
            // We have to reschedule (update) the check-in work, because, otherwise, if device
            // reboots, WorkManager will reschedule the work based on the changed system clock,
            // which will result in inaccurate schedule. (see b/285221785)
            rescheduleRetryCheckInWork();
        } else if (currentState == PROVISION_PAUSED) {
            long before = UserParameters.getResumeProvisionTimeMillis(mContext);
            if (before > 0) {
                UserParameters.setResumeProvisionTimeMillis(mContext,
                        before + delta);
            }
        } else if (currentState == PROVISION_FAILED) {
            long before = UserParameters.getNextProvisionFailedStepTimeMills(
                    mContext);
            if (before > 0) {
                UserParameters.setNextProvisionFailedStepTimeMills(mContext,
                        before + delta);
            }
            before = UserParameters.getResetDeviceTimeMillis(mContext);
            if (before > 0) {
                UserParameters.setResetDeviceTimeMillis(mContext,
                        before + delta);
            }
        }
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
        UserParameters.setResumeProvisionTimeMillis(mContext,
                whenExpectedToRun.toEpochMilli());
    }

    @Override
    public void notifyRebootWhenProvisionPaused() {
        dispatchFuture(this::rescheduleResumeProvisionAlarmIfNeeded,
                "notifyRebootWhenProvisionPaused");
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
    public void notifyNeedRescheduleCheckIn() {
        dispatchFuture(this::rescheduleRetryCheckInWork, "notifyNeedRescheduleCheckIn");
    }

    @VisibleForTesting
    void rescheduleRetryCheckInWork() {
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
    public void notifyRebootWhenProvisionFailed() {
        dispatchFuture(() -> {
            rescheduleNextProvisionFailedStepAlarmIfNeeded();
            rescheduleResetDeviceAlarmIfNeeded();
        }, "notifyRebootWhenProvisionFailed");
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
        UserParameters.setResetDeviceTimeMillis(mContext, whenExpectedToRun.toEpochMilli());
    }

    @VisibleForTesting
    void rescheduleNextProvisionFailedStepAlarmIfNeeded() {
        long timestamp = UserParameters.getNextProvisionFailedStepTimeMills(mContext);
        if (timestamp > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(timestamp));
            scheduleNextProvisionFailedStepAlarm(delay);
        }
    }

    @VisibleForTesting
    void rescheduleResetDeviceAlarmIfNeeded() {
        long timestamp = UserParameters.getResetDeviceTimeMillis(mContext);
        if (timestamp > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(timestamp));
            scheduleResetDeviceAlarmInternal(delay);
        }
    }

    @VisibleForTesting
    void rescheduleResumeProvisionAlarmIfNeeded() {
        long resumeProvisionTimeMillis = UserParameters.getResumeProvisionTimeMillis(mContext);
        if (resumeProvisionTimeMillis > 0) {
            Duration delay = Duration.between(
                    Instant.now(mClock),
                    Instant.ofEpochMilli(resumeProvisionTimeMillis));
            scheduleResumeProvisionAlarm(delay);
        }
    }

    /**
     * Run the input runnable in order on the scheduler's sequential executor
     *
     * @param runnable   The runnable to run on worker thread.
     * @param methodName The name of the method that requested to run runnable.
     */
    private void dispatchFuture(Runnable runnable, String methodName) {
        Futures.addCallback(Futures.submit(runnable, mSequentialExecutor),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void unused) {
                        LogUtil.i(TAG, "Successfully called " + methodName);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException("failed to call " + methodName, t);
                    }
                }, MoreExecutors.directExecutor());
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
