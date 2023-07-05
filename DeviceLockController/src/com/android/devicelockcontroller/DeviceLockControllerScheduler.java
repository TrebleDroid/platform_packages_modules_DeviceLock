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

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceEvent.PROVISION_RESUME;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.PROVISION_IN_PROGRESS;
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

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.provision.worker.DeviceCheckInWorker;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** A class responsible for scheduling delayed work / alarm */
public final class DeviceLockControllerScheduler extends AbstractDeviceLockControllerScheduler {
    private static final String TAG = "DeviceLockControllerScheduler";
    public static final String DEVICE_CHECK_IN_WORK_NAME = "device-check-in";
    private static final String PROVISION_PAUSED_MINUTES_SYS_PROPERTY_KEY =
            "debug.devicelock.paused-minutes";
    @VisibleForTesting
    static final int PROVISION_PAUSED_MINUTES_DEFAULT = 60;
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
        ListenableFuture<Void> updateTimestampFuture = null;
        if (currentState == UNPROVISIONED) {
            updateTimestampFuture = Futures.transformAsync(client.getNextCheckInTimeMillis(),
                    before -> {
                        if (before == 0) return Futures.immediateVoidFuture();
                        return client.setNextCheckInTimeMillis(before + delta.toMillis());
                    }, MoreExecutors.directExecutor());
        } else if (currentState == PROVISION_PAUSED) {
            updateTimestampFuture = Futures.transformAsync(client.getResumeProvisionTimeMillis(),
                    before -> {
                        if (before == 0) return Futures.immediateVoidFuture();
                        return client.setResumeProvisionTimeMillis(before + delta.toMillis());
                    }, MoreExecutors.directExecutor());
        }

        if (updateTimestampFuture != null) {
            Futures.addCallback(updateTimestampFuture, new FutureCallback<>() {
                @Override
                public void onSuccess(Void result) {
                    LogUtil.i(TAG, "Successfully corrected expected to run time");
                }

                @Override
                public void onFailure(Throwable t) {
                    LogUtil.e(TAG, "Failed to correct expected to run time", t);
                }
            }, MoreExecutors.directExecutor());
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

    @Override
    public void rescheduleResumeProvisionAlarm() {
        Futures.addCallback(
                getInstance().getResumeProvisionTimeMillis(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Long resumeProvisionTimeMillis) {
                        Duration delay = Duration.between(
                                Instant.now(mClock),
                                Instant.ofEpochMilli(resumeProvisionTimeMillis));
                        scheduleResumeProvisionAlarm(delay);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.w(TAG,
                                "Failed to retrieve resume provision time. Resume provision "
                                        + "immediately!", t);
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

    @Override
    public void rescheduleRetryCheckInWork() {
        Futures.addCallback(
                getInstance().getNextCheckInTimeMillis(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Long nextCheckInTimeMillis) {
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
        long countDownBase = SystemClock.elapsedRealtime() + delay.toMillis();
        AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
        PendingIntent resumeProvisionIntent = PendingIntent.getBroadcast(mContext, /* ignored */ 0,
                new Intent(mContext, ResumeProvisionReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        Objects.requireNonNull(alarmManager).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                countDownBase,
                resumeProvisionIntent);
    }

    @VisibleForTesting
    static final class ResumeProvisionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ResumeProvisionReceiver.class.getName().equals(
                    intent.getComponent().getClassName())) {
                throw new IllegalArgumentException("Can not handle implicit intent!");
            }
            DeviceStateController stateController =
                    ((PolicyObjectsInterface) context.getApplicationContext()).getStateController();
            Futures.addCallback(stateController.setNextStateForEvent(PROVISION_RESUME),
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Integer newState) {
                            LogUtil.v(TAG, "DeviceState is: " + newState);
                            if (PROVISION_IN_PROGRESS != newState) {
                                onFailure(new IllegalStateException());
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            LogUtil.e(TAG, "Failed to resume provision", t);
                        }
                    }, MoreExecutors.directExecutor());
        }
    }
}
