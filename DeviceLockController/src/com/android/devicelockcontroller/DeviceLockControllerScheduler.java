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

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNPROVISIONED;
import static com.android.devicelockcontroller.storage.GlobalParametersClient.getInstance;

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
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.receivers.NextProvisionFailedStepReceiver;
import com.android.devicelockcontroller.receivers.ResetDeviceReceiver;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    // The default minute value of the count down when device is about to reset.
    @VisibleForTesting
    static final int RESET_DEVICE_DEFAULT_MINUTES = 30;
    private final Context mContext;
    private static final int CHECK_IN_INTERVAL_MINUTE = 60;
    private final Clock mClock;


    public DeviceLockControllerScheduler(Context context) {
        this(context, Clock.systemUTC());
    }

    @VisibleForTesting
    DeviceLockControllerScheduler(Context context, Clock clock) {
        mContext = context;
        mClock = clock;
    }

    @Override
    public void correctExpectedToRunTime(Duration delta) {
        DeviceStateController stateController =
                ((PolicyObjectsInterface) mContext.getApplicationContext()).getStateController();
        GlobalParametersClient client = getInstance();
        int currentState = stateController.getState();
        List<ListenableFuture<Void>> updateTimestampFutures = new ArrayList<>();
        updateTimestampFutures.add(Futures.transformAsync(client.getBootTimeMillis(),
                before -> client.setBootTimeMillis(before + delta.toMillis()),
                MoreExecutors.directExecutor()));
        if (currentState == UNPROVISIONED) {
            updateTimestampFutures.add(Futures.transformAsync(client.getNextCheckInTimeMillis(),
                    before -> {
                        if (before == 0) return Futures.immediateVoidFuture();
                        return client.setNextCheckInTimeMillis(before + delta.toMillis());
                    }, MoreExecutors.directExecutor()));
        } else if (currentState == PROVISION_PAUSED) {
            updateTimestampFutures.add(Futures.transformAsync(client.getResumeProvisionTimeMillis(),
                    before -> {
                        if (before == 0) return Futures.immediateVoidFuture();
                        return client.setResumeProvisionTimeMillis(before + delta.toMillis());
                    }, MoreExecutors.directExecutor()));
        } else if (currentState == PROVISION_FAILED) {
            updateTimestampFutures.add(
                    Futures.transformAsync(client.getNextProvisionFailedStepTimeMills(),
                            before -> {
                                if (before == 0) return Futures.immediateVoidFuture();
                                return client.setNextProvisionFailedStepTimeMills(
                                        before + delta.toMillis());
                            }, MoreExecutors.directExecutor()));
            updateTimestampFutures.add(
                    Futures.transformAsync(client.getResetDeviceTimeMillis(),
                            before -> {
                                if (before == 0) return Futures.immediateVoidFuture();
                                return client.setResetDeviceTImeMillis(before + delta.toMillis());
                            }, MoreExecutors.directExecutor()));
        }

        Futures.addCallback(
                Futures.whenAllSucceed(updateTimestampFutures)
                        .call(() -> null, MoreExecutors.directExecutor()),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        LogUtil.i(TAG, "Successfully corrected expected to run time");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to correct expected to run time", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    @Override
    public void rescheduleIfNeeded() {
        DeviceStateController stateController =
                ((PolicyObjectsInterface) mContext.getApplicationContext()).getStateController();

        switch (stateController.getState()) {
            case UNPROVISIONED:
                rescheduleRetryCheckInWork();
                break;
            case PROVISION_PAUSED:
                rescheduleResumeProvisionAlarm();
                break;
            case PROVISION_FAILED:
                rescheduleNextProvisionFailedStepAlarm();
                // Reset device is the last step in provision failed flow. We call this API
                // regardless, because the alarm will only be rescheduled if it has been
                // scheduled previously.
                rescheduleResetDeviceAlarm();
                break;
            default:
                // no-op for other states.
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
        Futures.addCallback(
                getInstance().setResumeProvisionTimeMillis(whenExpectedToRun.toEpochMilli()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.v(TAG, "Successfully stored resume provision time");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG, "Failed to store resume provision time", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    @VisibleForTesting
    void rescheduleResumeProvisionAlarm() {
        Futures.addCallback(
                getInstance().getResumeProvisionTimeMillis(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Long resumeProvisionTimeMillis) {
                        if (resumeProvisionTimeMillis <= 0) return;
                        Duration delay = Duration.between(
                                Instant.now(mClock),
                                Instant.ofEpochMilli(resumeProvisionTimeMillis));
                        scheduleResumeProvisionAlarm(delay);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG,
                                "Failed to retrieve resume provision time. "
                                        + "Resume provision immediately!", t);
                        scheduleResumeProvisionAlarm(Duration.ZERO);
                    }
                }, MoreExecutors.directExecutor());
    }

    @Override
    public void scheduleInitialCheckInWork() {
        LogUtil.i(TAG, "Scheduling initial check-in work");
        enqueueCheckInWorkRequest(/* isExpedited= */ true, Duration.ZERO);
    }

    @Override
    public void scheduleRetryCheckInWork(Duration delay) {
        LogUtil.i(TAG, "Scheduling retry check-in work with delay: " + delay);
        enqueueCheckInWorkRequest(/* isExpedited= */ false, delay);
        Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
        Futures.addCallback(
                getInstance().setNextCheckInTimeMillis(
                        whenExpectedToRun.toEpochMilli()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.v(TAG, "Successfully stored next check-in time");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG, "Failed to store next check-in time", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    @VisibleForTesting
    void rescheduleRetryCheckInWork() {
        Futures.addCallback(
                getInstance().getNextCheckInTimeMillis(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Long nextCheckInTimeMillis) {
                        if (nextCheckInTimeMillis <= 0) return;
                        Duration delay = Duration.between(
                                Instant.now(mClock),
                                Instant.ofEpochMilli(nextCheckInTimeMillis));
                        LogUtil.i(TAG, "Rescheduling retry check-in work with delay: " + delay);
                        enqueueCheckInWorkRequest(/* isExpedited= */ false, delay);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG,
                                "Failed to retrieve next checkin time. Retry check-in immediately!",
                                t);
                        enqueueCheckInWorkRequest(/* isExpedited= */ false, Duration.ZERO);
                    }
                }, MoreExecutors.directExecutor());
    }

    @Override
    public void scheduleNextProvisionFailedStepAlarm() {
        ListenableFuture<Void> scheduleTask =
                Futures.transformAsync(
                        getInstance().getNextProvisionFailedStepTimeMills(),
                        lastTimestamp -> {
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
                            return getInstance().setNextProvisionFailedStepTimeMills(nextTimestamp);
                        }, MoreExecutors.directExecutor());
        Futures.addCallback(
                scheduleTask,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.v(TAG, "Successfully scheduled next provision failed step alarm");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG, "Failed to schedule next provision failed step alarm", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    @VisibleForTesting
    void rescheduleNextProvisionFailedStepAlarm() {
        Futures.addCallback(getInstance().getNextProvisionFailedStepTimeMills(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Long timestamp) {
                        if (timestamp <= 0) return;
                        Duration delay = Duration.between(
                                Instant.now(mClock),
                                Instant.ofEpochMilli(timestamp));
                        scheduleNextProvisionFailedStepAlarm(delay);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG,
                                "Failed to retrieve next report provision state time. Report "
                                        + "immediately!",
                                t);
                        scheduleNextProvisionFailedStepAlarm(Duration.ZERO);
                    }
                }, MoreExecutors.directExecutor());
    }

    @Override
    public void scheduleResetDeviceAlarm() {
        Duration delay = Duration.ofMinutes(RESET_DEVICE_DEFAULT_MINUTES);
        if (Build.isDebuggable()) {
            delay = Duration.ofMinutes(
                    SystemProperties.getInt("devicelock.provision.reset-device-minutes",
                            RESET_DEVICE_DEFAULT_MINUTES));
        }
        scheduleResetDeviceAlarm(delay);
        Instant whenExpectedToRun = Instant.now(mClock).plus(delay);
        DeviceLockNotificationManager.sendDeviceResetTimerNotification(mContext,
                whenExpectedToRun.toEpochMilli());
        Futures.addCallback(
                getInstance().setResetDeviceTImeMillis(whenExpectedToRun.toEpochMilli()),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.v(TAG, "Successfully stored reset device time");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG, "Failed to store reset device time", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    @VisibleForTesting
    void rescheduleResetDeviceAlarm() {
        Futures.addCallback(getInstance().getResetDeviceTimeMillis(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Long timestamp) {
                        if (timestamp <= 0) return;
                        Duration delay = Duration.between(
                                Instant.now(mClock),
                                Instant.ofEpochMilli(timestamp));
                        scheduleResetDeviceAlarm(delay);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG,
                                "Failed to retrieve reset device time. Reset immediately!",
                                t);
                        scheduleResetDeviceAlarm(Duration.ZERO);
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

    private void scheduleResetDeviceAlarm(Duration delay) {
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
