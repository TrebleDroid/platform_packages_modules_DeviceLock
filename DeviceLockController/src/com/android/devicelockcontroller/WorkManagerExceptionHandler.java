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

import android.annotation.IntDef;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Attempt to recover from failed check ins due to disk full.
 */
public final class WorkManagerExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "WorkManagerExceptionHandler";

    private static final long RETRY_ALARM_MILLISECONDS = Duration.ofHours(1).toMillis();

    @VisibleForTesting
    public static final String ALARM_REASON = "ALARM_REASON";

    private final Context mContext;
    private final Executor mWorkManagerTaskExecutor;

    /** Alarm reason definitions. */
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            AlarmReason.INITIAL_CHECK_IN,
            AlarmReason.RETRY_CHECK_IN,
            AlarmReason.RESCHEDULE_CHECK_IN,
            AlarmReason.INITIALIZATION,
    })
    public @interface AlarmReason {
        int INITIAL_CHECK_IN = 0;
        int RETRY_CHECK_IN = 1;
        int RESCHEDULE_CHECK_IN = 2;
        int INITIALIZATION = 3;
    }

    /**
     * Receiver to handle alarms scheduled upon failure to enqueue check-in work due to
     * SQLite exceptions, or WorkManager initialization failures.
     * This receiver tries to recover the check-in process, if still needed.
     */
    public static final class WorkFailureAlarmReceiver extends BroadcastReceiver {
        private final Executor mExecutor = Executors.newSingleThreadExecutor();

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!WorkFailureAlarmReceiver.class.getName().equals(intent.getComponent()
                    .getClassName())) {
                throw new IllegalArgumentException("Can not handle implicit intent!");
            }

            final Bundle bundle = intent.getExtras();
            if (bundle == null) {
                throw new IllegalArgumentException("Intent has no bundle");
            }

            final @AlarmReason int alarmReason = bundle.getInt(ALARM_REASON, -1 /* undefined */);
            if (alarmReason < 0) {
                throw new IllegalArgumentException("Missing alarm reason");
            }

            LogUtil.i(TAG, "Received alarm to recover from WorkManager exception with reason: "
                    + alarmReason);

            final PendingResult pendingResult = goAsync();

            final ListenableFuture<Void> checkInIfNotYetProvisioned =
                    Futures.transformAsync(GlobalParametersClient.getInstance().isProvisionReady(),
                            isProvisionReady -> {
                                if (isProvisionReady) {
                                    // Already provisioned, no need to check in
                                    return Futures.immediateVoidFuture();
                                } else {
                                    return getCheckInFuture(context, alarmReason);
                                }
                            }, mExecutor);

            Futures.addCallback(checkInIfNotYetProvisioned, new FutureCallback<>() {
                @Override
                public void onSuccess(Void result) {
                    LogUtil.i(TAG, "Successfully scheduled check in after WorkManager exception");
                    pendingResult.finish();
                }

                @Override
                public void onFailure(Throwable t) {
                    LogUtil.e(TAG, "Failed to schedule check in after WorkManager exception", t);
                    pendingResult.finish();
                }
            }, mExecutor);
        }

        private ListenableFuture<Void> getCheckInFuture(Context context,
                @AlarmReason int alarmReason) {
            final DeviceLockControllerSchedulerProvider schedulerProvider =
                    (DeviceLockControllerSchedulerProvider) context.getApplicationContext();
            final DeviceLockControllerScheduler scheduler =
                    schedulerProvider.getDeviceLockControllerScheduler();

            ListenableFuture<Void> checkInOperation;

            switch (alarmReason) {
                case AlarmReason.INITIAL_CHECK_IN:
                case AlarmReason.INITIALIZATION:
                    checkInOperation = scheduler.maybeScheduleInitialCheckIn();
                    break;
                case AlarmReason.RETRY_CHECK_IN:
                    // Use zero as delay since this is a corner case. We will eventually get the
                    // proper value from the server.
                    checkInOperation = scheduler.scheduleRetryCheckInWork(Duration.ZERO);
                    break;
                case AlarmReason.RESCHEDULE_CHECK_IN:
                    checkInOperation = scheduler.notifyNeedRescheduleCheckIn();
                    break;
                default:
                    throw new IllegalArgumentException("Invalid alarm reason");
            }

            return checkInOperation;
        }
    }

    private Executor createWorkManagerTaskExecutor() {
        final ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger mThreadCount = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new DlcWmThread(r,
                        "DLC.WorkManager.task-" + mThreadCount.incrementAndGet());
                thread.setUncaughtExceptionHandler(WorkManagerExceptionHandler.this);
                return thread;
            }
        };
        // Same as the one used by WorkManager internally.
        return Executors.newFixedThreadPool(Math.max(2,
                Math.min(Runtime.getRuntime().availableProcessors() - 1, 4)), threadFactory);
    }

    private static final class DlcWmThread extends Thread {
        private final Thread.UncaughtExceptionHandler mOriginalUncaughtExceptionHandler;

        DlcWmThread(Runnable target, String name) {
            super(target, name);
            mOriginalUncaughtExceptionHandler = getUncaughtExceptionHandler();
        }

        UncaughtExceptionHandler getOriginalUncaughtExceptionHandler() {
            return mOriginalUncaughtExceptionHandler;
        }
    }

    /**
     * Schedule an alarm to restart the check in process in case of critical failures.
     * This is called if we failed to enqueue the check in work.
     */
    public static void scheduleAlarm(Context context, @AlarmReason int alarmReason) {
        final AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        final Intent intent = new Intent(context, WorkFailureAlarmReceiver.class);
        final Bundle bundle = new Bundle();
        bundle.putInt(ALARM_REASON, alarmReason);
        intent.putExtras(bundle);
        final PendingIntent alarmIntent =
                PendingIntent.getBroadcast(context, /* requestCode = */ 0, intent,
                        PendingIntent.FLAG_IMMUTABLE);

        alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()
                + RETRY_ALARM_MILLISECONDS, alarmIntent);
        LogUtil.i(TAG, "Alarm scheduled, reason: " + alarmReason);
    }

    /**
     * Schedule an alarm to restart the app in case of critical failures.
     * This is called if we failed to initialize WorkManager.
     */
    public static void scheduleAlarmAndTerminate(Context context, @AlarmReason int alarmReason) {
        scheduleAlarm(context, alarmReason);
        // Terminate the process without calling the original uncaught exception handler,
        // otherwise the alarm may be canceled if there are several crashes in a short period
        // of time (similar to what happens in the force stopped case).
        LogUtil.i(TAG, "Terminating Device Lock Controller because of a critical failure.");
        System.exit(0);
    }

    WorkManagerExceptionHandler(Context context) {
        mContext = context;
        mWorkManagerTaskExecutor = createWorkManagerTaskExecutor();
    }

    Executor getWorkManagerTaskExecutor() {
        return mWorkManagerTaskExecutor;
    }

    // Called when one of the internal task threads of WorkManager throws an exception.
    // We're interested in some exceptions subclass of SQLiteException (like SQLiteFullException)
    // since it's not handled in initializationExceptionHandler.
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        LogUtil.e(TAG, "Uncaught exception in WorkManager task", e);
        if (!(t instanceof DlcWmThread)) {
            throw new RuntimeException("Thread is not a DlcWmThread", e);
        }

        if (e instanceof SQLiteException) {
            scheduleAlarmAndTerminate(mContext, AlarmReason.INITIALIZATION);
        } else {
            final Thread.UncaughtExceptionHandler originalExceptionHandler =
                    ((DlcWmThread) t).getOriginalUncaughtExceptionHandler();

            originalExceptionHandler.uncaughtException(t, e);
        }
    }

    // This is setup in WM configuration and is called when initialization fails. It does not
    // include the SQLiteFullException case.
    void initializationExceptionHandler(Throwable t) {
        LogUtil.e(TAG, "WorkManager initialization error", t);

        scheduleAlarmAndTerminate(mContext, AlarmReason.INITIALIZATION);
    }
}
