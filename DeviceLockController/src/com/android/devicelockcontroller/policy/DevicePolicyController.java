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

import android.content.Intent;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Interface for the policy controller that is responsible for applying policies based
 * on state.
 */
public interface DevicePolicyController {

    /**
     * Factory resets the device when the setup has failed and cannot continue.
     * Returns true if action was successful.
     * <p>
     * Using the new {@code DevicePolicyManager#wipeDevice()} introduced in Android U to
     * reset the device. This is because the {@code DevicePolicyManager#wipeData()} no longer resets
     * the device when called as the device owner, as it used to do in earlier Android versions.
     */
    boolean wipeDevice();

    /**
     * Enforce current policies.
     */
    ListenableFuture<Void> enforceCurrentPolicies();

    /**
     * Get the launch intent for current enforced state.
     */
    ListenableFuture<Intent> getLaunchIntentForCurrentState();
}
