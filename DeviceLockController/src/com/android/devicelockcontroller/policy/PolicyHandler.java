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

import androidx.annotation.IntDef;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface used for setting policies for a given state.
 */
public interface PolicyHandler {
    /** Result Type for operation */
    @IntDef(value = {SUCCESS, FAILURE})
    @Retention(RetentionPolicy.SOURCE)
    @interface ResultType {}

    int SUCCESS = 0;
    int FAILURE = 1;

    /**
     * Sets the policy state based on the new state. Throws SecurityException when the app is not
     * privileged.
     */
    @ResultType
    int setPolicyForState(@DeviceState int state);

    /** Verifies policy compliance for the state. */
    boolean isCompliant(@DeviceState int state);

    /**
     * Indicates finalization of Setup parameters. Since PolicyHandlers are instantiated when app
     * process is created, the setup parameters are not final. This method is called when the app
     * has entered a state where setup parameters are finalized.
     */
    default void setSetupParametersValid() {}
}
