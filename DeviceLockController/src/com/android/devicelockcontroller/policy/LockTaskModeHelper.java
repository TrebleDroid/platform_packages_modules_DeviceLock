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

import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORK_NAME;

import android.app.admin.DevicePolicyManager;
import android.content.Context;

import androidx.work.WorkManager;

import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * Helper class deal with Lock Task Mode.
 */
public final class LockTaskModeHelper {

    private static final String TAG = "LockTaskModeHelper";

    private LockTaskModeHelper() {
    }

    /**
     * Remove all allow-listed apps and exit lock task mode.
     */
    public static void disableLockTaskMode(Context context, DevicePolicyManager dpm) {
        WorkManager.getInstance(context).cancelUniqueWork(START_LOCK_TASK_MODE_WORK_NAME);

        final String currentPackage = UserParameters.getPackageOverridingHome(context);
        // Device Policy Engine treats lock task features and packages as one policy and
        // therefore we need to set both lock task features (to LOCK_TASK_FEATURE_NONE) and
        // lock task packages (to an empty string array).
        dpm.setLockTaskFeatures(null /* admin */, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
        // This will stop the lock task mode
        dpm.setLockTaskPackages(null /* admin */, new String[0]);
        LogUtil.i(TAG, "Clear Lock task allowlist");
        if (currentPackage != null) {
            dpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
            UserParameters.setPackageOverridingHome(context, null /* packageName */);
        }
    }
}
