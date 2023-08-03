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

package com.android.devicelockcontroller.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.AbstractDeviceLockControllerScheduler;
import com.android.devicelockcontroller.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Handle {@link Intent#ACTION_TIME_CHANGED}. This receiver runs for every user.
 * <p>
 * This receiver is responsible handle system time change and make corrections to the "expected to
 * run" time for scheduled work / alarm.
 */
public final class TimeChangedBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "TimeChangedBroadcastReceiver";
    private AbstractDeviceLockControllerScheduler mScheduler;
    private Clock mClock;

    public TimeChangedBroadcastReceiver() {
        this(null, Clock.systemUTC());
    }

    @VisibleForTesting
    TimeChangedBroadcastReceiver(AbstractDeviceLockControllerScheduler scheduler, Clock clock) {
        mScheduler = scheduler;
        mClock = clock;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_TIME_CHANGED)) {
            return;
        }

        LogUtil.d(TAG, "Time changed.");

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();
        if (isUserProfile) {
            return;
        }
        if (mScheduler == null) mScheduler = new DeviceLockControllerScheduler(context);

        long bootTimestamp = UserParameters.getBootTimeMillis(context);
        Instant bootInstant = Instant.ofEpochMilli(bootTimestamp);

        mScheduler.correctExpectedToRunTime(
                Duration.between(bootInstant.plusMillis(
                        SystemClock.elapsedRealtime()), Instant.now(mClock)));
    }
}
