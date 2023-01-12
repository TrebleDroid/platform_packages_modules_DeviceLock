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

import android.content.Context;

import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.util.LogUtil;

/** A utility class used when device is booting. */
final class BootUtils {

    static final String TAG = "BootUtils";

    private BootUtils() {
    }

    /**
     * Checks if device is in a "locked" state. If yes, enable the lock task mode and launches
     * applicable activity.
     */
    static void startLockTaskModeAtBoot(Context context) {
        PolicyObjectsInterface policies =
                ((PolicyObjectsInterface) context.getApplicationContext());
        if (!policies.getStateController().isLocked()) {
            return;
        }

        boolean result = policies.getPolicyController().launchActivityInLockedMode();
        if (!result) {
            LogUtil.e(TAG, "Failed to launch activity in lock task mode");
        }
        // TODO: Create a periodic worker to launch lock task mode.
    }
}
