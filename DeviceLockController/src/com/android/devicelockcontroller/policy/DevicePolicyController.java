/*
 * Copyright (C) 2022 The Android Open Source Project
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

/**
 * Interface for the policy controller that is responsible for applying policies based
 * on state.
 */
public interface DevicePolicyController {
    /**
     * Launches an activity in locked mode. The specific activity is resolved based on the current
     * device state.Returns false if package containing the activity is not in the allowlist.
     */
    boolean launchActivityInLockedMode();

    /**
     * Enqueue a worker to start lock task mode and launch corresponding activity. The work will be
     * retried until device is in lock task mode.
     */
    void enqueueStartLockTaskModeWorker();

    /**
     * Factory resets the device when the setup has failed and cannot continue.
     * Returns true if action was successful.
     */
    boolean wipeData();
}
